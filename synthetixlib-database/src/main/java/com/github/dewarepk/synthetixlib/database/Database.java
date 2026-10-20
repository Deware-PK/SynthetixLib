package com.github.dewarepk.synthetixlib.database;

import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;

/**
 * One database: a HikariCP pool and the worker threads that run its statements. Everything runs off the server
 * threads and every result is a {@link CompletableFuture} that completes on the database worker.
 *
 * <pre>{@code
 * Database db = Database.create(lib, settings.get().database());   // closed with lib
 *
 * CompletableFuture<List<Lot>> lots = db.query(
 *         "SELECT id, seller, price, ends_at FROM {prefix}lots WHERE ends_at > ?",
 *         row -> new Lot(row.uuid("id"), row.uuid("seller"), row.longValue("price"), row.instant("ends_at")),
 *         Instant.now());
 * lots.thenAccept(list -> lib.scheduler().entity(player).run(() -> gui.open(player, list)));   // back to a server thread
 *
 * db.transaction(tx -> {
 *     tx.update("UPDATE {prefix}balances SET money = money - ? WHERE player = ?", price, buyer);
 *     tx.update("INSERT INTO {prefix}mail (player, item) VALUES (?, ?)", seller, item);
 *     return null;
 * });
 * }</pre>
 *
 * <p>{@code {prefix}} in any statement is replaced by the {@code table-prefix} setting. Parameters are positional
 * {@code ?} and are bound by type: {@code String, int, long, double, boolean, byte[], UUID} (text), {@code Instant}
 * (epoch milliseconds), {@code Duration} (milliseconds), {@code Enum} (name), {@code ItemStack}
 * ({@code serializeAsBytes}) and {@code null}. Any other type throws {@link IllegalArgumentException} at the call.
 * {@link Row} reads the same formats back.
 *
 * <p>A failed statement completes its future with a {@link DatabaseException} and is logged once with its SQL, never
 * with the parameters. The futures do not wait on a server thread by accident: calling {@code join()} or {@code get()}
 * on one logs a warning with the stack, except while the plugin enables or disables. Do not wait for a future inside a
 * callback or a {@link TransactionBody}: the worker would wait for itself, so {@code join()} and {@code get()} there
 * throw an {@link IllegalStateException} at once. Chain with {@code thenCompose}/{@code thenAccept} instead.
 *
 * <p>Needs HikariCP on the plugin class path: add {@link SynthetixLibraries#database} to your {@code PluginLoader}.
 */
public interface Database extends AutoCloseable {

    /**
     * Opens the pool described by {@code settings} and ties it to {@code lib}: {@code lib.close()} closes it.
     * Blocks while connecting, which is fine in {@code onEnable}.
     *
     * @throws IllegalArgumentException if the settings are not valid (table prefix, port, ...)
     * @throws IllegalStateException    if HikariCP is not loaded, no JDBC driver accepts the URL, the database cannot
     *                                  be reached, or {@code lib} is closed; the message says what to do
     */
    static Database create(final SynthetixLib lib, final DatabaseSettings settings) {
        Objects.requireNonNull(lib, "lib");
        if (lib.isClosed()) {
            throw new IllegalStateException("SynthetixLib is closed");
        }
        final Database database = open(lib, settings);
        try {
            lib.attach(database);
        } catch (final RuntimeException e) {
            database.close();
            throw e;
        }
        return database;
    }

    /**
     * Like {@link #create} but not tied to {@code lib}: you close it, e.g. with try-with-resources. For a second
     * database that is only read, such as the data of an older version of the plugin.
     */
    static Database open(final SynthetixLib lib, final DatabaseSettings settings) {
        Objects.requireNonNull(lib, "lib");
        Objects.requireNonNull(settings, "settings");
        // Waiting for a future is fine while the plugin enables (until the first tick after this call) and disables.
        final AtomicBoolean enabling = new AtomicBoolean(true);
        final ServerThreadGuard guard = new ServerThreadGuard(Bukkit::isPrimaryThread,
                () -> enabling.get() || lib.isClosed() || !lib.plugin().isEnabled(), lib.plugin().getLogger());
        final DatabaseImpl database = DatabaseImpl.open(lib.plugin().getName(), lib.plugin().getDataFolder().toPath(),
                lib.plugin().getLogger(), settings, guard);
        try {
            lib.scheduler().global().run(() -> enabling.set(false));
        } catch (final RuntimeException e) {
            database.close();
            throw e;
        }
        return database;
    }

