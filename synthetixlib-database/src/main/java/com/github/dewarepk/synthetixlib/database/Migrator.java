package com.github.dewarepk.synthetixlib.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies {@link Migrations} on the database worker. Checks everything first (gaps, checksums, downgrade, a script for
 * this dialect) so a problem stops the run before any version is applied, then runs each missing version in its own
 * transaction together with its history row.
 */
final class Migrator {

    private static final String HISTORY = "{prefix}schema_history";

    private final DatabaseImpl database;
    private final Dialect dialect;
    private final String prefix;

    Migrator(final DatabaseImpl database, final Dialect dialect, final String prefix) {
        this.database = database;
        this.dialect = dialect;
        this.prefix = prefix;
    }

    /** @return how many versions were applied */
    int run(final Migrations migrations) throws Exception {
        final List<Migrations.Step> steps = migrations.steps();
        for (final Migrations.Step step : steps) {
            if (step.code() == null && step.script(dialect) == null) {
                throw new MigrationException("Migration " + step.version() + " '" + step.name() + "' has no script for "
                        + dialect + " (only a file for another database)");
            }
        }
        createHistory();
        final Map<Integer, String> applied = readHistory();

        for (final Map.Entry<Integer, String> entry : applied.entrySet()) {
            final int version = entry.getKey();
            if (version > steps.size()) {
                throw new MigrationException("The database is at migration " + version + " but this plugin only knows "
                        + steps.size() + ": it was used by a newer version. Update the plugin; it will not touch the data");
            }
            final Migrations.Step step = steps.get(version - 1);
            if (!step.checksum(dialect).equals(entry.getValue())) {
                throw new MigrationException("Migration " + version + " '" + step.name() + "' was changed after it was "
                        + "applied (checksum differs). Never edit a shipped migration; add a new version instead");
            }
        }

        int count = 0;
        for (final Migrations.Step step : steps) {
            if (!applied.containsKey(step.version())) {
                apply(step);
                count++;
            }
        }
        return count;
    }

    private void createHistory() {
        final String sql = DatabaseImpl.resolve(prefix, "CREATE TABLE IF NOT EXISTS " + HISTORY
                + " (version INTEGER PRIMARY KEY, name VARCHAR(190) NOT NULL, checksum CHAR(64) NOT NULL, "
                + "applied_at BIGINT NOT NULL)");
        try (Connection connection = database.connection(sql)) {
            execute(connection, sql);
        } catch (final SQLException e) {
            throw DatabaseException.of(sql, e, dialect);
        }
    }

    private Map<Integer, String> readHistory() throws SQLException {
        final String sql = DatabaseImpl.resolve(prefix, "SELECT version, checksum FROM " + HISTORY);
        try (Connection connection = database.connection(sql)) {
            final Map<Integer, String> applied = new HashMap<>();
            new Statements(connection, dialect).query(sql, row -> applied.put(row.intValue("version"),
                    row.string("checksum")), new Object[0]);
            return applied;
        }
    }

    private void apply(final Migrations.Step step) {
        final String label = "Migration " + step.version() + " '" + step.name() + "'";
        try {
            database.inTransaction("migration " + step.version(), connection -> {
                final CodeMigration code = step.code();
                if (code != null) {
                    final TransactionImpl tx = new TransactionImpl(new Statements(connection, dialect), dialect, prefix);
                    try {
                        code.run(tx);
                    } finally {
                        tx.finish();
                    }
                } else {
                    final String script = step.script(dialect);
                    for (final String statement : SqlScript.split(DatabaseImpl.resolve(prefix, script))) {
                        try {
                            execute(connection, statement);
                        } catch (final SQLException e) {
                            throw DatabaseException.of(statement, e, dialect);
                        }
                    }
                }
                final String insert = DatabaseImpl.resolve(prefix, "INSERT INTO " + HISTORY
                        + " (version, name, checksum, applied_at) VALUES (?, ?, ?, ?)");
                new Statements(connection, dialect).update(insert, Params.convert(new Object[] {step.version(),
                        step.name(), step.checksum(dialect), System.currentTimeMillis()}));
                return null;
            });
        } catch (final MigrationException e) {
            throw e;
        } catch (final Exception e) {
            throw new MigrationException(label + " failed and was rolled back (on MySQL, DDL already ran stays): "
                    + e.getMessage(), e);
        }
    }

    private static void execute(final Connection connection, final String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
