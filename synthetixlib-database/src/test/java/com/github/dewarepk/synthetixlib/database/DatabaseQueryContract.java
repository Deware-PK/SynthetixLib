package com.github.dewarepk.synthetixlib.database;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Everything the query API promises, written once and run against SQLite ({@link SqliteQueryTest}) and, when the
 * owner's Gradle properties are set, MySQL ({@link MysqlQueryTest}). Tables are created with {@code {prefix}} and a name
 * of their own per test, because a MySQL run shares one prefix per class (the extension drops them afterwards).
 */
abstract class DatabaseQueryContract {

    enum Kind { FIRST, SECOND }

    @TempDir
    Path dir;

    private final List<DatabaseImpl> opened = new ArrayList<>();
    private final List<Logger> loggers = new ArrayList<>();
    private final Capture capture = new Capture();

    /** @return the settings of the database under test, with a table prefix */
    abstract DatabaseSettings settings();

    final DatabaseImpl open(final String plugin) {
        return open(plugin, ServerThreadGuard.disabled());
    }

    final DatabaseImpl open(final String plugin, final ServerThreadGuard guard) {
        final Logger logger = Logger.getLogger("DatabaseQueryContract." + plugin);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(capture);
        loggers.add(logger);
        final DatabaseImpl db = DatabaseImpl.open(plugin, dir, logger, settings(), guard);
        opened.add(db);
        return db;
    }

    @AfterEach
    void closeDatabases() {
        opened.forEach(DatabaseImpl::close);
        loggers.forEach(logger -> logger.removeHandler(capture));
    }

    /** Collects what a database logs. */
    static final class Capture extends Handler {

        final List<LogRecord> records = new CopyOnWriteArrayList<>();

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

        /** @return everything a record shows: message, thrown exceptions, causes, suppressed, as one text */
        String everything() {
            final StringBuilder text = new StringBuilder();
            for (final LogRecord record : records) {
                text.append(record.getMessage()).append('\n');
                for (Throwable t = record.getThrown(); t != null; t = t.getCause()) {
                    text.append(t).append('\n');
                    for (final Throwable suppressed : t.getSuppressed()) {
                        text.append(suppressed).append('\n');
                    }
                }
            }
            return text.toString();
        }

        List<LogRecord> severe() {
            return records.stream().filter(record -> record.getLevel().equals(Level.SEVERE)).toList();
        }
    }

    static <T> Throwable failureOf(final CompletableFuture<T> future) {
        final ExecutionException error = assertThrows(ExecutionException.class, () -> future.get(30, TimeUnit.SECONDS));
        return error.getCause();
    }

    // --- binding and reading ----------------------------------------------------------------------------------------

    record Everything(UUID id, String text, int number, long big, double real, boolean flag, byte[] bytes,
                      Instant at, Duration length, Kind kind) {
    }

    @Test
    void everyTypeRoundTripsIncludingNulls() {
        final DatabaseImpl db = open("QueryTypes");
        db.update("CREATE TABLE {prefix}types (id CHAR(36) PRIMARY KEY, text VARCHAR(100), number INTEGER, "
                + "big BIGINT, real_ DOUBLE, flag BOOLEAN, bytes BLOB, at_ BIGINT, length_ BIGINT, kind VARCHAR(20))").join();
        final UUID id = UUID.randomUUID();
        final Instant at = Instant.ofEpochMilli(1_760_000_000_123L);
        final Everything expected = new Everything(id, "héllo ✓ 日本語 😀", -42, 1L << 40, 2.5, true,
                new byte[] {0, 1, 2, (byte) 255}, at, Duration.ofMillis(90_061_001L), Kind.SECOND);

        final int inserted = db.update("INSERT INTO {prefix}types VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                expected.id(), expected.text(), expected.number(), expected.big(), expected.real(), expected.flag(),
                expected.bytes(), expected.at(), expected.length(), expected.kind()).join();
        final List<Everything> rows = db.query("SELECT * FROM {prefix}types WHERE id = ?", row -> new Everything(
                row.uuid("id"), row.string("text"), row.intValue("number"), row.longValue("big"),
                row.doubleValue("real_"), row.bool("flag"), row.bytes("bytes"), row.instant("at_"),
                row.duration("length_"), row.enumOf("kind", Kind.class)), id).join();

        assertEquals(1, inserted);
        assertEquals(1, rows.size());
        final Everything actual = rows.get(0);
        assertEquals(expected.id(), actual.id());
        assertEquals(expected.text(), actual.text());
        assertEquals(expected.number(), actual.number());
        assertEquals(expected.big(), actual.big());
        assertEquals(expected.real(), actual.real());
        assertTrue(actual.flag());
        assertArrayEquals(expected.bytes(), actual.bytes());
        assertEquals(expected.at(), actual.at());
        assertEquals(expected.length(), actual.length());
        assertEquals(Kind.SECOND, actual.kind());
    }