    /** @return the SQL dialect of this database */
    Dialect dialect();

    /**
     * Runs a {@code SELECT} and maps every row.
     *
     * @param sql    the statement with {@code ?} for parameters
     * @param mapper called on the worker for each row
     * @param params one value per {@code ?}
     * @return the rows (unmodifiable); fails if the statement or the mapper throws
     * @throws IllegalArgumentException if a parameter has an unsupported type
     */
    <T> CompletableFuture<List<T>> query(String sql, RowMapper<T> mapper, Object... params);

    /**
     * Like {@link #query} but only the first row is read.
     *
     * @return the mapped first row, or empty when there is none
     */
    <T> CompletableFuture<Optional<T>> queryOne(String sql, RowMapper<T> mapper, Object... params);

    /**
     * Runs an {@code INSERT}, {@code UPDATE}, {@code DELETE} or DDL statement.
     *
     * @return the number of rows changed
     * @throws IllegalArgumentException if a parameter has an unsupported type
     */
    CompletableFuture<Integer> update(String sql, Object... params);

    /**
     * Runs one statement once per row of parameters, in one transaction (all rows or none). On MySQL the driver
     * rewrites it into few multi-row statements.
     *
     * @param rows one array of parameters per execution
     * @return the update count of each row as the driver reports it (MySQL may report "no info" for rewritten batches)
     * @throws IllegalArgumentException if a parameter in any row has an unsupported type
     */
    CompletableFuture<int[]> batch(String sql, List<Object[]> rows);

    /**
     * Runs {@code body} on one connection: committed when it returns, rolled back when it throws. The future gets the
     * value {@code body} returns, or the exception.
     */
    <T> CompletableFuture<T> transaction(TransactionBody<T> body);

    /**
     * Inserts a row, or updates it when one with the same key exists ({@code ON CONFLICT} on SQLite,
     * {@code ON DUPLICATE KEY} on MySQL). A unique index or primary key must cover {@code keyColumns}.
     *
     * <pre>{@code
     * db.upsert("{prefix}balances", List.of("player"), Map.of("player", uuid, "money", 100L));
     * }</pre>
     *
     * @param table      table name, optionally starting with {@code {prefix}}
     * @param keyColumns the columns that identify the row
     * @param values     column to value, including the key columns
     * @return the number of rows changed as the driver reports it
     * @throws IllegalArgumentException for a table or column that is not a plain name, or an unsupported value type
     */
    CompletableFuture<Integer> upsert(String table, List<String> keyColumns, Map<String, Object> values);

    /**
     * Brings the tables up to date: creates {@code {prefix}schema_history}, then runs every version that is not recorded
     * there yet, in order, each in its own transaction and recorded in the same one.
     *
     * <p>Nothing runs, and the future fails with a {@link MigrationException}, when the sequence has a gap, when the
     * checksum of an applied version differs from its file (a shipped file was edited), when the database holds a
     * version this plugin does not know (a downgrade; disable the plugin), or when no script exists for this dialect. A
     * migration that fails stops the run; the versions before it stay applied. See {@link Migrations} for the file
     * format and the MySQL note on DDL.
     *
     * <pre>{@code
     * db.migrate(Migrations.fromResources("db/migrations")).join();   // in onEnable
     * }</pre>
     *
     * @return the number of versions applied (0 when the database was up to date)
     */
    CompletableFuture<Integer> migrate(Migrations migrations);

    /**
     * Closes the database without losing a write: calls made from now on fail their future with a
     * {@link DatabaseClosedException} (nothing is thrown at the call), every statement already accepted runs to the end
     * for up to the {@code shutdown-timeout} setting, and only then is the pool closed. When the time runs out the
     * number of unfinished statements is logged (their SQL, never parameters), they are interrupted, and the futures of
     * those that never started fail with {@link DatabaseClosedException}.
     *
     * <p>Idempotent and safe from any thread: a second call returns when the first has finished. Blocks while it waits,
     * so call it from {@code onDisable} (which {@code lib.close()} does for a database made with
     * {@link #create}) or from the owner of a database made with {@link #open}.
     */
    @Override
    void close();
}
