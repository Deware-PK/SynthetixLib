package com.github.dewarepk.synthetixlib.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.database.MysqlExtension.Mysql;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/** Runs against the owner's MySQL when the Gradle properties are set, skipped otherwise. */
@ExtendWith(MysqlExtension.class)
class MysqlDatabaseTest {

    @TempDir
    Path dir;

    private DatabaseImpl open(final Mysql mysql, final String plugin) {
        return DatabaseImpl.open(plugin, dir, Logger.getLogger("test"), mysql.settings());
    }

    private static String scalar(final DatabaseImpl db, final String sql) throws SQLException {
        try (Connection connection = db.connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }

    @Test
    void createConnectsAndSelectOneWorks(final Mysql mysql) throws SQLException {
        // the library defaults, no extra JDBC properties: the driver's sslMode=PREFERRED must be enough
        assertTrue(mysql.settings().mysql().properties().isEmpty(), mysql.settings().mysql().properties().toString());
        try (DatabaseImpl db = open(mysql, "MysqlOne")) {
            assertEquals(Dialect.MYSQL, db.dialect());
            assertEquals("1", scalar(db, "SELECT 1"));
        }
    }

    @Test
    void theConnectionSpeaksUtf8mb4(final Mysql mysql) throws SQLException {
        try (DatabaseImpl db = open(mysql, "MysqlCharset")) {
            assertEquals("utf8mb4", scalar(db, "SELECT @@character_set_connection"));
        }
    }

    @Test
    void theWorkerHasAsManyThreadsAsThePool(final Mysql mysql) {
        final DatabaseSettings.Pool pool = new DatabaseSettings.Pool(3, mysql.settings().pool().connectionTimeout(),
                mysql.settings().pool().maxLifetime());
        final DatabaseSettings settings = new DatabaseSettings(Dialect.MYSQL, mysql.prefix(), mysql.settings().sqlite(),
                mysql.settings().mysql(), pool, mysql.settings().shutdownTimeout());
        try (DatabaseImpl db = DatabaseImpl.open("MysqlThreads", dir, Logger.getLogger("test"), settings)) {
            assertEquals(3, SqliteDatabaseTest.threadsOf("MysqlThreads").size());
        }
        assertEquals(0, SqliteDatabaseTest.threadsOf("MysqlThreads").size());
    }

    @Test
    void aWrongPasswordFailsAtOnceWithoutLeakingIt(final Mysql mysql) {
        final DatabaseSettings.Mysql section = mysql.settings().mysql();
        final DatabaseSettings settings = new DatabaseSettings(Dialect.MYSQL, mysql.prefix(), mysql.settings().sqlite(),
                new DatabaseSettings.Mysql(section.host(), section.port(), section.database(), section.username(),
                        "definitely-not-the-password", section.properties()),
                mysql.settings().pool(), mysql.settings().shutdownTimeout());
        final IllegalStateException error = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> DatabaseImpl.open("MysqlWrong", dir, Logger.getLogger("test"), settings));
        assertTrue(error.getMessage().startsWith("Cannot connect to the mysql database at jdbc:mysql://"),
                error.getMessage());
        assertFalse(error.getMessage().contains("definitely-not-the-password"), error.getMessage());
    }

    @Test
    void theTablesOfTheRunAreDroppedAfterwards(final Mysql mysql) throws SQLException {
        try (DatabaseImpl db = open(mysql, "MysqlTables");
             Connection connection = db.connection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + mysql.prefix() + "probe (id INTEGER PRIMARY KEY)");
            assertEquals(java.util.List.of(mysql.prefix() + "probe"), mysql.tables());
        }
        // MysqlExtension drops the table after the class.
    }
}
