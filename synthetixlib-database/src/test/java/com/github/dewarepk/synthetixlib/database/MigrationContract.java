package com.github.dewarepk.synthetixlib.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every rule of design section 5 once, run on SQLite ({@link SqliteMigrationTest}) and, when the owner's Gradle
 * properties are set, on MySQL ({@link MysqlMigrationTest}). Each test gets a table prefix of its own, because
 * {@code schema_history} is per prefix and a MySQL run shares the database between tests.
 */
abstract class MigrationContract {

    private static final AtomicInteger RUNS = new AtomicInteger();

    @TempDir
    Path dir;

    private final List<DatabaseImpl> opened = new ArrayList<>();
    private final List<Logger> loggers = new ArrayList<>();
    private final Capture log = new Capture();
    private int resourceRoots;

    /** @return the settings of the database under test */
    abstract DatabaseSettings settings();

    final DatabaseImpl open() {
        final DatabaseSettings base = settings();
        final DatabaseSettings own = base.withTablePrefix(base.tablePrefix() + RUNS.incrementAndGet() + "_");
        final Logger logger = Logger.getLogger("MigrationContract." + own.tablePrefix());
        logger.setUseParentHandlers(false);
        logger.addHandler(log);
        loggers.add(logger);
        final DatabaseImpl db = DatabaseImpl.open("Migrations", dir, logger, own, ServerThreadGuard.disabled());
        opened.add(db);
        return db;
    }

    @AfterEach
    void closeDatabases() {
        opened.forEach(DatabaseImpl::close);
        loggers.forEach(logger -> logger.removeHandler(log));
    }

    /** Collects what the database logs. */
    static final class Capture extends Handler {

