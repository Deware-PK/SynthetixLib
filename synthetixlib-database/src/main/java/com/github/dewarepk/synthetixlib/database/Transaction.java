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

    /** Like {@link Database#batch}. */
    int[] batch(String sql, List<Object[]> rows);

    /** Like {@link Database#upsert}. */
    int upsert(String table, List<String> keyColumns, Map<String, Object> values);
}
