package com.github.dewarepk.synthetixexample.selftest;

import static com.github.dewarepk.synthetixexample.selftest.SelfTest.require;

import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.database.Copy;
import com.github.dewarepk.synthetixlib.database.Database;
import com.github.dewarepk.synthetixlib.database.DatabaseSettings;
import com.github.dewarepk.synthetixlib.database.Dialect;
import com.github.dewarepk.synthetixlib.database.Migrations;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Self-tests for the database module. {@code database.driver} is the class-loader proof of ADR 0014: HikariCP comes from
 * the plugin's {@code PluginLoader}, the JDBC driver is the one Paper ships, and the plugin's own code reaches both.
 */
final class DatabaseSelfTests {

    private DatabaseSelfTests() {
    }

    static List<SelfTest> all(final JavaPlugin plugin, final SynthetixLib lib) {
        return List.of(
                SelfTest.sync("database.driver", () -> {
                    final Path file = fresh(plugin, "selftest/driver.db");
                    final String url = "jdbc:sqlite:" + file;
                    final Driver driver = DriverManager.getDriver(url);
                    plugin.getLogger().info("[selftest] database.driver: DriverManager sees " + driver.getClass().getName()
                            + " from " + driver.getClass().getClassLoader());
                    final String hikari = HikariConfig.class.getProtectionDomain().getCodeSource().getLocation().toString();
                    plugin.getLogger().info("[selftest] database.driver: HikariCP from " + hikari + " in "
                            + HikariConfig.class.getClassLoader());
                    require(hikari.contains("HikariCP-"), "HikariCP does not come from a library jar: " + hikari);

                    final HikariConfig config = new HikariConfig();
                    config.setJdbcUrl(url);
                    config.setMaximumPoolSize(1);
                    try (HikariDataSource pool = new HikariDataSource(config);
                         Connection connection = pool.getConnection();
                         Statement statement = connection.createStatement();
                         ResultSet rows = statement.executeQuery("SELECT 1")) {
                        require(rows.next() && rows.getInt(1) == 1, "SELECT 1 did not return 1");
                    }
                }),
                SelfTest.sync("database.create", () -> {
                    fresh(plugin, "selftest/create.db");
                    final String workers = plugin.getName() + "-db-";
                    // A second library instance, so closing it does not end the shared one the other tests use.
                    final SynthetixLib own = SynthetixLib.init(plugin);
                    try {
                        final Database database = Database.create(own, DatabaseSettings.sqlite("selftest/create.db"));
                        require(database.dialect() == Dialect.SQLITE, "dialect " + database.dialect());
                        require(workerThreads(workers) == 1, "expected 1 worker thread, got " + workerThreads(workers));
                        own.close();
                        for (int i = 0; i < 100 && workerThreads(workers) > 0; i++) {
                            Thread.sleep(20);
                        }
                        require(workerThreads(workers) == 0, "worker threads still alive after lib.close()");
                        database.close();
                    } finally {
                        own.close();
                    }
                }),
                SelfTest.async("database.query", () -> query(plugin, lib)),
                SelfTest.async("database.join-guard", () -> joinGuard(plugin, lib)),
                SelfTest.async("database.migrate", () -> migrate(plugin, lib)),
                SelfTest.async("database.shutdown", () -> shutdown(plugin, lib)),
                SelfTest.async("database.copy", () -> copy(plugin, lib)));
    }

    /**
     * Writes are queued, then {@code lib.close()} runs: every write must be done when it returns, and the database must
     * close before the scheduler (a callback of a write can still schedule while the queue drains). A second library
     * instance then finds all the rows.
     */
    private static CompletableFuture<Void> shutdown(final JavaPlugin plugin, final SynthetixLib lib) {
        freshQuietly(plugin, "selftest/shutdown.db");
        final SynthetixLib own = SynthetixLib.init(plugin);
        final DatabaseSettings settings = DatabaseSettings.sqlite("selftest/shutdown.db").withTablePrefix("sd_");
        final Database db = Database.create(own, settings);
        final int writes = 300;
        final CompletableFuture<Void> done = new CompletableFuture<>();

        db.update("CREATE TABLE {prefix}rows (n INTEGER PRIMARY KEY)").whenComplete((ignored, created) ->
                // closing waits for the worker, so it must not happen on it: hop to the global thread first
                lib.scheduler().global().run(() -> {
                    SynthetixLib again = null;
                    try {
                        if (created != null) {
                            throw new IllegalStateException("create table failed", created);
                        }
                        final AtomicInteger callbacks = new AtomicInteger();
                        final AtomicInteger schedulingRefused = new AtomicInteger();
                        final List<CompletableFuture<Integer>> queued = new ArrayList<>();
                        for (int n = 0; n < writes; n++) {
                            queued.add(db.update("INSERT INTO {prefix}rows VALUES (?)", n).whenComplete((rows, failure) -> {
                                callbacks.incrementAndGet();
                                try {
                                    own.scheduler().global().run(() -> { });
                                } catch (final IllegalStateException closedAlready) {
                                    schedulingRefused.incrementAndGet();
                                }
                            }));
                        }

                        own.close();

                        for (final CompletableFuture<Integer> write : queued) {
                            require(write.isDone() && !write.isCompletedExceptionally(), "a queued write did not finish");
                        }
                        require(callbacks.get() == writes, "callbacks " + callbacks.get());
                        require(schedulingRefused.get() == 0, "the scheduler closed before the database: "
                                + schedulingRefused.get() + " callbacks could not schedule");
                        require(db.update("SELECT 1").isCompletedExceptionally(), "a call after close must fail its future");

                        again = SynthetixLib.init(plugin);
                        final SynthetixLib reopenedLib = again;
                        Database.create(reopenedLib, settings)
                                .query("SELECT COUNT(*) AS c FROM {prefix}rows", row -> row.intValue("c"))
                                .whenComplete((counted, failure) -> lib.scheduler().global().run(() -> {
                                    reopenedLib.close();
                                    if (failure != null) {
                                        done.completeExceptionally(failure);
                                    } else if (counted.get(0) != writes) {
                                        done.completeExceptionally(new IllegalStateException(
                                                "expected " + writes + " rows after the restart, found " + counted));
                                    } else {
                                        done.complete(null);
                                    }
                                }));
                    } catch (final Throwable error) {
                        if (again != null) {
                            again.close();
                        }
                        done.completeExceptionally(error);
                    }
                }));
        return done;
    }

