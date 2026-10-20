package com.github.dewarepk.synthetixlib.database;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What the settings mean for JDBC: URL, account, pool size and driver properties. Pure data, so it is unit-tested
 * without a connection. Validation of values that end up in SQL or in the URL lives here.
 *
 * @param file the SQLite file, or {@code null} for MySQL
 */
record ConnectionPlan(
        Dialect dialect,
        String url,
        @Nullable String username,
        @Nullable String password,
        int poolSize,
        Duration connectionTimeout,
        Duration maxLifetime,
        Map<String, String> driverProperties,
        String driverJar,
        @Nullable Path file) {

    private static final Pattern PREFIX = Pattern.compile("[A-Za-z0-9_]{0,32}");
    private static final Pattern URL_PART = Pattern.compile("[^\\s/?#&;=@\\\\]+");

    /**
     * @param dataFolder where a relative SQLite file lives
     * @throws IllegalArgumentException for a table prefix, host, database name or port that cannot be used
     */
    static ConnectionPlan of(final DatabaseSettings settings, final Path dataFolder) {
        if (!PREFIX.matcher(settings.tablePrefix()).matches()) {
            throw new IllegalArgumentException("database.table-prefix \"" + settings.tablePrefix()
                    + "\" is not valid: use at most 32 letters, digits or _");
        }
        final DatabaseSettings.Pool pool = settings.pool();
        return switch (settings.type()) {
            case SQLITE -> sqlite(settings, pool, dataFolder);
            case MYSQL -> mysql(settings, pool);
        };
    }

    private static ConnectionPlan sqlite(final DatabaseSettings settings, final DatabaseSettings.Pool pool,
                                         final Path dataFolder) {
        final String name = settings.sqlite().file();
        if (name.isBlank()) {
            throw new IllegalArgumentException("database.sqlite.file is empty");
        }
        final Path file = dataFolder.resolve(name).toAbsolutePath().normalize();
        // sqlite-jdbc applies these on every connection it opens: WAL, wait instead of "database is locked",
        // enforced foreign keys, cheaper fsync that WAL makes safe.
        final Map<String, String> properties = new LinkedHashMap<>();
        properties.put("journal_mode", "WAL");
        properties.put("busy_timeout", "5000");
        properties.put("foreign_keys", "true");
        properties.put("synchronous", "NORMAL");
        // One writer at a time: a single connection removes "database is locked" instead of retrying it.
        return new ConnectionPlan(Dialect.SQLITE, "jdbc:sqlite:" + file, null, null, 1,
                pool.connectionTimeout(), pool.maxLifetime(), Map.copyOf(properties), "org.xerial:sqlite-jdbc", file);
    }

    private static ConnectionPlan mysql(final DatabaseSettings settings, final DatabaseSettings.Pool pool) {
        final DatabaseSettings.Mysql mysql = settings.mysql();
        requireUrlPart("database.mysql.host", mysql.host());
        requireUrlPart("database.mysql.database", mysql.database());
        if (mysql.port() < 1 || mysql.port() > 65535) {
            throw new IllegalArgumentException("database.mysql.port " + mysql.port() + " is not between 1 and 65535");
        }
        final Map<String, String> properties = new LinkedHashMap<>();
        properties.put("characterEncoding", "UTF-8");
        properties.put("connectionCollation", "utf8mb4_unicode_ci");
        properties.put("cachePrepStmts", "true");
        properties.put("useServerPrepStmts", "true");
        properties.put("rewriteBatchedStatements", "true");
        properties.putAll(mysql.properties());
        return new ConnectionPlan(Dialect.MYSQL, "jdbc:mysql://" + mysql.host() + ":" + mysql.port() + "/" + mysql.database(),
                mysql.username(), mysql.password(), pool.size(), pool.connectionTimeout(), pool.maxLifetime(),
                Map.copyOf(properties), "com.mysql:mysql-connector-j", null);
    }

    private static void requireUrlPart(final String key, final String value) {
        if (!URL_PART.matcher(value).matches()) {
            throw new IllegalArgumentException(key + " \"" + value + "\" is empty or has characters that do not "
                    + "belong in a host or schema name");
        }
    }

    @Override
    public String toString() {
        return "ConnectionPlan[" + dialect + ", url=" + url + ", username=" + username + ", poolSize=" + poolSize + "]";
    }
}
