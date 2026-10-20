package com.github.dewarepk.synthetixlib.database;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * MySQL for the tests of a class: {@code @ExtendWith(MysqlExtension.class)}. The class is skipped unless the Gradle
 * properties {@code mysqlUrl}, {@code mysqlUser} and {@code mysqlPassword} are set (the owner's user
 * {@code gradle.properties}; the build hands them to the test JVM). Each test class gets its own random table prefix
 * ({@code t<random>_}) through a {@link Mysql} parameter and every table with that prefix is dropped after the class.
 * Tests must only create tables whose name starts with {@link Mysql#prefix()}.
 */
public final class MysqlExtension implements ExecutionCondition, ParameterResolver, AfterAllCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(MysqlExtension.class);

    /** The database a test class may use. */
    public record Mysql(String prefix, DatabaseSettings settings, String url, String user, String password) {

        @Override
        public String toString() {
            return "Mysql[prefix=" + prefix + ", url=" + url + ", user=" + user + ", password=***]";
        }

        /** @return the names of the tables that exist with this class's prefix */
        public List<String> tables() throws SQLException {
            final List<String> names = new ArrayList<>();
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SHOW TABLES LIKE '" + prefix.replace("_", "\\_") + "%'")) {
                while (rows.next()) {
                    names.add(rows.getString(1));
                }
            }
            return names;
        }

        void dropTables() throws SQLException {
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 Statement statement = connection.createStatement()) {
                for (final String table : tables()) {
                    if (table.startsWith(prefix)) {
                        statement.execute("DROP TABLE IF EXISTS `" + table + "`");
                    }
                }
            }
        }
    }

    private static boolean configured() {
        return System.getProperty("mysqlUrl") != null && System.getProperty("mysqlUser") != null
                && System.getProperty("mysqlPassword") != null;
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(final ExtensionContext context) {
        return configured()
                ? ConditionEvaluationResult.enabled("MySQL properties are set")
                : ConditionEvaluationResult.disabled("mysqlUrl, mysqlUser and mysqlPassword are not set");
    }

    @Override
    public boolean supportsParameter(final ParameterContext parameter, final ExtensionContext context)
            throws ParameterResolutionException {
        return parameter.getParameter().getType() == Mysql.class;
    }

    @Override
    public Object resolveParameter(final ParameterContext parameter, final ExtensionContext context)
            throws ParameterResolutionException {
        return context.getRoot().getStore(NAMESPACE).getOrComputeIfAbsent(
                context.getRequiredTestClass(), type -> create(), Mysql.class);
    }

    @Override
    public void afterAll(final ExtensionContext context) throws Exception {
        final Mysql mysql = context.getRoot().getStore(NAMESPACE).remove(context.getRequiredTestClass(), Mysql.class);
        if (mysql != null) {
            mysql.dropTables();
        }
    }

    private static Mysql create() {
        final String url = System.getProperty("mysqlUrl");
        final String user = System.getProperty("mysqlUser");
        final String password = System.getProperty("mysqlPassword");
        final URI uri = URI.create(url.substring("jdbc:".length()));
        final String schema = uri.getPath().substring(1);
        final int port = uri.getPort() == -1 ? 3306 : uri.getPort();
        final String prefix = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "_";
        // the library defaults as they are: no extra JDBC properties (P2-25: sslMode=PREFERRED must connect on its own)
        final DatabaseSettings settings = DatabaseSettings.mysql(uri.getHost(), port, schema, user, password)
                .withTablePrefix(prefix);
        return new Mysql(prefix, settings, url, user, password);
    }
}