    /** Copies a table between two SQLite files with Paper's driver (primary key metadata, paging, a text key). */
    private static CompletableFuture<Void> copy(final JavaPlugin plugin, final SynthetixLib lib) {
        freshQuietly(plugin, "selftest/copy-from.db");
        freshQuietly(plugin, "selftest/copy-to.db");
        final SynthetixLib own = SynthetixLib.init(plugin);
        final Database from = Database.create(own, DatabaseSettings.sqlite("selftest/copy-from.db").withTablePrefix("a_"));
        final Database to = Database.create(own, DatabaseSettings.sqlite("selftest/copy-to.db").withTablePrefix("b_"));
        final String create = "CREATE TABLE {prefix}items (id CHAR(36) PRIMARY KEY, amount BIGINT, data BLOB)";
        final List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            rows.add(new Object[] {new UUID(0L, i), (long) i, i % 2 == 0 ? null : new byte[] {(byte) i}});
        }

        final CompletableFuture<Void> result = from.update(create)
                .thenCompose(ignored -> to.update(create))
                .thenCompose(ignored -> from.batch("INSERT INTO {prefix}items VALUES (?, ?, ?)", rows))
                .thenCompose(ignored -> Copy.tables(from, to, List.of("items")).batchSize(10).run())
                .thenCompose(report -> {
                    require(report.rows("items") == 25L, "report " + report.rows());
                    return to.query("SELECT id, amount, data FROM {prefix}items ORDER BY id",
                            row -> row.uuid("id") + ":" + row.longValue("amount") + ":" + (row.bytesOrNull("data") == null));
                })
                .thenAccept(copied -> {
                    require(copied.size() == 25, "copied " + copied.size() + " rows");
                    require(copied.get(0).equals(new UUID(0L, 0).toString() + ":0:true"), "first row " + copied.get(0));
                    require(copied.get(1).equals(new UUID(0L, 1).toString() + ":1:false"), "second row " + copied.get(1));
                });

