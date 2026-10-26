package com.github.dewarepk.synthetixlib.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/**
 * A pool plus the worker that will run its statements. The worker is the database's own (not Paper's async pool):
 * closing must wait for exactly this database's queued work, and slow SQL must not starve the server's threads.
 */
final class DatabaseImpl implements Database {

    /** How long interrupted statements get to stop before the pool is closed under them. */
    private static final Duration INTERRUPT_GRACE = Duration.ofSeconds(2);
    private static final int MAX_LOGGED = 5;

    private final Dialect dialect;
    private final ConnectionSource source;
    private final ThreadPoolExecutor worker;
    private final Duration shutdownTimeout;
    private final String prefix;
    private final Logger logger;
    private final ServerThreadGuard guard;
    private final Object workerOwner;
    private final String pluginName;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CountDownLatch terminated = new CountDownLatch(1);
    /** Every accepted call that has not completed yet: queued or running. */
    private final Set<Pending<?>> unfinished = ConcurrentHashMap.newKeySet();

    private DatabaseImpl(final String pluginName, final Dialect dialect, final ConnectionSource source,
                         final ThreadPoolExecutor worker, final Object workerOwner, final Duration shutdownTimeout,
                         final String prefix, final Logger logger, final ServerThreadGuard guard) {
        this.pluginName = pluginName;
        this.dialect = dialect;
        this.source = source;
        this.worker = worker;
        this.workerOwner = workerOwner;
        this.shutdownTimeout = shutdownTimeout;
        this.prefix = prefix;
        this.logger = logger;
        this.guard = guard;
    }

    /**
     * Everything {@link Database#create} does that does not need a server: checks the settings, finds the driver,
     * connects, starts the worker.
     */
    static DatabaseImpl open(final String pluginName, final Path dataFolder, final Logger logger,
                             final DatabaseSettings settings) {
        return open(pluginName, dataFolder, logger, settings, ServerThreadGuard.disabled());
    }

    /** {@link Database#standalone}: no server, no plugin; a missing library is reported for a test class path. */
    static DatabaseImpl openStandalone(final String name, final Path directory, final Logger logger,
                                       final DatabaseSettings settings) {
        return open(name, directory, logger, settings, ServerThreadGuard.disabled(), true);
    }

    /** @param guard warns when a server thread waits for one of the futures */
    static DatabaseImpl open(final String pluginName, final Path dataFolder, final Logger logger,
                             final DatabaseSettings settings, final ServerThreadGuard guard) {
        return open(pluginName, dataFolder, logger, settings, guard, false);
    }

    private static DatabaseImpl open(final String pluginName, final Path dataFolder, final Logger logger,
                                     final DatabaseSettings settings, final ServerThreadGuard guard,
                                     final boolean standalone) {
        final ConnectionPlan plan = ConnectionPlan.of(settings, dataFolder);
        if (plan.file() != null) {
            createParentFolder(plan.file());
        }
        if (standalone) {
            requireStandaloneDriver(plan.url(), plan.driverJar());
        } else {
            requireDriver(plan.url(), plan.driverJar());
        }

        final ConnectionSource source;
        try {
            source = HikariConnectionSource.open(pluginName + "-db", plan);
        } catch (final NoClassDefFoundError e) {
            throw standalone ? missingStandaloneHikari(e) : missingHikari(e);
        }
        try {
            final Object owner = new Object();
            return new DatabaseImpl(pluginName, plan.dialect(), source, newWorker(pluginName, plan.poolSize(), owner),
                    owner, settings.shutdownTimeout(), settings.tablePrefix(), logger, guard);
        } catch (final RuntimeException e) {
            source.close();
            throw e;
        }
    }

    @Override
    public Dialect dialect() {
        return dialect;
    }

    // --- statements -----------------------------------------------------------------------------------------------

    @Override
    public <T> CompletableFuture<List<T>> query(final String sql, final RowMapper<T> mapper, final Object... params) {
        Objects.requireNonNull(mapper, "mapper");
        final String resolved = resolve(prefix, sql);
        final Object[] values = Params.convert(params);
        return submit(resolved, () -> {
            try (Connection connection = connection(resolved)) {
                return new Statements(connection, dialect).query(resolved, mapper, values);
            }
        });
    }

