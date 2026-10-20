package com.github.dewarepk.synthetixlib.database;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * One "insert or update" statement for a dialect. Names are pasted into the SQL (quoted with backticks, which SQLite
 * accepts too), so they are checked first; the values travel as parameters.
 *
 * @param sql    the statement, {@code {prefix}} not replaced yet
 * @param values the parameters, converted for binding
 */
record Upsert(String sql, @Nullable Object[] values) {

    private static final Pattern TABLE = Pattern.compile("(\\{prefix})?[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern COLUMN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * @param table      table name, optionally starting with {@code {prefix}}
     * @param keyColumns columns that identify the row (a primary key or unique index must cover them)
     * @param values     column to value, including the key columns
     * @throws IllegalArgumentException for a name that is not a plain identifier, no key columns, a key column without a
     *                                  value, or a value of a type that cannot be bound
     */
    static Upsert of(final Dialect dialect, final String table, final List<String> keyColumns,
                     final Map<String, Object> values) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(keyColumns, "keyColumns");
        Objects.requireNonNull(values, "values");
        if (!TABLE.matcher(table).matches()) {
            throw new IllegalArgumentException("Table \"" + table + "\" is not a plain name (optionally starting with {prefix})");
        }
        if (keyColumns.isEmpty()) {
            throw new IllegalArgumentException("keyColumns is empty: say which columns identify the row");
        }
        final List<String> columns = new ArrayList<>(values.keySet());
        for (final String column : columns) {
            requireColumn(column);
        }
        for (final String key : keyColumns) {
            requireColumn(key);
            if (!values.containsKey(key)) {
                throw new IllegalArgumentException("Key column \"" + key + "\" has no entry in values");
            }
        }
        final List<String> updated = columns.stream().filter(column -> !keyColumns.contains(column)).toList();

        final StringBuilder sql = new StringBuilder("INSERT INTO ").append(quote(table)).append(" (");
        sql.append(String.join(", ", columns.stream().map(Upsert::quote).toList())).append(") VALUES (");
        sql.append(String.join(", ", columns.stream().map(column -> "?").toList())).append(')');
        switch (dialect) {
            case SQLITE -> {
                sql.append(" ON CONFLICT (").append(String.join(", ", keyColumns.stream().map(Upsert::quote).toList()))
                        .append(')');
                if (updated.isEmpty()) {
                    sql.append(" DO NOTHING");
                } else {
                    sql.append(" DO UPDATE SET ")
                            .append(String.join(", ", updated.stream()
                                    .map(column -> quote(column) + " = excluded." + quote(column)).toList()));
                }
            }
            case MYSQL -> {
                sql.append(" ON DUPLICATE KEY UPDATE ");
                // nothing to update: assign a key to itself, which leaves the row as it is
                final List<String> assignments = updated.isEmpty() ? List.of(quote(keyColumns.get(0)) + " = "
                        + quote(keyColumns.get(0)))
                        : updated.stream().map(column -> quote(column) + " = VALUES(" + quote(column) + ")").toList();
                sql.append(String.join(", ", assignments));
            }
        }
        final Object[] given = columns.stream().map(values::get).toArray();
        return new Upsert(sql.toString(), Params.convert(given));
    }

    private static void requireColumn(final String column) {
        if (!COLUMN.matcher(column).matches()) {
            throw new IllegalArgumentException("Column \"" + column + "\" is not a plain name");
        }
    }

    private static String quote(final String name) {
        return '`' + name + '`';
    }
}