        // Closing waits for the worker, so it must not happen on it: hop to the global thread first.
        final CompletableFuture<Void> done = new CompletableFuture<>();
        result.whenComplete((ignored, error) -> lib.scheduler().global().run(() -> {
            own.close();
            if (error == null) {
                done.complete(null);
            } else {
                done.completeExceptionally(error);
            }
        }));
        return done;
    }

    /** The bundled {@code db/migrations} folder, read from the (shaded) plugin jar, applied twice on SQLite. */
    private static CompletableFuture<Void> migrate(final JavaPlugin plugin, final SynthetixLib lib) {
        freshQuietly(plugin, "selftest/migrate.db");
        final SynthetixLib own = SynthetixLib.init(plugin);
        final Database db = Database.create(own, DatabaseSettings.sqlite("selftest/migrate.db").withTablePrefix("mig_"));
        final Migrations migrations = Migrations.fromResources("db/migrations");

        final CompletableFuture<Void> result = db.migrate(migrations)
                .thenCompose(applied -> {
                    require(applied == 2, "expected 2 migrations applied, got " + applied);
                    return db.update("INSERT INTO {prefix}mail (player, item) VALUES (?, ?)", UUID.randomUUID(), null);
                })
                .thenCompose(ignored -> db.migrate(migrations))
                .thenCompose(applied -> {
                    require(applied == 0, "a second run must do nothing, applied " + applied);
                    return db.query("SELECT version, name FROM {prefix}schema_history ORDER BY version",
                            row -> row.intValue("version") + ":" + row.string("name"));
                })
                .thenAccept(history -> require(history.equals(List.of("1:init", "2:mail")), "history " + history));

        // Closing waits for the worker, so it must not happen on it: hop to the global thread first.
        final CompletableFuture<Void> done = new CompletableFuture<>();
        result.whenComplete((ignored, error) -> lib.scheduler().global().run(() -> {
            own.close();
            if (error == null) {
                done.complete(null);
            } else {
                done.completeExceptionally(error);
            }
        }));
        return done;
    }

    /** Insert, query, upsert and two transactions on SQLite with Paper's driver, including an ItemStack round trip. */
    private static CompletableFuture<Void> query(final JavaPlugin plugin, final SynthetixLib lib) {
        freshQuietly(plugin, "selftest/query.db");
        final SynthetixLib own = SynthetixLib.init(plugin);
        final Database db = Database.create(own, DatabaseSettings.sqlite("selftest/query.db").withTablePrefix("st_"));
        final UUID id = UUID.randomUUID();
        final ItemStack item = new ItemStack(Material.DIAMOND, 3);
        final String insert = "INSERT INTO {prefix}st VALUES (?, ?, ?, ?)";

        final CompletableFuture<Void> result = db
                .update("CREATE TABLE {prefix}st (id CHAR(36) PRIMARY KEY, item BLOB, at_ BIGINT, kind VARCHAR(30))")
                .thenCompose(ignored -> db.update(insert, id, item, Instant.ofEpochMilli(1234L), Material.STONE))
                .thenCompose(ignored -> db.queryOne("SELECT * FROM {prefix}st WHERE id = ?", row -> {
                    require(row.uuid("id").equals(id), "uuid");
                    require(row.itemStack("item").equals(item), "item " + row.itemStack("item"));
                    require(row.instant("at_").toEpochMilli() == 1234L, "instant");
                    require(row.enumOf("kind", Material.class) == Material.STONE, "enum");
                    return row.itemStackOrNull("item") != null;
                }, id))
                .thenCompose(read -> {
                    require(read.orElse(false), "queryOne returned " + read);
                    return db.upsert("{prefix}st", List.of("id"), Map.of("id", id, "at_", 99L));
                })
                .thenCompose(ignored -> db.transaction(tx -> {
                    tx.update(insert, UUID.randomUUID(), null, 1L, Material.DIRT);
                    return tx.query("SELECT at_ FROM {prefix}st ORDER BY at_", row -> row.longValue("at_"));
                }))
                .thenCompose(times -> {
                    require(times.equals(List.of(1L, 99L)), "transaction saw " + times);
                    return db.transaction(tx -> {
                        tx.update(insert, UUID.randomUUID(), null, 2L, Material.GRASS_BLOCK);
                        throw new IllegalStateException("roll me back");
                    }).handle((value, error) -> {
                        require(error != null && error.getMessage().contains("roll me back"), "transaction failed with " + error);
                        return null;
                    });
                })
                .thenCompose(ignored -> db.query("SELECT kind FROM {prefix}st", row -> row.string("kind")))
                .thenAccept(kinds -> require(kinds.size() == 2, "rollback kept a row: " + kinds));

        // Closing waits for the worker, so it must not happen on it: hop to the global thread first.
        final CompletableFuture<Void> done = new CompletableFuture<>();
        result.whenComplete((ignored, error) -> lib.scheduler().global().run(() -> {
            own.close();
            if (error == null) {
                done.complete(null);
            } else {
                done.completeExceptionally(error);
            }
        }));
        return done;
    }

    /** Waiting for a result on the real global thread warns, shortly after the database was opened. */
    private static CompletableFuture<Void> joinGuard(final JavaPlugin plugin, final SynthetixLib lib) {
        freshQuietly(plugin, "selftest/guard.db");
        final SynthetixLib own = SynthetixLib.init(plugin);
        final Database db = Database.create(own, DatabaseSettings.sqlite("selftest/guard.db"));
        final List<LogRecord> warnings = new CopyOnWriteArrayList<>();
        final Handler handler = new Handler() {
            @Override
            public void publish(final LogRecord record) {
                if (record.getLevel().equals(Level.WARNING) && record.getMessage().contains("waiting for the database")) {
                    warnings.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        plugin.getLogger().addHandler(handler);
        final CompletableFuture<Void> done = new CompletableFuture<>();
        // After the first tick: right after Database.create the plugin still counts as enabling and waiting is allowed.
        lib.scheduler().global().runLater(3, () -> {
            try {
                db.update("CREATE TABLE guard (n INTEGER)").join();
                require(warnings.size() == 1, "expected one warning, got " + warnings.size());
                require(warnings.get(0).getThrown() != null, "the warning must carry a stack");
                done.complete(null);
            } catch (final Throwable error) {
                done.completeExceptionally(error);
            } finally {
                plugin.getLogger().removeHandler(handler);
                own.close();
            }
        });
        return done;
    }

    private static Path fresh(final JavaPlugin plugin, final String name) throws IOException {
        final Path file = plugin.getDataFolder().toPath().resolve(name);
        Files.createDirectories(file.getParent());
        for (final String suffix : new String[] {"", "-wal", "-shm"}) {
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + suffix));
        }
        return file;
    }

    private static void freshQuietly(final JavaPlugin plugin, final String name) {
        try {
            fresh(plugin, name);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static long workerThreads(final String prefix) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.isAlive() && thread.getName().startsWith(prefix))
                .count();
    }
}
