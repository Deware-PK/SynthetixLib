package com.github.dewarepk.synthetixlib.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

/**
 * The only class that touches HikariCP (ArchUnit enforces it). Loading it throws {@link NoClassDefFoundError} when the
 * plugin's {@code PluginLoader} did not add HikariCP; {@link DatabaseImpl} turns that into a message.
 */
final class HikariConnectionSource implements ConnectionSource {

    private final HikariDataSource source;

    private HikariConnectionSource(final HikariDataSource source) {
        this.source = source;
    }

    /**
     * @param poolName shown in HikariCP's own log lines
     * @throws IllegalStateException if no connection can be made (the first one is made here, so a wrong password or
     *                               a stopped server fails now and not on the first statement)
     */
    static HikariConnectionSource open(final String poolName, final ConnectionPlan plan) {
        final HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setJdbcUrl(plan.url());
        if (plan.username() != null) {
            config.setUsername(plan.username());
        }
        if (plan.password() != null) {
            config.setPassword(plan.password());
        }
        config.setMaximumPoolSize(plan.poolSize());
        config.setConnectionTimeout(plan.connectionTimeout().toMillis());
        config.setMaxLifetime(plan.maxLifetime().toMillis());
        plan.driverProperties().forEach(config::addDataSourceProperty);
        try {
            return new HikariConnectionSource(new HikariDataSource(config));
        } catch (final RuntimeException e) {
            final String reason = rootMessage(e);
            throw new IllegalStateException("Cannot connect to the " + plan.dialect().name().toLowerCase(Locale.ROOT)
                    + " database at " + plan.url() + ": " + reason + hint(reason), e);
        }
    }

    @Override
    public Connection get() throws SQLException {
        return source.getConnection();
    }

    @Override
    public void close() {
        source.close();
    }

    /** MySQL 8+ without SSL cannot log in with the default password plugin unless the driver may fetch the key. */
    private static String hint(final String reason) {
        return reason.contains("Public Key Retrieval")
                ? " (enable SSL, or set allowPublicKeyRetrieval: 'true' under database.mysql.properties)"
                : "";
    }

    private static String rootMessage(final Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