    @Override
    public <T> CompletableFuture<Optional<T>> queryOne(final String sql, final RowMapper<T> mapper,
                                                       final Object... params) {
        Objects.requireNonNull(mapper, "mapper");
        final String resolved = resolve(prefix, sql);
        final Object[] values = Params.convert(params);
        return submit(resolved, () -> {
            try (Connection connection = connection(resolved)) {
                return new Statements(connection, dialect).queryOne(resolved, mapper, values);
            }
        });
    }

    @Override
    public CompletableFuture<Integer> update(final String sql, final Object... params) {
        return updateResolved(resolve(prefix, sql), Params.convert(params));
    }

    private CompletableFuture<Integer> updateResolved(final String resolved, final @Nullable Object[] values) {
        return submit(resolved, () -> {
            try (Connection connection = connection(resolved)) {
                return new Statements(connection, dialect).update(resolved, values);
            }
        });
    }

    @Override
    public CompletableFuture<Long> insert(final String sql, final Object... params) {
        final String resolved = resolve(prefix, sql);
        final Object[] values = Params.convert(params);
        // a transaction, so an INSERT ... SELECT that created the wrong number of rows is rolled back
        return submit(resolved, () -> inTransaction(resolved,
                connection -> new Statements(connection, dialect).insert(resolved, values)));
    }

    @Override
    public CompletableFuture<int[]> batch(final String sql, final List<Object[]> rows) {
        final String resolved = resolve(prefix, sql);
        final List<Object[]> converted = Params.convertRows(rows);
        return submit(resolved, () -> inTransaction(resolved,
                connection -> new Statements(connection, dialect).batch(resolved, converted)));
    }

    @Override
    public <T> CompletableFuture<T> transaction(final TransactionBody<T> body) {
        Objects.requireNonNull(body, "body");
        return submit(null, () -> inTransaction("BEGIN", connection -> {
            final TransactionImpl tx = new TransactionImpl(new Statements(connection, dialect), dialect, prefix);
            try {
                return body.run(tx);
            } finally {
                tx.finish();
            }
        }));
    }

    @Override
    public CompletableFuture<Integer> upsert(final String table, final List<String> keyColumns,
                                             final Map<String, Object> values) {
        final Upsert upsert = Upsert.of(dialect, table, keyColumns, values);
        return updateResolved(resolve(prefix, upsert.sql()), upsert.values());
    }

    @Override
    public CompletableFuture<Integer> migrate(final Migrations migrations) {
        Objects.requireNonNull(migrations, "migrations");
        return submit(null, () -> new Migrator(this, dialect, prefix).run(migrations));
    }

    /** @return {@code sql} with {@code {prefix}} replaced by the table prefix */
    static String resolve(final String prefix, final String sql) {
        return Objects.requireNonNull(sql, "sql").replace("{prefix}", prefix);
    }

    private interface Task<T> {
        T run() throws Exception;
    }

    interface Work<T> {
        T run(Connection connection) throws Exception;
    }

    /**
     * A call accepted by {@link #submit}. It is in {@link #unfinished} from acceptance until its future completes, so
     * {@link #close} knows what is left, and whoever drains the queue can fail what never ran.
     */
    private final class Pending<T> implements Runnable {

        private final @Nullable String sql;
        private final Task<T> task;
        private final DbFuture<T> future;

        Pending(final @Nullable String sql, final Task<T> task, final DbFuture<T> future) {
            this.sql = sql;
            this.task = task;
            this.future = future;
        }

        @Override
        public void run() {
            final T value;
            try {
                value = task.run();
            } catch (final Throwable error) {
                unfinished.remove(this);
                logFailure(sql, error);
                future.completeExceptionally(error);
                if (error instanceof Error fatal) {
                    throw fatal;
                }
                return;
            }
            // off the list before the caller can see the result, so a close that follows it never counts this call
            unfinished.remove(this);
            future.complete(value);
        }

        void fail(final Throwable error) {
            unfinished.remove(this);
            future.completeExceptionally(error);
        }
    }

