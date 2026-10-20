package com.github.dewarepk.synthetixlib.database;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * SnapPlug bug #1 and everything around {@link Database#close()}, written once and run against SQLite
 * ({@link SqliteShutdownTest}) and, when the owner's Gradle properties are set, MySQL ({@link MysqlShutdownTest}).
 * Tables have a name of their own per test (a MySQL run shares one prefix per class).
 */
abstract class DatabaseShutdownContract {

    @TempDir
    Path dir;

    private final List<DatabaseImpl> opened = new ArrayList<>();
    private final List<Logger> loggers = new ArrayList<>();
    private final DatabaseQueryContract.Capture capture = new DatabaseQueryContract.Capture();

    /** @return the settings of the database under test, with a table prefix */
    abstract DatabaseSettings settings();

    private DatabaseSettings with(final int poolSize, final Duration shutdownTimeout) {
        final DatabaseSettings base = settings();
        return new DatabaseSettings(base.type(), base.tablePrefix(), base.sqlite(), base.mysql(),
                new DatabaseSettings.Pool(poolSize, base.pool().connectionTimeout(), base.pool().maxLifetime()),
                shutdownTimeout);
    }

    final DatabaseImpl open(final String plugin) {
        return open(plugin, settings());
    }

    final DatabaseImpl open(final String plugin, final DatabaseSettings settings) {
        final Logger logger = Logger.getLogger("DatabaseShutdownContract." + plugin);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(capture);
        loggers.add(logger);
        final DatabaseImpl db = DatabaseImpl.open(plugin, dir, logger, settings, ServerThreadGuard.disabled());
        opened.add(db);
        return db;
    }

    @AfterEach
    void closeDatabases() {
        opened.forEach(DatabaseImpl::close);
        loggers.forEach(logger -> logger.removeHandler(capture));
    }

    private static <T> T result(final CompletableFuture<T> future) throws Exception {
        return future.get(30, TimeUnit.SECONDS);
    }

    private static Throwable failureOf(final CompletableFuture<?> future) {
        return DatabaseQueryContract.failureOf(future);
    }

    private static int countOf(final DatabaseImpl db, final String table) {
        return db.queryOne("SELECT COUNT(*) AS c FROM {prefix}" + table, row -> row.intValue("c")).join().orElseThrow();
    }

    // --- bug #1 ---------------------------------------------------------------------------------------------------

    @Test
    void aThousandQueuedWritesSurviveAnImmediateClose() throws Exception {
        final DatabaseImpl db = open("ShutThousand");
        db.update("CREATE TABLE {prefix}thousand (n INTEGER PRIMARY KEY, v VARCHAR(20))").join();
        final List<CompletableFuture<Integer>> writes = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            writes.add(db.update("INSERT INTO {prefix}thousand VALUES (?, ?)", i, "row" + i));
        }

        db.close();

