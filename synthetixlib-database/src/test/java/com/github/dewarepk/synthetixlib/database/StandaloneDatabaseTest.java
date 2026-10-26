package com.github.dewarepk.synthetixlib.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link Database#standalone}: plain JUnit, no server. */
class StandaloneDatabaseTest {

    @TempDir
    Path dir;

    private Database open(final String name) {
        return Database.standalone(name, dir, Logger.getLogger("test"), DatabaseSettings.sqlite("s.db").withTablePrefix("s_"));
    }

    @Test
    void migratesQueriesAndRunsATransaction() {
        try (Database db = open("StandaloneOne")) {
            db.update("CREATE TABLE {prefix}t (id INTEGER PRIMARY KEY, name TEXT)").join();
            db.transaction(tx -> {
                tx.update("INSERT INTO {prefix}t (id, name) VALUES (?, ?)", 1, "a");
                tx.update("INSERT INTO {prefix}t (id, name) VALUES (?, ?)", 2, "b");
                return null;
            }).join();

            final List<String> names = db.query("SELECT name FROM {prefix}t ORDER BY id", row -> row.string("name")).join();

            assertEquals(List.of("a", "b"), names);
            assertEquals(1, SqliteDatabaseTest.threadsOf("StandaloneOne").size());
        }
        assertEquals(0, SqliteDatabaseTest.threadsOf("StandaloneOne").size());
    }

    @Test
    void closeWaitsForAcceptedWrites() {
        final Database db = open("StandaloneClose");
        db.update("CREATE TABLE {prefix}t (id INTEGER PRIMARY KEY)").join();
        for (int i = 0; i < 50; i++) {
            db.update("INSERT INTO {prefix}t (id) VALUES (?)", i);
        }
        db.close();

        try (Database again = open("StandaloneClose2")) {
            assertEquals(50, again.queryOne("SELECT COUNT(*) AS n FROM {prefix}t", row -> row.intValue("n")).join().orElseThrow());
        }
    }

    @Test
    void joinOnTheTestThreadDoesNotWarnOrThrow() {
        try (Database db = open("StandaloneJoin")) {
            assertEquals(1, db.queryOne("SELECT 1 AS n", row -> row.intValue("n")).join().orElseThrow());
        }
    }

    /** An ItemStack cannot even be built without a server, so the check the parameter conversion runs is tested alone. */
    @Test
    void itemStackSupportWithoutAServerSaysSo() {
        final IllegalStateException error = assertThrows(IllegalStateException.class, Params::requireServer);
        assertTrue(error.getMessage().contains("need a running Paper server"), error.getMessage());
    }

    @Test
    void readingAnItemStackWithoutAServerSaysSo() {
        try (Database db = open("StandaloneRead")) {
            final var future = db.queryOne("SELECT x'00' AS item", row -> row.itemStack("item"));
            final var error = assertThrows(java.util.concurrent.CompletionException.class, future::join);
            assertTrue(error.getCause() instanceof IllegalStateException, error.getMessage());
            assertTrue(error.getCause().getMessage().contains("need a running Paper server"), error.getMessage());
        }
    }
}
