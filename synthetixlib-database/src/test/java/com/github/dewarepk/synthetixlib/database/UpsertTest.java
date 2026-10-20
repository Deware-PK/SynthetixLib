package com.github.dewarepk.synthetixlib.database;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The statement text of an upsert per dialect, and the conversion of parameters, without a database. */
class UpsertTest {

    private static Map<String, Object> values() {
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("player", "p");
        values.put("money", 5L);
        values.put("note", "n");
        return values;
    }

    @Test
    void sqliteUsesOnConflictDoUpdate() {
        final Upsert upsert = Upsert.of(Dialect.SQLITE, "{prefix}bal", List.of("player"), values());

        assertEquals("INSERT INTO `{prefix}bal` (`player`, `money`, `note`) VALUES (?, ?, ?) "
                + "ON CONFLICT (`player`) DO UPDATE SET `money` = excluded.`money`, `note` = excluded.`note`", upsert.sql());
        assertArrayEquals(new Object[] {"p", 5L, "n"}, upsert.values());
    }

    @Test
    void mysqlUsesOnDuplicateKeyUpdate() {
        final Upsert upsert = Upsert.of(Dialect.MYSQL, "{prefix}bal", List.of("player"), values());

        assertEquals("INSERT INTO `{prefix}bal` (`player`, `money`, `note`) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE `money` = VALUES(`money`), `note` = VALUES(`note`)", upsert.sql());
    }

    @Test
    void onlyKeyColumnsMeansLeaveAnExistingRowAlone() {
        final Map<String, Object> keyOnly = Map.of("a", 1, "b", 2);

        final String sqlite = Upsert.of(Dialect.SQLITE, "t", List.of("a", "b"), keyOnly).sql();
        final String mysql = Upsert.of(Dialect.MYSQL, "t", List.of("a", "b"), keyOnly).sql();

        assertEquals(true, sqlite.endsWith("ON CONFLICT (`a`, `b`) DO NOTHING"), sqlite);
        assertEquals(true, mysql.endsWith("ON DUPLICATE KEY UPDATE `a` = `a`"), mysql);
    }

    @Test
    void namesThatAreNotPlainIdentifiersAreRefused() {
        for (final String table : new String[] {"", "a b", "a;b", "a`b", "1abc", "{prefix}", "x{prefix}y", "a.b"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> Upsert.of(Dialect.SQLITE, table, List.of("a"), Map.of("a", 1)), table);
        }
        assertThrows(IllegalArgumentException.class,
                () -> Upsert.of(Dialect.SQLITE, "t", List.of("a"), Map.of("a", 1, "b c", 2)));
        assertThrows(IllegalArgumentException.class,
                () -> Upsert.of(Dialect.SQLITE, "t", List.of("a; DROP"), Map.of("a; DROP", 1)));
    }

    @Test
    void parametersAreConvertedToWhatTheDriverTakes() {
        final UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        final byte[] bytes = {1, 2};

        final Object[] converted = Params.convert(new Object[] {"s", 1, 2L, 3.5, true, bytes, id,
                Instant.ofEpochMilli(1234), Duration.ofSeconds(2), Dialect.MYSQL, null});

        assertArrayEquals(new Object[] {"s", 1, 2L, 3.5, true, bytes, "123e4567-e89b-12d3-a456-426614174000", 1234L,
                2000L, "MYSQL", null}, converted);
    }

    @Test
    void anUnsupportedTypeNamesThePositionAndTheTypeButNotTheValue() {
        final IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> Params.convert(new Object[] {1, new StringBuilder("secret-value")}));

        assertEquals(true, error.getMessage().startsWith("Parameter 2 is a java.lang.StringBuilder"), error.getMessage());
        assertEquals(false, error.getMessage().contains("secret-value"), error.getMessage());
    }
}