        for (final CompletableFuture<Integer> write : writes) {
            assertTrue(write.isDone(), "close() returned while a write was still pending");
            assertEquals(1, result(write));
        }
        final DatabaseImpl again = open("ShutThousandAgain");
        assertEquals(1000, countOf(again, "thousand"));
    }

    @Test
    void closeWaitsForALongTransaction() throws Exception {
        final DatabaseImpl db = open("ShutLong");
        db.update("CREATE TABLE {prefix}long_tx (n INTEGER PRIMARY KEY)").join();
        final CountDownLatch started = new CountDownLatch(1);
        final CompletableFuture<String> transaction = db.transaction(tx -> {
            started.countDown();
            Thread.sleep(500);
            tx.update("INSERT INTO {prefix}long_tx VALUES (1)");
            return "committed";
        });
        assertTrue(started.await(10, TimeUnit.SECONDS));

        db.close();

        assertTrue(transaction.isDone(), "close() returned before the running transaction finished");
        assertEquals("committed", result(transaction));
        assertEquals(1, countOf(open("ShutLongAgain"), "long_tx"));
    }

    @Test
    void closeFromSeveralThreadsReturnsOnlyWhenTheWorkIsDone() throws Exception {
        final DatabaseImpl db = open("ShutMany");
        final CountDownLatch started = new CountDownLatch(1);
        final CompletableFuture<String> transaction = db.transaction(tx -> {
            started.countDown();
            Thread.sleep(400);
            return "done";
        });
        assertTrue(started.await(10, TimeUnit.SECONDS));
        final ExecutorService closers = Executors.newFixedThreadPool(4);
        try {
            final List<java.util.concurrent.Future<Boolean>> returned = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                returned.add(closers.submit(() -> {
                    db.close();
                    return transaction.isDone() && db.worker().isTerminated();
                }));
            }
            for (final java.util.concurrent.Future<Boolean> closer : returned) {
                assertTrue(closer.get(30, TimeUnit.SECONDS), "a close() returned before the database was closed");
            }
        } finally {
            closers.shutdownNow();
        }
    }

    @Test
    void aStatementMayCloseItsOwnDatabaseWithoutDeadlocking() throws Exception {
        final DatabaseImpl db = open("ShutSelf");
        final CompletableFuture<String> transaction = db.transaction(tx -> {
            db.close();
            return "closed from inside";
        });

        assertEquals("closed from inside", result(transaction));
        assertTimeoutPreemptively(Duration.ofSeconds(30), db::close);
        assertTrue(db.worker().isTerminated());
    }

    // --- after close ----------------------------------------------------------------------------------------------

    @Test
    void everyCallAfterCloseFailsItsFutureAndNeverThrows() {
        final DatabaseImpl db = open("ShutAfter");
        db.close();
        final Map<String, java.util.function.Supplier<CompletableFuture<?>>> calls = new java.util.LinkedHashMap<>();
        calls.put("query", () -> db.query("SELECT 1", row -> 1));
        calls.put("queryOne", () -> db.queryOne("SELECT 1", row -> 1));
        calls.put("update", () -> db.update("SELECT 1"));
        calls.put("batch", () -> db.batch("SELECT 1", List.<Object[]>of()));
        calls.put("transaction", () -> db.transaction(tx -> 1));
        calls.put("upsert", () -> db.upsert("{prefix}x", List.of("a"), Map.of("a", 1)));
        calls.put("migrate", () -> db.migrate(Migrations.fromResources("db/sample")));

        calls.forEach((name, call) -> {
            final CompletableFuture<?> future = assertDoesNotThrow(call::get, name + " must not throw at the call site");
            assertTrue(future.isCompletedExceptionally(), name);
            assertInstanceOf(DatabaseClosedException.class, failureOf(future), name);
        });
    }

    // --- the shutdown timeout -------------------------------------------------------------------------------------

    @Test
    void whenTheTimeoutRunsOutTheUnfinishedAreCountedLoggedAndInterrupted() throws Exception {
        final DatabaseImpl db = open("ShutTimeout", with(1, Duration.ofMillis(300)));
        db.update("CREATE TABLE {prefix}stuck (v VARCHAR(40))").join();
        final CountDownLatch started = new CountDownLatch(1);
        final CompletableFuture<Object> stuck = db.transaction(tx -> {
            started.countDown();
            Thread.sleep(60_000);
            return null;
        });
        assertTrue(started.await(10, TimeUnit.SECONDS));
        final String secret = "s3cr3t-player-data-77aa";
        final CompletableFuture<Integer> queued = db.update("INSERT INTO {prefix}stuck VALUES (?)", secret);
        capture.records.clear();

        final long begin = System.nanoTime();
        db.close();
        final long tookMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);

        assertTrue(tookMillis < 20_000, "close() took " + tookMillis + " ms");
        assertTrue(db.worker().isTerminated());
        assertInstanceOf(InterruptedException.class, failureOf(stuck));
        assertInstanceOf(DatabaseClosedException.class, failureOf(queued));
        final List<LogRecord> warnings = capture.records.stream()
                .filter(record -> record.getLevel().equals(Level.WARNING)).toList();
        assertEquals(1, warnings.size(), capture.everything());
        final String warning = warnings.get(0).getMessage();
        assertTrue(warning.contains("2 statement(s) did not finish"), warning);
        assertTrue(warning.contains("INSERT INTO " + settings().tablePrefix() + "stuck VALUES (?)"), warning);
        assertFalse(capture.everything().contains(secret), "parameters must never be logged");
        // the interrupted transaction was rolled back, the queued insert never ran
        assertEquals(0, countOf(open("ShutTimeoutAgain"), "stuck"));
    }

    // --- the race -------------------------------------------------------------------------------------------------

    @Test
    void sixteenCallersRacingWithCloseAreAllRunFullyOrFailed() throws Exception {
        final DatabaseImpl db = open("ShutRace", with(settings().pool().size(), Duration.ofSeconds(60)));
        db.update("CREATE TABLE {prefix}race (id CHAR(36) PRIMARY KEY)").join();
        final Map<UUID, CompletableFuture<Integer>> calls = new ConcurrentHashMap<>();
        final AtomicBoolean stop = new AtomicBoolean();
        final CountDownLatch go = new CountDownLatch(1);
        final ExecutorService callers = Executors.newFixedThreadPool(16);
        try {
            final List<java.util.concurrent.Future<?>> loops = new ArrayList<>();
            for (int thread = 0; thread < 16; thread++) {
                loops.add(callers.submit(() -> {
                    go.await();
                    for (int i = 0; i < 20_000 && !stop.get(); i++) {
                        final UUID id = UUID.randomUUID();
                        calls.put(id, db.update("INSERT INTO {prefix}race VALUES (?)", id));
                        LockSupport.parkNanos(200_000);
                    }
                    return null;
                }));
            }
            go.countDown();
            Thread.sleep(50);

            db.close();
            Thread.sleep(50); // the callers keep calling a closed database for a while
            stop.set(true);
            for (final java.util.concurrent.Future<?> loop : loops) {
                loop.get(60, TimeUnit.SECONDS);
            }
        } finally {
            callers.shutdownNow();
        }

        final Set<UUID> reportedOk = new HashSet<>();
        int closedFailures = 0;
        for (final Map.Entry<UUID, CompletableFuture<Integer>> call : calls.entrySet()) {
            try {
                assertEquals(1, call.getValue().get(30, TimeUnit.SECONDS));
                reportedOk.add(call.getKey());
            } catch (final ExecutionException e) {
                assertInstanceOf(DatabaseClosedException.class, e.getCause());
                closedFailures++;
            }
        }
        assertTrue(!reportedOk.isEmpty(), "no call ran before the close");
        assertTrue(closedFailures > 0, "no call met the closed database: the test did not race");
        final Set<UUID> stored = new HashSet<>(open("ShutRaceAgain")
                .query("SELECT id FROM {prefix}race", row -> row.uuid("id")).join());
        assertEquals(reportedOk, stored, "a call reported as run must be stored, one reported as failed must not be");
    }
}
