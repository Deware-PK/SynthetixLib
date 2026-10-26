package com.github.dewarepk.synthetixlib.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The database on real SQLite in a temp folder. */
class SqliteDatabaseTest {

    @TempDir
    Path dir;

    private DatabaseImpl open(final String plugin, final DatabaseSettings settings) {
        return DatabaseImpl.open(plugin, dir, Logger.getLogger("test"), settings);
    }

    /** The live worker threads of one database ({@code <plugin>-db-<n>}); every test uses its own plugin name. */
    static List<Thread> threadsOf(final String plugin) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().startsWith(plugin + "-db-") && thread.isAlive())
                .toList();
    }

    private static String scalar(final Connection connection, final String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            return rows.getString(1);
        }
    }

    @Test
    void createConnectsAndSelectOneWorks() throws SQLException {
        try (DatabaseImpl db = open("SqliteOne", DatabaseSettings.sqlite("test.db"));
             Connection connection = db.connection()) {
            assertEquals(Dialect.SQLITE, db.dialect());
            assertEquals("1", scalar(connection, "SELECT 1"));
        }
        assertTrue(Files.exists(dir.resolve("test.db")));
    }

    @Test
    void theFileMayLiveInASubfolderThatDoesNotExistYet() {
        try (DatabaseImpl db = open("SqliteSub", DatabaseSettings.sqlite("data/deeper/test.db"))) {
            assertEquals(Dialect.SQLITE, db.dialect());
        }
        assertTrue(Files.exists(dir.resolve("data/deeper/test.db")));
    }

    @Test
    void thePragmasAreAppliedToEveryConnection() throws SQLException {
        try (DatabaseImpl db = open("SqlitePragmas", DatabaseSettings.sqlite("test.db"))) {
            // twice: the pool hands out the same connection, but a replaced one must have them too
            for (int round = 0; round < 2; round++) {
                try (Connection connection = db.connection()) {
                    assertEquals("wal", scalar(connection, "PRAGMA journal_mode"));
                    assertEquals("5000", scalar(connection, "PRAGMA busy_timeout"));
                    assertEquals("1", scalar(connection, "PRAGMA foreign_keys"));
                    assertEquals("1", scalar(connection, "PRAGMA synchronous"));
                }
            }
        }
    }

    @Test
    void foreignKeysAreReallyEnforced() throws SQLException {
        try (DatabaseImpl db = open("SqliteFk", DatabaseSettings.sqlite("test.db"));
             Connection connection = db.connection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
            statement.execute("CREATE TABLE child (id INTEGER PRIMARY KEY, parent INTEGER REFERENCES parent(id))");
            assertThrows(SQLException.class, () -> statement.execute("INSERT INTO child VALUES (1, 99)"));
        }
    }

    @Test
    void sqliteGetsOneConnectionAndOneWorkerWhateverThePoolSizeSays() throws SQLException {
        final DatabaseSettings base = DatabaseSettings.sqlite("test.db");
        final DatabaseSettings settings = new DatabaseSettings(base.type(), base.tablePrefix(), base.sqlite(),
                base.mysql(), new DatabaseSettings.Pool(10, Duration.ofMillis(300), Duration.ofMinutes(30)),
                base.shutdownTimeout());
        try (DatabaseImpl db = open("SqliteSize", settings)) {
            assertEquals(1, threadsOf("SqliteSize").size());
            try (Connection first = db.connection()) {
                assertEquals("1", scalar(first, "SELECT 1"));
                final SQLException second = assertThrows(SQLException.class, db::connection);
                assertTrue(second.getMessage().contains("request timed out"), second.getMessage());
            }
        }
    }

    @Test
    void workerThreadsAreNamedAfterThePluginAndStopOnClose() {
        final DatabaseImpl db = open("SqliteNames", DatabaseSettings.sqlite("test.db"));
        final List<Thread> threads = threadsOf("SqliteNames");
        assertEquals(1, threads.size());
        assertEquals("SqliteNames-db-1", threads.get(0).getName());
        assertFalse(threads.get(0).isDaemon());

        db.close();

        assertEquals(0, threadsOf("SqliteNames").size());
    }

    @Test
    void closeIsIdempotentAndWaitsForAQueuedStatement() throws Exception {
        final DatabaseImpl db = open("SqliteClose", DatabaseSettings.sqlite("test.db"));
        final java.util.concurrent.Future<String> pending = db.worker().submit(() -> {
            Thread.sleep(300);
            try (Connection connection = db.connection()) {
                return scalar(connection, "SELECT 'done'");
            }
        });

        db.close();
        db.close();

        assertEquals("done", pending.get(0, TimeUnit.SECONDS));
        assertTrue(db.worker().isTerminated());
    }

    @Test
    void closeInterruptsWhatIsStillRunningAfterTheShutdownTimeout() throws Exception {
        final DatabaseSettings base = DatabaseSettings.sqlite("test.db");
        final DatabaseSettings settings = new DatabaseSettings(base.type(), base.tablePrefix(), base.sqlite(),
                base.mysql(), base.pool(), Duration.ofMillis(200));
        final DatabaseImpl db = open("SqliteStuck", settings);
        final java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.Future<?> stuck = db.worker().submit(() -> {
            started.countDown();
            Thread.sleep(60_000);
            return null;
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));

        db.close();

        assertThrows(java.util.concurrent.ExecutionException.class, () -> stuck.get(5, TimeUnit.SECONDS));
        assertEquals(0, threadsOf("SqliteStuck").size());
    }

    @Test
    void aFailedOpenLeavesNoThreadsBehind() {
        final DatabaseSettings base = DatabaseSettings.mysql("127.0.0.1", 1, "nothing", "u", "p");
        final DatabaseSettings settings = new DatabaseSettings(base.type(), base.tablePrefix(), base.sqlite(),
                base.mysql(), new DatabaseSettings.Pool(2, Duration.ofMillis(300), Duration.ofMinutes(30)),
                base.shutdownTimeout());

        final IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> open("SqliteNoServer", settings));

        assertTrue(error.getMessage().startsWith("Cannot connect to the mysql database at jdbc:mysql://127.0.0.1:1/nothing"),
                error.getMessage());
        assertEquals(0, threadsOf("SqliteNoServer").size());
    }

    @Test
    void anInvalidTablePrefixIsRefusedBeforeAnythingIsOpened() {
        assertThrows(IllegalArgumentException.class,
                () -> open("SqliteBad", DatabaseSettings.sqlite("test.db").withTablePrefix("a;b")));
        assertFalse(Files.exists(dir.resolve("test.db")));
        assertEquals(0, threadsOf("SqliteBad").size());
    }

    // --- missing pieces: clear messages ---------------------------------------------------------------------------

    @Test
    void aMissingDriverNamesTheJarToAdd() {
        final IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DatabaseImpl.requireDriver("jdbc:nothing:somewhere", "org.example:nothing-jdbc"));

        assertTrue(error.getMessage().contains("jdbc:nothing:somewhere"), error.getMessage());
        assertTrue(error.getMessage().contains("org.example:nothing-jdbc"), error.getMessage());
    }

    /** Runs {@link DatabaseImpl#open} in a class loader that cannot see HikariCP, as in a plugin without the loader. */
    @Test
    void missingHikariCpSaysWhatToAddToThePluginLoader() throws Exception {
        final List<URL> urls;
        try (Stream<String> entries = Stream.of(System.getProperty("java.class.path").split(java.io.File.pathSeparator))) {
            urls = entries.map(entry -> {
                try {
                    return Path.of(entry).toUri().toURL();
                } catch (final java.net.MalformedURLException e) {
                    throw new IllegalStateException(e);
                }
            }).toList();
        }
        try (URLClassLoader hidden = new HidesHikari(urls.toArray(new URL[0]))) {
            @SuppressWarnings("unchecked")
            final Supplier<String> probe = (Supplier<String>) hidden.loadClass(HiddenHikariProbe.class.getName())
                    .getDeclaredConstructor(Path.class).newInstance(dir);
            assertThrows(ClassNotFoundException.class, () -> hidden.loadClass("com.zaxxer.hikari.HikariConfig"));

            final String message = probe.get();

            assertTrue(message.contains("HikariCP is not on the plugin's class path"), message);
            assertTrue(message.contains("SynthetixLibraries.database(classpath)"), message);
            assertTrue(message.contains("PluginLoader"), message);
        }
    }

    @Test
    void standaloneWithoutHikariCpSaysWhichTestDependenciesToAdd() throws Exception {
        final List<URL> urls;
        try (Stream<String> entries = Stream.of(System.getProperty("java.class.path").split(java.io.File.pathSeparator))) {
            urls = entries.map(entry -> {
                try {
                    return Path.of(entry).toUri().toURL();
                } catch (final java.net.MalformedURLException e) {
                    throw new IllegalStateException(e);
                }
            }).toList();
        }
        try (URLClassLoader hidden = new HidesHikari(urls.toArray(new URL[0]))) {
            @SuppressWarnings("unchecked")
            final Supplier<String> probe = (Supplier<String>) hidden.loadClass(HiddenHikariProbe.class.getName())
                    .getDeclaredConstructor(Path.class, boolean.class).newInstance(dir, true);

            final String message = probe.get();

            assertTrue(message.contains("add com.zaxxer:HikariCP and org.xerial:sqlite-jdbc"), message);
            assertTrue(message.contains("testImplementation"), message);
        }
    }

    /** A class loader that has every class of the test class path except HikariCP; the JDBC drivers stay shared. */
    private static final class HidesHikari extends URLClassLoader {

        HidesHikari(final URL[] urls) {
            super(urls, ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("com.zaxxer.")) {
                throw new ClassNotFoundException(name);
            }
            if (name.startsWith("org.sqlite.") || name.startsWith("com.mysql.")) {
                // the registered drivers: DriverManager only hands out a driver the caller's loader sees as the same class
                return SqliteDatabaseTest.class.getClassLoader().loadClass(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