        final List<LogRecord> records = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void publish(final LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    /** Writes the files to a folder and loads them like a plugin's {@code db/migrations} folder. */
    final Migrations migrations(final Map<String, String> files) {
        return migrations(files, false);
    }

    final Migrations migrations(final Map<String, String> files, final boolean crlf) {
        try {
            final Path root = dir.resolve("res" + resourceRoots++);
            final Path folder = root.resolve("m");
            Files.createDirectories(folder);
            for (final Map.Entry<String, String> file : files.entrySet()) {
                final String text = crlf ? file.getValue().replace("\n", "\r\n") : file.getValue();
                Files.write(folder.resolve(file.getKey()), text.getBytes(StandardCharsets.UTF_8));
            }
            return Migrations.fromResources(new URLClassLoader(new URL[] {root.toUri().toURL()}, null), "m");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Map<String, String> files(final String... namesAndTexts) {
        final Map<String, String> files = new LinkedHashMap<>();
        for (int i = 0; i < namesAndTexts.length; i += 2) {
            files.put(namesAndTexts[i], namesAndTexts[i + 1]);
        }
        return files;
    }

    static MigrationException migrationFailure(final CompletableFuture<?> future) {
        final ExecutionException error = assertThrows(ExecutionException.class, () -> future.get(30, TimeUnit.SECONDS));
        return assertInstanceOf(MigrationException.class, error.getCause());
    }

    static boolean tableExists(final DatabaseImpl db, final String table) {
        try {
            db.query("SELECT COUNT(*) AS n FROM {prefix}" + table, row -> row.longValue("n")).join();
            return true;
        } catch (final CompletionException e) {
            return false;
        }
    }

    static List<String> historyNames(final DatabaseImpl db) {
        return db.query("SELECT version, name FROM {prefix}schema_history ORDER BY version",
                row -> row.intValue("version") + ":" + row.string("name")).join();
    }

    static final String LOTS = "CREATE TABLE {prefix}lots (id INTEGER PRIMARY KEY, title VARCHAR(50));\n"
            + "INSERT INTO {prefix}lots VALUES (1, 'first');\n";

    // --- order, re-run ----------------------------------------------------------------------------------------------

    @Test
    void missingVersionsRunInOrderEachRecordedInTheHistory() {
        final DatabaseImpl db = open();
        final Migrations all = migrations(files(
                "V3__third.sql", "ALTER TABLE {prefix}lots ADD COLUMN note VARCHAR(20);\n"
                        + "UPDATE {prefix}lots SET note = 'v3 after';\n",
                "V1__init.sql", LOTS,
                "V2__second.sql", "INSERT INTO {prefix}lots VALUES (2, 'second'); -- a comment after it\n"));

        assertEquals(3, db.migrate(all).join());

        assertEquals(List.of("1:init", "2:second", "3:third"), historyNames(db));
        assertEquals(List.of("first", "second"), db.query("SELECT title FROM {prefix}lots ORDER BY id",
                row -> row.string("title")).join());
        assertEquals(List.of("v3 after", "v3 after"), db.query("SELECT note FROM {prefix}lots ORDER BY id",
                row -> row.string("note")).join());
    }

    @Test
    void aSecondRunDoesNothingAndANewVersionRunsAlone() {
        final DatabaseImpl db = open();
        final Map<String, String> v1v2 = files("V1__init.sql", LOTS,
                "V2__second.sql", "INSERT INTO {prefix}lots VALUES (2, 'second');\n");
        assertEquals(2, db.migrate(migrations(v1v2)).join());
        final List<Long> appliedAt = db.query("SELECT applied_at FROM {prefix}schema_history ORDER BY version",
                row -> row.longValue("applied_at")).join();

        assertEquals(0, db.migrate(migrations(v1v2)).join());
        assertEquals(appliedAt, db.query("SELECT applied_at FROM {prefix}schema_history ORDER BY version",
                row -> row.longValue("applied_at")).join());

        final Map<String, String> v3 = new LinkedHashMap<>(v1v2);
        v3.put("V3__third.sql", "INSERT INTO {prefix}lots VALUES (3, 'third');\n");
        assertEquals(1, db.migrate(migrations(v3)).join());
        assertEquals(List.of("1:init", "2:second", "3:third"), historyNames(db));
        assertEquals(3, db.query("SELECT id FROM {prefix}lots", row -> row.intValue("id")).join().size());
    }

    @Test
    void theHistoryTableHasTheTablePrefix() {
        final DatabaseImpl db = open();
        db.migrate(migrations(files("V1__init.sql", LOTS))).join();
        final List<Map<String, Object>> row = db.query(
                "SELECT version, name, checksum, applied_at FROM {prefix}schema_history", r -> Map.<String, Object>of(
                        "version", r.intValue("version"), "name", r.string("name"),
                        "checksum", r.string("checksum"), "at", r.longValue("applied_at"))).join();
        assertEquals(1, row.size());
        assertEquals(64, ((String) row.get(0).get("checksum")).length());
        assertTrue((Long) row.get(0).get("at") > 1_700_000_000_000L);
    }

    // --- checksum, downgrade ----------------------------------------------------------------------------------------

    @Test
    void aChangedShippedFileFailsAndNothingRuns() {
        final DatabaseImpl db = open();
        db.migrate(migrations(files("V1__init.sql", LOTS,
                "V2__second.sql", "INSERT INTO {prefix}lots VALUES (2, 'second');\n"))).join();

        final MigrationException error = migrationFailure(db.migrate(migrations(files(
                "V1__init.sql", LOTS.replace("first", "edited"),
                "V2__second.sql", "INSERT INTO {prefix}lots VALUES (2, 'second');\n",
                "V3__new.sql", "CREATE TABLE {prefix}fresh (id INTEGER PRIMARY KEY);\n"))));

        assertTrue(error.getMessage().contains("Migration 1 'init' was changed"), error.getMessage());
        assertFalse(tableExists(db, "fresh"), "the new version must not run");
        assertEquals(List.of("1:init", "2:second"), historyNames(db));
    }

    @Test
    void aDatabaseNewerThanThePluginFails() {
        final DatabaseImpl db = open();
        db.migrate(migrations(files("V1__init.sql", LOTS,
                "V2__second.sql", "INSERT INTO {prefix}lots VALUES (2, 'second');\n"))).join();

        final MigrationException error = migrationFailure(db.migrate(migrations(files("V1__init.sql", LOTS))));

        assertTrue(error.getMessage().contains("at migration 2") && error.getMessage().contains("only knows 1"),
                error.getMessage());
        assertEquals(List.of("1:init", "2:second"), historyNames(db));
    }

    @Test
    void lineEndingsDoNotChangeTheChecksum() {
        final DatabaseImpl db = open();
        final Map<String, String> sql = files("V1__init.sql", LOTS,
                "V2__second.sql", "INSERT INTO {prefix}lots VALUES (2, 'second');\n\nINSERT INTO {prefix}lots VALUES (3, 'x');\n");

        assertEquals(2, db.migrate(migrations(sql, false)).join());
        assertEquals(0, db.migrate(migrations(sql, true)).join(), "the same files with CRLF are the same migrations");
    }

    // --- gaps and duplicates ----------------------------------------------------------------------------------------

    @Test
    void aGapFailsBeforeAnythingRunsNotEvenTheHistoryTable() {
        final DatabaseImpl db = open();
        final Migrations gap = migrations(files("V1__init.sql", LOTS, "V3__third.sql", "SELECT 1;\n"));

        final MigrationException error = migrationFailure(db.migrate(gap));

        assertTrue(error.getMessage().contains("Migration 2 is missing"), error.getMessage());
        assertFalse(tableExists(db, "lots"));
        assertFalse(tableExists(db, "schema_history"));
    }

    // --- dialect overrides ------------------------------------------------------------------------------------------

    @Test
    void theFileOfTheDialectIsUsedInsteadOfThePlainOne() {
        final DatabaseImpl db = open();
        final String create = "CREATE TABLE {prefix}marker (n INTEGER, v VARCHAR(20));\n";
        final String insert = "INSERT INTO {prefix}marker VALUES (%d, '%s');\n";
        final Migrations migrations = migrations(files(
                "V1__marker.sql", create + insert.formatted(1, "base"),
                "V1__marker.mysql.sql", create + insert.formatted(1, "mysql"),
                "V1__marker.sqlite.sql", create + insert.formatted(1, "sqlite"),
                "V2__more.sql", insert.formatted(2, "base-2"),
                "V2__more.mysql.sql", insert.formatted(2, "mysql-2")));

        db.migrate(migrations).join();

        final boolean mysql = db.dialect() == Dialect.MYSQL;
        assertEquals(List.of(mysql ? "mysql" : "sqlite", mysql ? "mysql-2" : "base-2"),
                db.query("SELECT v FROM {prefix}marker ORDER BY n", row -> row.string("v")).join());
    }

    @Test
    void theChecksumIsThatOfTheChosenFile() {
        final Migrations migrations = migrations(files("V1__x.sql", "SELECT 1;\n", "V1__x.mysql.sql", "SELECT 2;\n"));
        final Migrations.Step step = migrations.steps().get(0);

        assertEquals(SqlScript.sha256("SELECT 1;\n"), step.checksum(Dialect.SQLITE));
        assertEquals(SqlScript.sha256("SELECT 2;\n"), step.checksum(Dialect.MYSQL));
    }

    @Test
    void aVersionWithOnlyAFileForAnotherDialectFailsHere() {
        final DatabaseImpl db = open();
        final String other = db.dialect() == Dialect.MYSQL ? "sqlite" : "mysql";

        final MigrationException error = migrationFailure(db.migrate(migrations(files(
                "V1__only.%s.sql".formatted(other), "SELECT 1;\n"))));

        assertTrue(error.getMessage().contains("no script for " + db.dialect()), error.getMessage());
        assertFalse(tableExists(db, "schema_history"));
    }

    // --- code migrations --------------------------------------------------------------------------------------------

    @Test
    void codeMigrationsRunInTheSequenceInsideTheirTransaction() {
        final DatabaseImpl db = open();
        final Migrations migrations = migrations(files(
                "V1__init.sql", "CREATE TABLE {prefix}t (n INTEGER);\n",
                "V3__after.sql", "UPDATE {prefix}t SET n = n + 1;\n"))
                .withCode(2, "fill", tx -> {
                    tx.update("INSERT INTO {prefix}t VALUES (?)", 10);
                    assertEquals(1, tx.query("SELECT n FROM {prefix}t", row -> row.intValue("n")).size());
                });

        assertEquals(3, db.migrate(migrations).join());
        assertEquals(List.of(11), db.query("SELECT n FROM {prefix}t", row -> row.intValue("n")).join());
        assertEquals(List.of("1:init", "2:fill", "3:after"), historyNames(db));
        assertEquals(0, db.migrate(migrations).join(), "a code migration is recorded like a file");
    }

    @Test
    void aFailingCodeMigrationRollsBackAndIsNotRecorded() {
        final DatabaseImpl db = open();
        final Migrations migrations = migrations(files("V1__init.sql", "CREATE TABLE {prefix}t (n INTEGER);\n"))
                .withCode(2, "boom", tx -> {
                    tx.update("INSERT INTO {prefix}t VALUES (1)");
                    throw new IllegalStateException("code went wrong");
                });

        final MigrationException error = migrationFailure(db.migrate(migrations));

        assertTrue(error.getMessage().contains("Migration 2 'boom'") && error.getMessage().contains("code went wrong"),
                error.getMessage());
        assertEquals(List.of(), db.query("SELECT n FROM {prefix}t", row -> row.intValue("n")).join());
        assertEquals(List.of("1:init"), historyNames(db));
    }

    @Test
    void aRenamedCodeMigrationCountsAsChanged() {
        final DatabaseImpl db = open();
        final Migrations files = migrations(files("V1__init.sql", "CREATE TABLE {prefix}t (n INTEGER);\n"));
        db.migrate(files.withCode(2, "fill", tx -> tx.update("INSERT INTO {prefix}t VALUES (1)"))).join();

        final MigrationException error = migrationFailure(db.migrate(
                files.withCode(2, "fill-again", tx -> tx.update("INSERT INTO {prefix}t VALUES (1)"))));

        assertTrue(error.getMessage().contains("Migration 2 'fill-again' was changed"), error.getMessage());
    }

    // --- splitting and comments -------------------------------------------------------------------------------------

    @Test
    void commentsAndSemicolonsInsideStringsSurviveSplitting() {
        final DatabaseImpl db = open();
        db.migrate(migrations(files("V1__text.sql", """
                -- header comment; with a semicolon
                CREATE TABLE {prefix}s (id INTEGER PRIMARY KEY, txt VARCHAR(100)); -- trailing comment
                INSERT INTO {prefix}s VALUES (1, 'a;b -- not a comment');
                INSERT INTO {prefix}s
                  VALUES (2, 'it''s');

                -- a last comment without a statement
                """))).join();

        assertEquals(List.of("a;b -- not a comment", "it's"), db.query("SELECT txt FROM {prefix}s ORDER BY id",
                row -> row.string("txt")).join());
    }

    // --- failure ----------------------------------------------------------------------------------------------------

    @Test
    void aFailingVersionIsUndoneAndStopsTheRunButEarlierVersionsStay() {
        final DatabaseImpl db = open();
        final Migrations migrations = migrations(files(
                "V1__init.sql", "CREATE TABLE {prefix}r (id INTEGER PRIMARY KEY);\n",
                "V2__bad.sql", "INSERT INTO {prefix}r VALUES (1);\nINSERT INTO {prefix}nosuchtable VALUES (2);\n",
                "V3__never.sql", "INSERT INTO {prefix}r VALUES (3);\n"));

        final MigrationException error = migrationFailure(db.migrate(migrations));

        assertTrue(error.getMessage().startsWith("Migration 2 'bad' failed"), error.getMessage());
        assertEquals(List.of(), db.query("SELECT id FROM {prefix}r", row -> row.intValue("id")).join());
        assertEquals(List.of("1:init"), historyNames(db));
    }

    @Test
    void aFailedMigrationIsLoggedOnceWithoutTheDriversText() {
        final DatabaseImpl db = open();
        migrationFailure(db.migrate(migrations(files("V1__bad.sql", "INSERT INTO {prefix}nosuchtable VALUES (1);\n"))));

        final List<LogRecord> severe = log.records.stream().filter(r -> r.getLevel().equals(Level.SEVERE)).toList();
        assertEquals(1, severe.size());
        assertTrue(severe.get(0).getMessage().startsWith("Migration 1 'bad' failed"), severe.get(0).getMessage());
        assertNull(severe.get(0).getThrown());
    }

    @Test
    void migratingAClosedDatabaseFailsTheFuture() {
        final DatabaseImpl db = open();
        db.close();
        final ExecutionException error = assertThrows(ExecutionException.class,
                () -> db.migrate(migrations(files("V1__init.sql", LOTS))).get(30, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, error.getCause());
    }
}
