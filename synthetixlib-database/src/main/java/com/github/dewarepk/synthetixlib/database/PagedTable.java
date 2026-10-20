package com.github.dewarepk.synthetixlib.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Reads a table page by page, in primary key order, and writes a page into a table of the same shape. Works on raw JDBC
 * values ({@code getObject}/{@code setObject}), because {@link Copy} does not know the columns. Everything runs on the
 * connection it is given, on the calling thread; a driver failure becomes a {@link DatabaseException} with the real
 * statement (no values in it).
 */
final class PagedTable {

    private static final String KEY_ALIAS = "__sx_k";

    private PagedTable() {
    }

    /**
     * One page.
     *
     * @param columns the data columns, in table order
     * @param rows    one array per row, same order as {@code columns}
     * @param lastKey the primary key of the last row (the cursor for the next page), {@code null} for an empty page
     */
    record Page(List<String> columns, List<Object[]> rows, Object @Nullable [] lastKey) {
    }

    /**
     * @param table the real table name (prefix already applied)
     * @return the key expressions rows are ordered by: the primary key columns in key order, or {@code rowid} for a
     *         SQLite table without a declared primary key
     * @throws DatabaseException        if the table does not exist or cannot be read
     * @throws IllegalArgumentException for a MySQL table without a primary key (there is nothing stable to page by)
     */
    static List<String> keysOf(final Connection connection, final Dialect dialect, final String table) {
        final String probe = "SELECT * FROM " + quote(table) + " WHERE 1 = 0";
        try {
            try (PreparedStatement statement = connection.prepareStatement(probe)) {
                statement.executeQuery().close();
            }
            final String catalog = dialect == Dialect.MYSQL ? connection.getCatalog() : null;
            final Map<Integer, String> bySequence = new TreeMap<>();
            try (ResultSet keys = connection.getMetaData().getPrimaryKeys(catalog, null, table)) {
                while (keys.next()) {
                    bySequence.put(keys.getInt("KEY_SEQ"), keys.getString("COLUMN_NAME"));
                }
            }
            if (!bySequence.isEmpty()) {
                return bySequence.values().stream().map(PagedTable::quote).toList();
            }
        } catch (final SQLException e) {
            throw DatabaseException.of(probe, e, dialect);
        }
        if (dialect == Dialect.SQLITE) {
            return List.of("rowid");
        }
        throw new IllegalArgumentException("Table " + table + " has no primary key, so Copy cannot page through it");
    }

    /** @return the first {@code limit} rows after the key {@code after} ({@code null}: from the start) */
    static Page read(final Connection connection, final Dialect dialect, final String table, final List<String> keys,
                     final Object @Nullable [] after, final int limit) {
        final String order = String.join(", ", keys);
        final StringBuilder text = new StringBuilder("SELECT ");
        for (int i = 0; i < keys.size(); i++) {
            text.append(keys.get(i)).append(" AS ").append(quote(KEY_ALIAS + i)).append(", ");
        }
        // `table`.* and not a bare *: MySQL refuses a bare * after other select items
        text.append(quote(table)).append(".* FROM ").append(quote(table));
        if (after != null) {
            text.append(" WHERE (").append(order).append(") > (").append("?, ".repeat(keys.size() - 1)).append("?)");
        }
        text.append(" ORDER BY ").append(order).append(" LIMIT ").append(limit);
        final String sql = text.toString();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (after != null) {
                for (int i = 0; i < after.length; i++) {
                    statement.setObject(i + 1, after[i]);
                }
            }
            try (ResultSet rows = statement.executeQuery()) {
                final ResultSetMetaData meta = rows.getMetaData();
                final int first = keys.size() + 1;
                final List<String> columns = new ArrayList<>();
                for (int column = first; column <= meta.getColumnCount(); column++) {
                    columns.add(meta.getColumnLabel(column));
                }
                final List<Object[]> data = new ArrayList<>();
                Object[] lastKey = null;
                while (rows.next()) {
                    lastKey = new Object[keys.size()];
                    for (int i = 0; i < lastKey.length; i++) {
                        lastKey[i] = rows.getObject(i + 1);
                    }
                    final Object[] values = new Object[columns.size()];
                    for (int i = 0; i < values.length; i++) {
                        values[i] = rows.getObject(first + i);
                    }
                    data.add(values);
                }
                return new Page(List.copyOf(columns), data, lastKey);
            }
        } catch (final SQLException e) {
            throw DatabaseException.of(sql, e, dialect);
        }
    }

    /** Inserts the rows of {@code page} into {@code table} (plain {@code INSERT}: a row that exists already is an error). */
    static void write(final Connection connection, final Dialect dialect, final String table, final Page page) {
        final String sql = insertSql(table, page.columns());
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (final Object[] row : page.rows()) {
                for (int i = 0; i < row.length; i++) {
                    if (row[i] == null) {
                        statement.setNull(i + 1, Types.NULL);
                    } else {
                        statement.setObject(i + 1, row[i]);
                    }
                }
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (final SQLException e) {
            throw DatabaseException.of(sql, e, dialect);
        }
    }

    static String insertSql(final String table, final List<String> columns) {
        return "INSERT INTO " + quote(table) + " (" + String.join(", ", columns.stream().map(PagedTable::quote).toList())
                + ") VALUES (" + "?, ".repeat(columns.size() - 1) + "?)";
    }

    private static String quote(final String name) {
        return '`' + name.replace("`", "``") + '`';
    }
}
