package com.github.dewarepk.synthetixlib.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What the settings turn into, without opening a connection. */
class ConnectionPlanTest {

    @TempDir
    Path dir;

    private static DatabaseSettings withPool(final DatabaseSettings base, final int size) {
        return new DatabaseSettings(base.type(), base.tablePrefix(), base.sqlite(), base.mysql(),
                new DatabaseSettings.Pool(size, base.pool().connectionTimeout(), base.pool().maxLifetime()),
                base.shutdownTimeout());
    }

    @Test
    void sqliteIsAFileInThePluginFolderWithOneConnection() {
        final ConnectionPlan plan = ConnectionPlan.of(withPool(DatabaseSettings.sqlite("data/auction.db"), 10), dir);

        final Path file = dir.resolve("data/auction.db").toAbsolutePath().normalize();
        assertEquals(Dialect.SQLITE, plan.dialect());
        assertEquals("jdbc:sqlite:" + file, plan.url());
        assertEquals(file, plan.file());
        assertEquals(1, plan.poolSize());
        assertNull(plan.username());
        assertEquals("org.xerial:sqlite-jdbc", plan.driverJar());
    }

    @Test
    void sqliteAppliesTheFourPragmasThroughTheDriver() {
        final ConnectionPlan plan = ConnectionPlan.of(DatabaseSettings.sqlite("a.db"), dir);

        assertEquals(Map.of("journal_mode", "WAL", "busy_timeout", "5000", "foreign_keys", "true",
                "synchronous", "NORMAL"), plan.driverProperties());
    }

    @Test
    void mysqlUrlComesFromHostPortAndSchema() {
        final DatabaseSettings settings = DatabaseSettings.mysql("db.example.com", 3307, "shop", "auction", "secret");

        final ConnectionPlan plan = ConnectionPlan.of(settings, dir);

        assertEquals(Dialect.MYSQL, plan.dialect());
        assertEquals("jdbc:mysql://db.example.com:3307/shop", plan.url());
        assertEquals("auction", plan.username());
        assertEquals("secret", plan.password());
        assertEquals(10, plan.poolSize());
        assertEquals(Duration.ofSeconds(10), plan.connectionTimeout());
        assertEquals(Duration.ofMinutes(30), plan.maxLifetime());
        assertNull(plan.file());
        assertEquals("com.mysql:mysql-connector-j", plan.driverJar());
    }

    @Test
    void mysqlHasItsDefaultPropertiesAndTheUsersWinOverThem() {
        final DatabaseSettings base = DatabaseSettings.mysql("localhost", 3306, "db", "u", "p");
        final DatabaseSettings custom = new DatabaseSettings(base.type(), base.tablePrefix(), base.sqlite(),
                new DatabaseSettings.Mysql("localhost", 3306, "db", "u", "p",
                        Map.of("useSSL", "false", "rewriteBatchedStatements", "false")),
                base.pool(), base.shutdownTimeout());

        final Map<String, String> defaults = ConnectionPlan.of(base, dir).driverProperties();
        final Map<String, String> merged = ConnectionPlan.of(custom, dir).driverProperties();

        assertEquals("true", defaults.get("cachePrepStmts"));
        assertEquals("true", defaults.get("useServerPrepStmts"));
        assertEquals("true", defaults.get("rewriteBatchedStatements"));
        assertEquals("UTF-8", defaults.get("characterEncoding"));
        assertEquals("utf8mb4_unicode_ci", defaults.get("connectionCollation"));
        assertFalse(defaults.containsKey("useSSL"), "no SSL setting by default: the driver's sslMode=PREFERRED applies");
        assertFalse(defaults.containsKey("allowPublicKeyRetrieval"));
        assertEquals("false", merged.get("rewriteBatchedStatements"));
        assertEquals("true", merged.get("cachePrepStmts"));
    }

    @Test
    void theTablePrefixIsLimitedToWhatIsSafeInSql() {
        for (final String good : new String[] {"", "ia_", "Auction2_", "x".repeat(32)}) {
            ConnectionPlan.of(DatabaseSettings.sqlite("a.db").withTablePrefix(good), dir);
        }
        for (final String bad : new String[] {"a b", "x;DROP", "a-b", "`x`", "é", "x".repeat(33)}) {
            final IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> ConnectionPlan.of(DatabaseSettings.sqlite("a.db").withTablePrefix(bad), dir), bad);
            assertTrue(error.getMessage().contains("table-prefix"), error.getMessage());
        }
    }

    @Test
    void hostSchemaAndPortThatCannotBePartOfAUrlAreRefused() {
        for (final String host : new String[] {"", "a/b", "a b", "h?x=1", "h#x", "h@x"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> ConnectionPlan.of(DatabaseSettings.mysql(host, 3306, "db", "u", "p"), dir), host);
        }
        for (final String schema : new String[] {"", "a/b", "db?x=1", "db;x"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> ConnectionPlan.of(DatabaseSettings.mysql("localhost", 3306, schema, "u", "p"), dir), schema);
        }
        for (final int port : new int[] {0, -1, 65536}) {
            assertThrows(IllegalArgumentException.class,
                    () -> ConnectionPlan.of(DatabaseSettings.mysql("localhost", port, "db", "u", "p"), dir));
        }
        ConnectionPlan.of(DatabaseSettings.mysql("[::1]", 3306, "my_db-1", "u", "p"), dir);
    }

    @Test
    void anEmptySqliteFileNameIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ConnectionPlan.of(DatabaseSettings.sqlite(" "), dir));
    }

    @Test
    void thePasswordIsNotInAnyToString() {
        final DatabaseSettings settings = DatabaseSettings.mysql("localhost", 3306, "db", "u", "hunter2");

        assertFalse(settings.toString().contains("hunter2"), settings.toString());
        assertFalse(settings.mysql().toString().contains("hunter2"));
        assertFalse(ConnectionPlan.of(settings, dir).toString().contains("hunter2"));
        assertTrue(settings.mysql().toString().contains("db"));
    }
}