    @Test
    void falseAndNullsAreStoredAndReadAsNulls() {
        final DatabaseImpl db = open("QueryNulls");
        db.update("CREATE TABLE {prefix}nulls (id CHAR(36) PRIMARY KEY, text VARCHAR(100), number INTEGER, big BIGINT, "
                + "real_ DOUBLE, flag BOOLEAN, bytes BLOB, kind VARCHAR(20))").join();
        final UUID id = UUID.randomUUID();
        final Object[] nothing = {id, null, null, null, null, null, null, null};

        db.update("INSERT INTO {prefix}nulls VALUES (?, ?, ?, ?, ?, ?, ?, ?)", nothing).join();
        final Optional<String> read = db.queryOne("SELECT * FROM {prefix}nulls", row -> {
            assertNull(row.stringOrNull("text"));
            assertNull(row.uuidOrNull("text"));
            assertNull(row.intValueOrNull("number"));
            assertNull(row.longValueOrNull("big"));
            assertNull(row.doubleValueOrNull("real_"));
            assertNull(row.boolOrNull("flag"));
            assertNull(row.bytesOrNull("bytes"));
            assertNull(row.instantOrNull("big"));
            assertNull(row.durationOrNull("big"));
            assertNull(row.enumOfOrNull("kind", Kind.class));
            assertNull(row.itemStackOrNull("bytes"));
            final IllegalStateException plain = assertThrows(IllegalStateException.class, () -> row.string("text"));
            assertTrue(plain.getMessage().contains("\"text\"") && plain.getMessage().contains("stringOrNull"),
                    plain.getMessage());
            return "ok";
        }).join();
        assertEquals(Optional.of("ok"), read);

        db.update("UPDATE {prefix}nulls SET flag = ?", false).join();
        assertEquals(Optional.of(false), db.queryOne("SELECT flag FROM {prefix}nulls", row -> row.bool("flag")).join());
    }

