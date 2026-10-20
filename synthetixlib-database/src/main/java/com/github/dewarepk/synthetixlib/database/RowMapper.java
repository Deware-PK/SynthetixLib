package com.github.dewarepk.synthetixlib.database;

/**
 * Turns the current row of a result into a value. Runs on the database worker; do not keep the {@link Row}.
 *
 * @param <T> the mapped type
 */
@FunctionalInterface
public interface RowMapper<T> {

    /**
     * @param row the current row
     * @return the value for this row
     */
    T map(Row row);
}