    /**
     * Runs {@code task} on the worker. The future completes there, with the result or with whatever the task threw
     * (also logged once, see {@link #logFailure}). Once {@link #close} began the future fails at once with a
     * {@link DatabaseClosedException}. A call that races with the close either was queued before the worker stopped
     * accepting (and then runs, or is failed by {@link #abandon}) or is rejected here: it is never dropped.
     */
    private <T> CompletableFuture<T> submit(final @Nullable String sql, final Task<T> task) {
        final DbFuture<T> future = new DbFuture<>(guard, this::onOwnWorker);
        if (closed.get()) {
            future.completeExceptionally(new DatabaseClosedException("The database is closed"));
            return future;
        }
        final Pending<T> pending = new Pending<>(sql, task, future);
        unfinished.add(pending);
        try {
            worker.execute(pending);
        } catch (final RejectedExecutionException e) {
            pending.fail(new DatabaseClosedException("The database is closed", e));
        }
        return future;
    }

    /** Runs {@code work} on the worker with a pooled connection (auto-commit). */
    <T> CompletableFuture<T> withConnection(final String sqlForErrors, final Work<T> work) {
        return submit(sqlForErrors, () -> {
            try (Connection connection = connection(sqlForErrors)) {
                return work.run(connection);
            }
        });
    }

    /** Runs {@code work} on the worker in one transaction: committed when it returns, rolled back when it throws. */
    <T> CompletableFuture<T> withTransaction(final String sqlForErrors, final Work<T> work) {
        return submit(sqlForErrors, () -> inTransaction(sqlForErrors, work));
    }

    /** @return the {@code table-prefix} of this database */
    String prefix() {
        return prefix;
    }

    /** One connection, one transaction: commit when {@code work} returns, roll back when it throws. */
    <T> T inTransaction(final String sqlForErrors, final Work<T> work) throws Exception {
        try (Connection connection = connection(sqlForErrors)) {
            try {
                connection.setAutoCommit(false);
            } catch (final SQLException e) {
                throw DatabaseException.of(sqlForErrors, e, dialect);
            }
            final T result;
            try {
                result = work.run(connection);
            } catch (final Throwable error) {
                rollback(connection, error);
                throw error;
            }
            try {
                connection.commit();
            } catch (final SQLException e) {
                final DatabaseException failure = DatabaseException.of("COMMIT", e, dialect);
                rollback(connection, failure);
                throw failure;
            }
            // HikariCP puts autoCommit back to true when the connection returns to the pool
            return result;
        }
    }

    private static void rollback(final Connection connection, final Throwable cause) {
        try {
            connection.rollback();
        } catch (final SQLException e) {
            cause.addSuppressed(e);
        }
    }

    Connection connection(final String sqlForErrors) {
        try {
            return source.get();
        } catch (final SQLException e) {
            throw DatabaseException.of(sqlForErrors, e, dialect);
        }
    }

    /**
     * One log line per failed call, with the statement and never its parameters. A driver failure is logged without
     * the driver's exception (its text can quote data, see {@link DatabaseException}); anything else is the caller's own
     * code (a mapper, a transaction body) and is logged with its stack.
     */
    private void logFailure(final @Nullable String sql, final Throwable error) {
        if (error instanceof DatabaseException failure) {
            logger.log(Level.SEVERE, failure.getMessage());
        } else if (error instanceof MigrationException failure
                && (failure.getCause() == null || failure.getCause() instanceof DatabaseException)) {
            logger.log(Level.SEVERE, failure.getMessage());
        } else {
            logger.log(Level.SEVERE, "Database call failed" + (sql == null ? " in a transaction" : " in: " + sql), error);
        }
    }

