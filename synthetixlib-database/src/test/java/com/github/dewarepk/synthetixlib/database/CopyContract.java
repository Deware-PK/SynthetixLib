package com.github.dewarepk.synthetixlib.database;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link Copy} between two databases with their own table prefixes, written once and run for SQLite to SQLite
 * ({@link SqliteCopyTest}) and, when the owner's Gradle properties are set, for every pair with MySQL
 * ({@link MysqlCopyTest}).
 */
abstract class CopyContract {

    @TempDir
    Path dir;

    private final List<DatabaseImpl> opened = new ArrayList<>();
    private final DatabaseQueryContract.Capture capture = new DatabaseQueryContract.Capture();
    private final Logger logger = Logger.getLogger("CopyContract");

    /** @return the database rows are copied from (its table prefix differs from {@link #target()}) */
    abstract DatabaseSettings source();

    /** @return the database rows are copied to */
    abstract DatabaseSettings target();

    private DatabaseImpl open(final String plugin, final DatabaseSettings settings) {
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(capture);
        final DatabaseImpl db = DatabaseImpl.open(plugin, dir, logger, settings, ServerThreadGuard.disabled());
        opened.add(db);
        return db;
    }

    @AfterEach
    void closeDatabases() {
        opened.forEach(DatabaseImpl::close);
        logger.removeHandler(capture);
    }

    private static <T> T result(final CompletableFuture<T> future) throws Exception {
        return future.get(60, TimeUnit.SECONDS);
    }