    @Test
    void anUnsupportedParameterTypeFailsAtTheCallBeforeAnythingIsQueued() {
        final DatabaseImpl db = open("QueryBadType");
        final IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> db.update("SELECT ?", UUID.randomUUID(), new java.util.Date(0)));
        assertTrue(error.getMessage().contains("Parameter 2") && error.getMessage().contains("java.util.Date"),
                error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> db.query("SELECT ?", row -> 1, 1.5f));
        assertThrows(IllegalArgumentException.class, () -> db.batch("SELECT ?", List.of(new Object[] {1}, new Object[] {'c'})));
        assertInstanceOf(IllegalArgumentException.class,
                failureOf(db.transaction(tx -> tx.update("SELECT ?", new StringBuilder()))));
    }

    @Test
    void rowErrorsNameTheColumn() {
        final DatabaseImpl db = open("QueryRowErrors");
        db.update("CREATE TABLE {prefix}rowerr (id CHAR(36) PRIMARY KEY, text VARCHAR(100), number BIGINT)").join();
        db.update("INSERT INTO {prefix}rowerr VALUES (?, ?, ?)", UUID.randomUUID(), "not a number", 5_000_000_000L).join();

        final String missing = rowError(db, row -> row.string("nope"));
        assertTrue(missing.contains("\"nope\"") && missing.contains("text"), missing);
        final String wrongNumber = rowError(db, row -> row.longValue("text"));
        assertTrue(wrongNumber.contains("\"text\"") && wrongNumber.contains("long"), wrongNumber);
        final String wrongUuid = rowError(db, row -> row.uuid("text"));
        assertTrue(wrongUuid.contains("\"text\"") && wrongUuid.contains("UUID"), wrongUuid);
        final String wrongText = rowError(db, row -> row.string("number"));
        assertTrue(wrongText.contains("\"number\"") && wrongText.contains("String"), wrongText);
        final String tooBig = rowError(db, row -> row.intValue("number"));
        assertTrue(tooBig.contains("\"number\"") && tooBig.contains("int"), tooBig);
        final String wrongEnum = rowError(db, row -> row.enumOf("text", Kind.class));
        assertTrue(wrongEnum.contains("\"text\"") && wrongEnum.contains("Kind"), wrongEnum);
        final String wrongFlag = rowError(db, row -> row.bool("number"));
        assertTrue(wrongFlag.contains("\"number\"") && wrongFlag.contains("boolean"), wrongFlag);
        final String wrongBytes = rowError(db, row -> row.bytes("text"));
        assertTrue(wrongBytes.contains("\"text\""), wrongBytes);
    }

    private static String rowError(final DatabaseImpl db, final RowMapper<Object> mapper) {
        final Throwable error = failureOf(db.query("SELECT * FROM {prefix}rowerr", mapper));
        assertInstanceOf(RuntimeException.class, error);
        assertFalse(error.getMessage().contains("not a number"), "the value must not be in the message: " + error.getMessage());
        return error.getMessage();
    }

    // --- query, queryOne, update --------------------------------------------------------------------------------------

    @Test
    void queryReturnsAllRowsInOrderAndQueryOneTheFirstOrNothing() {
        final DatabaseImpl db = open("QueryOne");
        db.update("CREATE TABLE {prefix}items (n INTEGER PRIMARY KEY, name VARCHAR(20))").join();
        db.batch("INSERT INTO {prefix}items VALUES (?, ?)",
                List.of(new Object[] {3, "c"}, new Object[] {1, "a"}, new Object[] {2, "b"})).join();

        assertEquals(List.of("a", "b", "c"), db.query("SELECT name FROM {prefix}items ORDER BY n", row -> row.string("name")).join());
        assertEquals(List.of("b", "c"), db.query("SELECT name FROM {prefix}items WHERE n > ? ORDER BY n", row -> row.string("name"), 1).join());
        assertEquals(Optional.of("a"), db.queryOne("SELECT name FROM {prefix}items ORDER BY n", row -> row.string("name")).join());
        assertEquals(Optional.empty(), db.queryOne("SELECT name FROM {prefix}items WHERE n = ?", row -> row.string("name"), 99).join());
        assertEquals(List.of(), db.query("SELECT name FROM {prefix}items WHERE n = ?", row -> row.string("name"), 99).join());
    }

    @Test
    void updateReturnsTheNumberOfChangedRows() {
        final DatabaseImpl db = open("QueryUpdate");
        db.update("CREATE TABLE {prefix}upd (n INTEGER PRIMARY KEY, v INTEGER)").join();
        db.batch("INSERT INTO {prefix}upd VALUES (?, ?)", List.of(new Object[] {1, 0}, new Object[] {2, 0}, new Object[] {3, 0})).join();

        assertEquals(2, db.update("UPDATE {prefix}upd SET v = ? WHERE n < ?", 7, 3).join());
        assertEquals(0, db.update("UPDATE {prefix}upd SET v = ? WHERE n > ?", 7, 10).join());
        assertEquals(1, db.update("DELETE FROM {prefix}upd WHERE n = ?", 3).join());
    }

    @Test
    void prefixIsReplacedEverywhereInAStatement() {
        final DatabaseImpl db = open("QueryPrefix");
        final String prefix = settings().tablePrefix();
        assertFalse(prefix.isEmpty());
        db.update("CREATE TABLE {prefix}pa (n INTEGER PRIMARY KEY)").join();
        db.update("CREATE TABLE {prefix}pb (n INTEGER PRIMARY KEY)").join();
        db.update("INSERT INTO {prefix}pa VALUES (1)").join();
        db.update("INSERT INTO {prefix}pb VALUES (1)").join();

        final String joined = db.queryOne("SELECT '{prefix}' AS p FROM {prefix}pa a JOIN {prefix}pb b ON a.n = b.n",
                row -> row.string("p")).join().orElseThrow();

        assertEquals(prefix, joined);
        assertEquals(Optional.of(1), db.queryOne("SELECT COUNT(*) AS c FROM " + prefix + "pa", row -> row.intValue("c")).join());
    }

    @Test
    void futuresCompleteOnTheDatabaseWorker() throws Exception {
        final DatabaseImpl db = open("QueryThread");
        // the mapper waits until the callback is registered: a callback added to a finished future runs on the caller
        final CountDownLatch registered = new CountDownLatch(1);
        final CompletableFuture<String> named = db.query("SELECT 1 AS one", row -> {
            awaitQuietly(registered);
            return row.intValue("one");
        }).thenApply(rows -> Thread.currentThread().getName());
        registered.countDown();
        final String thread = named.get(30, TimeUnit.SECONDS);
        assertTrue(thread.startsWith("QueryThread-db-"), thread);
    }

    // --- transactions ---------------------------------------------------------------------------------------------

    @Test
    void aTransactionCommitsWhenTheBodyReturnsAndHandsBackItsValue() {
        final DatabaseImpl db = open("QueryTxCommit");
        db.update("CREATE TABLE {prefix}txc (n INTEGER PRIMARY KEY, name VARCHAR(20))").join();

        final String result = db.transaction(tx -> {
            tx.update("INSERT INTO {prefix}txc VALUES (?, ?)", 1, "one");
            tx.batch("INSERT INTO {prefix}txc VALUES (?, ?)", List.of(new Object[] {2, "two"}, new Object[] {3, "three"}));
            tx.upsert("{prefix}txc", List.of("n"), Map.of("n", 3, "name", "THREE"));
            // the transaction sees its own writes
            final List<String> seen = tx.query("SELECT name FROM {prefix}txc ORDER BY n", row -> row.string("name"));
            assertEquals(Optional.of("two"), tx.queryOne("SELECT name FROM {prefix}txc WHERE n = ?", row -> row.string("name"), 2));
            return String.join(",", seen);
        }).join();

        assertEquals("one,two,THREE", result);
        assertEquals(3, db.queryOne("SELECT COUNT(*) AS c FROM {prefix}txc", row -> row.intValue("c")).join().orElseThrow());
    }

    @Test
    void aTransactionRollsBackOnAnyExceptionAndKeepsNothing() {
        final DatabaseImpl db = open("QueryTxRollback");
        db.update("CREATE TABLE {prefix}txr (n INTEGER PRIMARY KEY)").join();

        final IllegalStateException unchecked = new IllegalStateException("boom");
        final Throwable first = failureOf(db.transaction(tx -> {
            tx.update("INSERT INTO {prefix}txr VALUES (1)");
            throw unchecked;
        }));
        final IOException checked = new IOException("disk");
        final Throwable second = failureOf(db.transaction(tx -> {
            tx.update("INSERT INTO {prefix}txr VALUES (2)");
            throw checked;
        }));
        final Throwable third = failureOf(db.transaction(tx -> {
            tx.update("INSERT INTO {prefix}txr VALUES (3)");
            return tx.update("INSERT INTO {prefix}txr VALUES (3)"); // primary key violation
        }));

        assertSame(unchecked, first);
        assertSame(checked, second);
        assertInstanceOf(DatabaseException.class, third);
        assertEquals(0, db.queryOne("SELECT COUNT(*) AS c FROM {prefix}txr", row -> row.intValue("c")).join().orElseThrow());
        // the connection is usable and in auto-commit again
        db.update("INSERT INTO {prefix}txr VALUES (4)").join();
        assertEquals(1, db.queryOne("SELECT COUNT(*) AS c FROM {prefix}txr", row -> row.intValue("c")).join().orElseThrow());
    }

    @Test
    void aTransactionObjectIsDeadOnceTheBodyIsOver() {
        final DatabaseImpl db = open("QueryTxDead");
        db.update("CREATE TABLE {prefix}txd (n INTEGER PRIMARY KEY)").join();
        final AtomicReference<Transaction> kept = new AtomicReference<>();
        db.transaction(tx -> {
            kept.set(tx);
            return null;
        }).join();

        assertThrows(IllegalStateException.class, () -> kept.get().update("INSERT INTO {prefix}txd VALUES (1)"));
    }

    // --- batch and upsert -------------------------------------------------------------------------------------------

    @Test
    void aBatchOfTenThousandRowsIsWrittenInOneGo() {
        final DatabaseImpl db = open("QueryBatch");
        db.update("CREATE TABLE {prefix}big (n INTEGER PRIMARY KEY, id CHAR(36), name VARCHAR(40))").join();
        final List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            rows.add(new Object[] {i, UUID.nameUUIDFromBytes(new byte[] {(byte) i, (byte) (i >> 8)}), "row " + i});
        }

        final int[] counts = assertTimeoutPreemptively(Duration.ofSeconds(60),
                () -> db.batch("INSERT INTO {prefix}big VALUES (?, ?, ?)", rows).join());

        assertEquals(10_000, counts.length);
        assertEquals(10_000, db.queryOne("SELECT COUNT(*) AS c FROM {prefix}big", row -> row.intValue("c")).join().orElseThrow());
        assertEquals("row 9999", db.queryOne("SELECT name FROM {prefix}big WHERE n = ?", row -> row.string("name"), 9999)
                .join().orElseThrow());
        assertEquals(0, db.batch("INSERT INTO {prefix}big VALUES (?, ?, ?)", List.of()).join().length);
    }

    @Test
    void aFailingBatchWritesNothing() {
        final DatabaseImpl db = open("QueryBatchFail");
        db.update("CREATE TABLE {prefix}bf (n INTEGER PRIMARY KEY)").join();

        final Throwable error = failureOf(db.batch("INSERT INTO {prefix}bf VALUES (?)",
                List.of(new Object[] {1}, new Object[] {2}, new Object[] {1})));

        assertInstanceOf(DatabaseException.class, error);
        assertEquals(0, db.queryOne("SELECT COUNT(*) AS c FROM {prefix}bf", row -> row.intValue("c")).join().orElseThrow());
    }

    @Test
    void upsertInsertsThenUpdatesAndKeyOnlyLeavesTheRow() {
        final DatabaseImpl db = open("QueryUpsert");
        db.update("CREATE TABLE {prefix}bal (player CHAR(36) PRIMARY KEY, money BIGINT, note VARCHAR(50))").join();
        final UUID player = UUID.randomUUID();
        final Map<String, Object> first = new LinkedHashMap<>();
        first.put("player", player);
        first.put("money", 100L);
        first.put("note", "first");

        db.upsert("{prefix}bal", List.of("player"), first).join();
        assertEquals(Optional.of(100L), db.queryOne("SELECT money FROM {prefix}bal", row -> row.longValue("money")).join());

        db.upsert("{prefix}bal", List.of("player"), Map.of("player", player, "money", 250L, "note", "second")).join();
        assertEquals(Optional.of("second"), db.queryOne("SELECT note FROM {prefix}bal", row -> row.string("note")).join());
        assertEquals(Optional.of(250L), db.queryOne("SELECT money FROM {prefix}bal", row -> row.longValue("money")).join());
        assertEquals(1, db.queryOne("SELECT COUNT(*) AS c FROM {prefix}bal", row -> row.intValue("c")).join().orElseThrow());

        db.upsert("{prefix}bal", List.of("player"), Map.of("player", player)).join();
        assertEquals(Optional.of(250L), db.queryOne("SELECT money FROM {prefix}bal", row -> row.longValue("money")).join());

        db.upsert("{prefix}bal", List.of("player"), Map.of("player", UUID.randomUUID(), "money", 1L)).join();
        assertEquals(2, db.queryOne("SELECT COUNT(*) AS c FROM {prefix}bal", row -> row.intValue("c")).join().orElseThrow());
    }

    @Test
    void upsertRefusesNamesThatAreNotPlainIdentifiers() {
        final DatabaseImpl db = open("QueryUpsertNames");
        assertThrows(IllegalArgumentException.class, () -> db.upsert("t; DROP TABLE x", List.of("a"), Map.of("a", 1)));
        assertThrows(IllegalArgumentException.class, () -> db.upsert("t", List.of("a"), Map.of("a", 1, "b`c", 2)));
        assertThrows(IllegalArgumentException.class, () -> db.upsert("t", List.of(), Map.of("a", 1)));
        assertThrows(IllegalArgumentException.class, () -> db.upsert("t", List.of("missing"), Map.of("a", 1)));
    }

    // --- errors and logs ------------------------------------------------------------------------------------------

    @Test
    void parametersNeverReachTheLogAndEachFailureIsLoggedOnce() {
        final DatabaseImpl db = open("QueryLog");
        final Capture log = capture;
        db.update("CREATE TABLE {prefix}secret (name VARCHAR(60) PRIMARY KEY, other VARCHAR(60))").join();
        final String secret = "s3cr3t-player-data-9f2c";
        db.update("INSERT INTO {prefix}secret VALUES (?, ?)", secret, "x").join();
        log.records.clear();

        final Throwable duplicate = failureOf(db.update("INSERT INTO {prefix}secret VALUES (?, ?)", secret, "y"));
        final Throwable syntax = failureOf(db.update("SELEC {prefix}secret WHERE name = ?", secret));
        final Throwable mapper = failureOf(db.query("SELECT * FROM {prefix}secret WHERE name = ?",
                row -> row.string("no_such_column"), secret));
        final Throwable inTransaction = failureOf(db.transaction(tx -> {
            tx.update("INSERT INTO {prefix}secret VALUES (?, ?)", "another-" + secret, "z");
            return tx.update("INSERT INTO {prefix}secret VALUES (?, ?)", secret, "dup");
        }));

        assertInstanceOf(DatabaseException.class, duplicate);
        assertInstanceOf(DatabaseException.class, syntax);
        assertInstanceOf(IllegalArgumentException.class, mapper);
        assertInstanceOf(DatabaseException.class, inTransaction);
        assertEquals(4, log.severe().size(), log.everything());
        final String everything = log.everything();
        assertFalse(everything.contains(secret), everything);
        assertFalse(((DatabaseException) duplicate).getMessage().contains(secret), duplicate.getMessage());
        assertTrue(everything.contains(settings().tablePrefix() + "secret"), "the SQL is logged: " + everything);
        assertTrue(((DatabaseException) duplicate).sql().contains("INSERT INTO " + settings().tablePrefix() + "secret"));
    }

    @Test
    void aClosedDatabaseFailsTheFutureAtOnce() {
        final DatabaseImpl db = open("QueryClosed");
        db.close();

        final Throwable error = failureOf(db.update("SELECT 1"));
        assertInstanceOf(IllegalStateException.class, error);
        assertTrue(error.getMessage().contains("closed"), error.getMessage());
    }

    // --- concurrency ----------------------------------------------------------------------------------------------

    @Test
    void eightThreadsTimesAHundredUpdatesAreAllApplied() throws Exception {
        final DatabaseImpl db = open("QueryConcurrent");
        db.update("CREATE TABLE {prefix}counter (n INTEGER PRIMARY KEY, hits BIGINT)").join();
        db.update("INSERT INTO {prefix}counter VALUES (0, 0)").join();
        db.update("CREATE TABLE {prefix}hitlog (id CHAR(36) PRIMARY KEY, thread_ INTEGER)").join();
        final ExecutorService callers = Executors.newFixedThreadPool(8);
        try {
            final List<CompletableFuture<?>> all = new CopyOnWriteArrayList<>();
            final List<java.util.concurrent.Future<?>> submitted = new ArrayList<>();
            for (int thread = 0; thread < 8; thread++) {
                final int number = thread;
                submitted.add(callers.submit(() -> {
                    for (int i = 0; i < 100; i++) {
                        all.add(db.update("UPDATE {prefix}counter SET hits = hits + 1 WHERE n = ?", 0));
                        all.add(db.update("INSERT INTO {prefix}hitlog VALUES (?, ?)", UUID.randomUUID(), number));
                    }
                }));
            }
            for (final java.util.concurrent.Future<?> future : submitted) {
                future.get(60, TimeUnit.SECONDS);
            }
            CompletableFuture.allOf(all.toArray(new CompletableFuture<?>[0])).get(60, TimeUnit.SECONDS);
        } finally {
            callers.shutdownNow();
        }

        assertEquals(800L, db.queryOne("SELECT hits FROM {prefix}counter", row -> row.longValue("hits")).join().orElseThrow());
        assertEquals(800, db.queryOne("SELECT COUNT(*) AS c FROM {prefix}hitlog", row -> row.intValue("c")).join().orElseThrow());
    }

    // --- waiting on a server thread ---------------------------------------------------------------------------------

    @Test
    void joinAndGetOnAServerThreadWarnWithAStackUnlessWaitingIsAllowed() throws Exception {
        final Thread serverThread = Thread.currentThread(); // this test plays the server thread
        final AtomicBoolean allowed = new AtomicBoolean();
        final Capture warnings = new Capture();
        final Logger logger = Logger.getLogger("DatabaseQueryContract.guard");
        logger.setUseParentHandlers(false);
        logger.addHandler(warnings);
        try {
            final ServerThreadGuard guard = new ServerThreadGuard(() -> Thread.currentThread() == serverThread,
                    allowed::get, logger);
            final DatabaseImpl db = open("QueryGuard", guard);

            db.update("CREATE TABLE {prefix}guard (n INTEGER PRIMARY KEY)").join();
            assertEquals(1, warnings.records.size(), "join()");
            final LogRecord warning = warnings.records.get(0);
            assertEquals(Level.WARNING, warning.getLevel());
            assertTrue(warning.getMessage().contains("join/get"), warning.getMessage());
            assertTrue(java.util.Arrays.stream(warning.getThrown().getStackTrace())
                    .anyMatch(frame -> frame.getClassName().equals(DatabaseQueryContract.class.getName())),
                    "the stack must reach the code that waits");

            db.update("INSERT INTO {prefix}guard VALUES (1)").get();
            db.update("INSERT INTO {prefix}guard VALUES (2)").get(30, TimeUnit.SECONDS);
            db.query("SELECT n FROM {prefix}guard", row -> row.intValue("n")).thenApply(List::size).join();
            assertEquals(4, warnings.records.size(), "get(), get(timeout) and a derived future also warn");

            allowed.set(true); // onEnable / onDisable
            db.update("INSERT INTO {prefix}guard VALUES (3)").join();
            assertEquals(4, warnings.records.size());

            allowed.set(false);
            final ExecutorService other = Executors.newSingleThreadExecutor();
            try {
                other.submit(() -> db.update("INSERT INTO {prefix}guard VALUES (4)").join()).get(30, TimeUnit.SECONDS);
            } finally {
                other.shutdownNow();
            }
            assertEquals(4, warnings.records.size(), "only a server thread is warned");
        } finally {
            logger.removeHandler(warnings);
        }
    }

    // --- waiting on the database from its own worker -----------------------------------------------------------------

    private interface Wait {
        void on(CompletableFuture<?> future) throws Exception;
    }

    @Test
    void joinAndGetOnTheDatabasesOwnWorkerFailAtOnceInsteadOfDeadlocking() {
        final DatabaseImpl db = open("QueryOwnWorker");
        final Map<String, Wait> waits = new LinkedHashMap<>();
        waits.put("join", CompletableFuture::join);
        waits.put("get", CompletableFuture::get);
        waits.put("get(timeout)", future -> future.get(30, TimeUnit.SECONDS));

        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            for (final Map.Entry<String, Wait> wait : waits.entrySet()) {
                // a callback of a database future runs on the worker
                // (the mapper waits until the callback is registered, so the callback really runs on the worker)
                final CountDownLatch registered = new CountDownLatch(1);
                final CompletableFuture<Object> inCallback = db.query("SELECT 1", row -> {
                    awaitQuietly(registered);
                    return 1;
                }).thenApply(rows -> {
                    waitFor(wait.getValue(), db.query("SELECT 2", row -> 2));
                    return "waited";
                });
                registered.countDown();
                assertDeadlockRefused(wait.getKey() + " in a callback", failureOf(inCallback));

                // so does a transaction body
                final CompletableFuture<Object> inBody = db.transaction(tx -> {
                    waitFor(wait.getValue(), db.query("SELECT 2", row -> 2));
                    return "waited";
                });
                assertDeadlockRefused(wait.getKey() + " in a transaction", failureOf(inBody));
            }
        });
        // the refusal did not hurt the worker
        assertEquals(List.of(1), db.query("SELECT 1 AS n", row -> row.intValue("n")).join());
    }

    private static void awaitQuietly(final CountDownLatch latch) {
        try {
            assertTrue(latch.await(30, TimeUnit.SECONDS));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void waitFor(final Wait wait, final CompletableFuture<?> future) {
        try {
            wait.on(future);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalStateException("unexpected " + e, e);
        }
    }

    private static void assertDeadlockRefused(final String what, final Throwable error) {
        Throwable cause = error;
        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertInstanceOf(IllegalStateException.class, cause, what);
        assertEquals("waiting on the database from its own worker would deadlock; chain with thenCompose/thenAccept",
                cause.getMessage(), what);
    }
}