    /**
     * Never loses a write (SnapPlug bug #1): new calls fail at once, everything already accepted runs to the end for up
     * to the shutdown timeout, and only then is the rest interrupted and the pool closed. The first caller does the
     * work; later ones (and other threads) return when it is finished.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            awaitTerminated();
            return;
        }
        worker.shutdown();
        if (onOwnWorker()) {
            // a statement closing its own database cannot wait for itself: finish on a thread of its own
            final Thread closer = new Thread(this::finishClose, pluginName + "-db-close");
            closer.setDaemon(false);
            closer.start();
            return;
        }
        finishClose();
    }

    private void finishClose() {
        try {
            if (!worker.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                abandon();
                if (!worker.awaitTermination(INTERRUPT_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                    logger.warning("Database statements are still running after being interrupted; closing the pool anyway");
                }
            }
        } catch (final InterruptedException e) {
            abandon();
            Thread.currentThread().interrupt();
        } finally {
            try {
                source.close();
            } finally {
                terminated.countDown();
            }
        }
    }

    /** The shutdown timeout ran out: say how much is left (SQL only, never parameters), interrupt it, fail what never ran. */
    private void abandon() {
        final List<Pending<?>> left = List.copyOf(unfinished);
        final StringBuilder statements = new StringBuilder();
        left.stream().limit(MAX_LOGGED).forEach(pending -> {
            statements.append("\n  ").append(pending.sql == null ? "(a transaction or migration)" : pending.sql);
        });
        if (left.size() > MAX_LOGGED) {
            statements.append("\n  ... and ").append(left.size() - MAX_LOGGED).append(" more");
        }
        logger.warning("Database shutdown: " + left.size() + " statement(s) did not finish within "
                + shutdownTimeout.toSeconds() + "s and are being interrupted:" + statements);
        for (final Runnable never : worker.shutdownNow()) {
            if (never instanceof Pending<?> pending) {
                pending.fail(new DatabaseClosedException("The database closed before this statement ran"));
            }
        }
    }

    private void awaitTerminated() {
        if (onOwnWorker()) {
            return;
        }
        try {
            terminated.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** @return the worker that runs this database's statements */
    ExecutorService worker() {
        return worker;
    }

    /** @return a pooled connection; closing it returns it to the pool */
    Connection connection() throws SQLException {
        return source.get();
    }

    /** A worker thread knows which database it belongs to, so waiting for that database from it can be refused. */
    private static final class WorkerThread extends Thread {

        private final Object owner;

        WorkerThread(final Runnable task, final String name, final Object owner) {
            super(task, name);
            this.owner = owner;
            setDaemon(false);
        }
    }

    private boolean onOwnWorker() {
        return Thread.currentThread() instanceof WorkerThread thread && thread.owner == workerOwner;
    }

    private static ThreadPoolExecutor newWorker(final String pluginName, final int size, final Object owner) {
        final AtomicInteger next = new AtomicInteger(1);
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(size, size, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                task -> new WorkerThread(task, pluginName + "-db-" + next.getAndIncrement(), owner));
        executor.prestartAllCoreThreads();
        return executor;
    }

    private static void createParentFolder(final Path file) {
        try {
            Files.createDirectories(file.getParent());
        } catch (final IOException e) {
            throw new IllegalStateException("Cannot create the folder of the database file " + file + ": "
                    + e.getMessage(), e);
        }
    }

    static void requireDriver(final String url, final String jar) {
        try {
            DriverManager.getDriver(url);
        } catch (final SQLException e) {
            throw new IllegalStateException("No JDBC driver accepts " + url + ". Paper ships it; if this server does not, "
                    + "add " + jar + " to its class path", e);
        }
    }

    private static void requireStandaloneDriver(final String url, final String jar) {
        try {
            DriverManager.getDriver(url);
        } catch (final SQLException e) {
            throw new IllegalStateException("No JDBC driver accepts " + url + ". " + TEST_LIBRARIES + " (driver: "
                    + jar + ")", e);
        }
    }

    private static final String TEST_LIBRARIES = "add com.zaxxer:HikariCP and org.xerial:sqlite-jdbc (or "
            + "mysql-connector-j) as testImplementation, versions in references/database.md";

    private static IllegalStateException missingStandaloneHikari(final NoClassDefFoundError error) {
        final String message = error.getMessage();
        if (message == null || !message.contains("com/zaxxer/hikari")) {
            throw error;
        }
        return new IllegalStateException("HikariCP is not on the class path: " + TEST_LIBRARIES, error);
    }

    private static IllegalStateException missingHikari(final NoClassDefFoundError error) {
        final String message = error.getMessage();
        if (message == null || !message.contains("com/zaxxer/hikari")) {
            throw error;
        }
        return new IllegalStateException("HikariCP is not on the plugin's class path: add "
                + "SynthetixLibraries.database(classpath) to your PluginLoader (the class named by `loader:` in "
                + "paper-plugin.yml)", error);
    }
}