    private static List<Object[]> rows(final int count, final java.util.function.IntFunction<Object[]> row) {
        final List<Object[]> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(row.apply(i));
        }
        return rows;
    }

    /** @return every row as text, in key order, so two tables can be compared whole (nulls and blobs included) */
    private static List<String> dump(final DatabaseImpl db, final String table, final String order) {
        return db.query("SELECT * FROM {prefix}" + table + " ORDER BY " + order, row -> {
            final StringBuilder text = new StringBuilder();
            for (final String column : List.of("id", "price", "note", "n", "who", "shop", "item", "amount", "a", "b", "v")) {
                try {
                    text.append(column).append('=').append(row.stringOrNull(column)).append(';');
                } catch (final RuntimeException notThisTable) {
                    // the column does not exist in this table
                }
            }
            try {
                text.append("data=").append(Arrays.toString(row.bytesOrNull("data")));
            } catch (final RuntimeException notThisTable) {
                // idem
            }
            return text.toString();
        }).join();
    }

    // --- the happy path -------------------------------------------------------------------------------------------

    @Test
    void copiesTwoTablesOfTenThousandAndTwentyFiveHundredRows() throws Exception {
        final DatabaseImpl from = open("CopyFrom1", source());
        final DatabaseImpl to = open("CopyTo1", target());
        for (final DatabaseImpl db : List.of(from, to)) {
            db.update("CREATE TABLE {prefix}lots (id CHAR(36) PRIMARY KEY, price BIGINT, note TEXT, data BLOB)").join();
            db.update("CREATE TABLE {prefix}mail (n INTEGER PRIMARY KEY, who CHAR(36))").join();
        }
        from.batch("INSERT INTO {prefix}lots VALUES (?, ?, ?, ?)", rows(10_000, i -> new Object[] {
                String.format("00000000-0000-0000-0000-%012d", i), (long) i * 1_000_003L,
                i % 7 == 0 ? null : "note åäö 日本 " + i,
                i % 5 == 0 ? null : new byte[] {(byte) i, 0, (byte) (i >> 8), -1}})).join();
        from.batch("INSERT INTO {prefix}mail VALUES (?, ?)", rows(2_500, i -> new Object[] {i, "player-" + i})).join();

        final Copy.Report report = result(Copy.tables(from, to, List.of("lots", "mail")).batchSize(500).run());

        assertEquals(10_000L, report.rows("lots"));
        assertEquals(2_500L, report.rows("mail"));
        assertEquals(12_500L, report.total());
        assertEquals(List.of("lots", "mail"), List.copyOf(report.rows().keySet()));
        assertEquals(dump(from, "lots", "id"), dump(to, "lots", "id"));
        assertEquals(dump(from, "mail", "n"), dump(to, "mail", "n"));
        // blobs and nulls are really there, not just their text
        final byte[] blob = to.queryOne("SELECT data FROM {prefix}lots WHERE id = ?", row -> row.bytes("data"),
                "00000000-0000-0000-0000-000000000003").join().orElseThrow();
        assertArrayEquals(new byte[] {3, 0, 0, -1}, blob);
        assertEquals(1429, to.queryOne("SELECT COUNT(*) AS c FROM {prefix}lots WHERE note IS NULL",
                row -> row.intValue("c")).join().orElseThrow());
        assertEquals(2000, to.queryOne("SELECT COUNT(*) AS c FROM {prefix}lots WHERE data IS NULL",
                row -> row.intValue("c")).join().orElseThrow());
    }

    @Test
    void aCompositePrimaryKeyAndAPageSizeThatDoesNotDivideTheRows() throws Exception {
        final DatabaseImpl from = open("CopyFrom2", source());
        final DatabaseImpl to = open("CopyTo2", target());
        for (final DatabaseImpl db : List.of(from, to)) {
            db.update("CREATE TABLE {prefix}stock (shop INTEGER, item VARCHAR(30), amount BIGINT, "
                    + "PRIMARY KEY (shop, item))").join();
        }
        // shop 1..10 x item "a".."j" in a scrambled insert order: paging must follow the key, not insertion order
        final List<Object[]> stock = new ArrayList<>();
        for (int shop = 10; shop >= 1; shop--) {
            for (int item = 9; item >= 0; item--) {
                stock.add(new Object[] {shop, String.valueOf((char) ('a' + item)), (long) shop * 100 + item});
            }
        }
        from.batch("INSERT INTO {prefix}stock VALUES (?, ?, ?)", stock).join();

        final Copy.Report report = result(Copy.tables(from, to, List.of("stock")).batchSize(7).run());

        assertEquals(100L, report.rows("stock"));
        assertEquals(dump(from, "stock", "shop, item"), dump(to, "stock", "shop, item"));
        assertEquals(100, to.queryOne("SELECT COUNT(*) AS c FROM {prefix}stock", row -> row.intValue("c")).join().orElseThrow());
    }

    @Test
    void anEmptyTableCopiesZeroRowsAndThePrefixFormIsAccepted() throws Exception {
        final DatabaseImpl from = open("CopyFrom3", source());
        final DatabaseImpl to = open("CopyTo3", target());
        for (final DatabaseImpl db : List.of(from, to)) {
            db.update("CREATE TABLE {prefix}empty (id INTEGER PRIMARY KEY)").join();
            db.update("CREATE TABLE {prefix}full (id INTEGER PRIMARY KEY)").join();
        }
        from.batch("INSERT INTO {prefix}full VALUES (?)", List.of(new Object[] {1}, new Object[] {2})).join();

        final Copy.Report report = result(Copy.tables(from, to, List.of("empty", "{prefix}full")).run());

        assertEquals(Map.of("empty", 0L, "{prefix}full", 2L), report.rows());
        assertEquals(2, to.queryOne("SELECT COUNT(*) AS c FROM {prefix}full", row -> row.intValue("c")).join().orElseThrow());
    }

    @Test
    void noTablesIsAnEmptyReport() throws Exception {
        final DatabaseImpl from = open("CopyFrom4", source());
        final DatabaseImpl to = open("CopyTo4", target());

        final Copy.Report report = result(Copy.tables(from, to, List.of()).run());

        assertEquals(0L, report.total());
        assertTrue(report.rows().isEmpty());
    }

    // --- tables that cannot be paged, rows that exist, tables that do not ----------------------------------------------

    @Test
    void aTableWithoutAPrimaryKeyIsPagedByRowidOnSqliteAndRefusedOnMysql() throws Exception {
        final DatabaseImpl from = open("CopyFrom5", source());
        final DatabaseImpl to = open("CopyTo5", target());
        for (final DatabaseImpl db : List.of(from, to)) {
            db.update("CREATE TABLE {prefix}loose (a INTEGER, b VARCHAR(20))").join();
        }
        from.batch("INSERT INTO {prefix}loose VALUES (?, ?)", rows(25, i -> new Object[] {i % 3, "b" + i})).join();

        final CompletableFuture<Copy.Report> copy = Copy.tables(from, to, List.of("loose")).batchSize(10).run();

        if (from.dialect() == Dialect.SQLITE) {
            assertEquals(25L, result(copy).rows("loose"));
            assertEquals(dump(from, "loose", "b"), dump(to, "loose", "b"));
        } else {
            final Throwable error = DatabaseQueryContract.failureOf(copy);
            assertInstanceOf(IllegalArgumentException.class, error);
            assertTrue(error.getMessage().contains("no primary key"), error.getMessage());
        }
    }

    @Test
    void aRowThatExistsAlreadyFailsItsPageAndLeavesTheOthersAlone() throws Exception {
        final DatabaseImpl from = open("CopyFrom6", source());
        final DatabaseImpl to = open("CopyTo6", target());
        for (final DatabaseImpl db : List.of(from, to)) {
            db.update("CREATE TABLE {prefix}dup (id INTEGER PRIMARY KEY, v VARCHAR(20))").join();
        }
        from.batch("INSERT INTO {prefix}dup VALUES (?, ?)", rows(30, i -> new Object[] {i, "v" + i})).join();
        to.update("INSERT INTO {prefix}dup VALUES (?, ?)", 12, "already here").join();

        final Throwable error = DatabaseQueryContract.failureOf(Copy.tables(from, to, List.of("dup")).batchSize(10).run());

        assertInstanceOf(DatabaseException.class, error);
        // page 1 (ids 0..9) was written, page 2 (10..19) holds the duplicate and was rolled back as a whole
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 12),
                to.query("SELECT id FROM {prefix}dup ORDER BY id", row -> row.intValue("id")).join());
        assertEquals("already here", to.queryOne("SELECT v FROM {prefix}dup WHERE id = 12", row -> row.string("v"))
                .join().orElseThrow());
    }

    @Test
    void aMissingTableFailsWithTheStatementAndNoValues() {
        final DatabaseImpl from = open("CopyFrom7", source());
        final DatabaseImpl to = open("CopyTo7", target());

        final Throwable error = DatabaseQueryContract.failureOf(Copy.tables(from, to, List.of("nothing")).run());

        assertInstanceOf(DatabaseException.class, error);
        assertTrue(((DatabaseException) error).sql().contains("nothing"), error.getMessage());
    }

    @Test
    void aClosedTargetFailsTheCopy() {
        final DatabaseImpl from = open("CopyFrom8", source());
        final DatabaseImpl to = open("CopyTo8", target());
        from.update("CREATE TABLE {prefix}closed (id INTEGER PRIMARY KEY)").join();
        from.update("INSERT INTO {prefix}closed VALUES (1)").join();
        to.close();

        final Throwable error = DatabaseQueryContract.failureOf(Copy.tables(from, to, List.of("closed")).run());

        assertInstanceOf(DatabaseClosedException.class, error);
    }

    // --- arguments ------------------------------------------------------------------------------------------------

    @Test
    void badArgumentsAreRefusedAtTheCall() {
        final DatabaseImpl from = open("CopyFrom9", source());
        final DatabaseImpl to = open("CopyTo9", target());

        assertThrows(IllegalArgumentException.class, () -> Copy.tables(from, from, List.of("a")));
        assertThrows(IllegalArgumentException.class, () -> Copy.tables(from, to, List.of("a b")));
        assertThrows(IllegalArgumentException.class, () -> Copy.tables(from, to, List.of("a;DROP TABLE x")));
        assertThrows(IllegalArgumentException.class, () -> Copy.tables(from, to, List.of("a", "a")));
        assertThrows(IllegalArgumentException.class, () -> Copy.tables(from, to, List.of("a", "{prefix}a")));
        assertThrows(IllegalArgumentException.class, () -> Copy.tables(from, to, List.of("a")).batchSize(0));
    }
}
