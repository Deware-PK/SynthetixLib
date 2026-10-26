package com.github.dewarepk.synthetixlib.database;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The statements of one {@link Database#transaction transaction}: same methods as {@link Database}, but they run now, on
 * the transaction's connection, and return their result instead of a future. Failures throw {@link DatabaseException}.
 *
 * <p>Use it only inside the body and on its thread. Do not call {@code Database} methods and wait for them inside
 * the body: with SQLite the one worker is already busy running you.
 */
public interface Transaction {

    /** Like {@link Database#query}, but returns the rows. */
    <T> List<T> query(String sql, RowMapper<T> mapper, Object... params);

    /** Like {@link Database#queryOne}, but returns the row. */
    <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params);

    /**
     * Like {@link Database#update}.
     *
     * @return the number of rows changed
     */
    int update(String sql, Object... params);

    /**
     * Runs one INSERT that creates exactly one row and returns its generated key (an auto-increment / {@code INTEGER
     * PRIMARY KEY} id), the same on SQLite and MySQL. Not for {@code upsert} or {@code batch}, which give no keys.
     *
     * <pre>{@code
     * long id = tx.insert("INSERT INTO {prefix}lots (seller, price) VALUES (?, ?)", seller, 100L);
     * }</pre>
     *
     * @return the generated key
     * @throws DatabaseException if the statement did not change exactly one row, or no key can be found
     * @since 0.9.1
     */
    long insert(String sql, Object... params);

    /** Like {@link Database#batch}. */
    int[] batch(String sql, List<Object[]> rows);

    /** Like {@link Database#upsert}. */
    int upsert(String table, List<String> keyColumns, Map<String, Object> values);
}
