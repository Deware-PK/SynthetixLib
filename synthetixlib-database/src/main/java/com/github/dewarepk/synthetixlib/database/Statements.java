package com.github.dewarepk.synthetixlib.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Runs statements on one connection, now, on the calling thread. SQL has {@code {prefix}} replaced and parameters
 * converted by {@link Params#convert} already. A driver failure becomes a {@link DatabaseException}; anything a
 * {@link RowMapper} throws passes through unchanged.
 */
final class Statements {

    private final Connection connection;
    private final Dialect dialect;

    Statements(final Connection connection, final Dialect dialect) {
        this.connection = connection;
        this.dialect = dialect;
    }

    <T> List<T> query(final String sql, final RowMapper<T> mapper, final @Nullable Object[] values) {
        return read(sql, mapper, values, 0);
    }

    <T> Optional<T> queryOne(final String sql, final RowMapper<T> mapper, final @Nullable Object[] values) {
        final List<T> rows = read(sql, mapper, values, 1);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }

    int update(final String sql, final @Nullable Object[] values) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Params.bind(statement, values);
            return statement.executeUpdate();
        } catch (final SQLException e) {
            throw DatabaseException.of(sql, e, dialect);
        }
    }

    /** One INSERT that must create exactly one row; returns its generated key (driver key, else the dialect's query). */
    long insert(final String sql, final @Nullable Object[] values) {
        final long key;
        try (PreparedStatement statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            Params.bind(statement, values);
            if (statement.executeUpdate() != 1) {
                throw DatabaseException.of(sql, INSERT_RULE);
            }
            long found = -1;
            boolean hasKey = false;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys != null && keys.next()) {
                    found = keys.getLong(1);
                    hasKey = !keys.wasNull();
                }
            }
            if (!hasKey) {
                final String query = dialect == Dialect.SQLITE ? "SELECT last_insert_rowid()" : "SELECT LAST_INSERT_ID()";
                try (PreparedStatement fallback = connection.prepareStatement(query);
                     ResultSet rows = fallback.executeQuery()) {
                    if (rows.next()) {
                        found = rows.getLong(1);
                        hasKey = !rows.wasNull() && found != 0;
                    }
                }
            }
            if (!hasKey) {
                throw DatabaseException.of(sql, INSERT_RULE);
            }
            key = found;
        } catch (final SQLException e) {
            throw DatabaseException.of(sql, e, dialect);
        }
        return key;
    }

    private static final String INSERT_RULE = "insert must create exactly one row with a generated key";

    int[] batch(final String sql, final List<Object[]> rows) {
        if (rows.isEmpty()) {
            return new int[0];
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (final Object[] values : rows) {
                Params.bind(statement, values);
                statement.addBatch();
            }
            return statement.executeBatch();
        } catch (final SQLException e) {
            throw DatabaseException.of(sql, e, dialect);
        }
    }

    private <T> List<T> read(final String sql, final RowMapper<T> mapper, final @Nullable Object[] values,
                             final int maxRows) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setMaxRows(maxRows);
            Params.bind(statement, values);
            try (ResultSet rows = statement.executeQuery()) {
                final ResultRow row = new ResultRow(rows);
                final List<T> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(mapper.map(row));
                }
                return Collections.unmodifiableList(result);
            }
        } catch (final SQLException e) {
            throw DatabaseException.of(sql, e, dialect);
        }
    }
}
