package com.github.dewarepk.synthetixlib.database;

import java.sql.SQLException;

/**
 * A statement failed. Thrown by {@link Transaction} methods and delivered by the futures of {@link Database}
 * (wrapped in a {@code CompletionException} by {@code join()}). The message and {@link #sql()} never contain statement
 * parameters; the {@linkplain #getCause() cause} is the driver's own exception, whose text may quote data. Logging this
 * exception with its cause (for example {@code logger.log(level, msg, exception)}) prints that driver text, so do not
 * log it where players can read it; log {@link #getMessage()} instead.
 */
public final class DatabaseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String sql;

    private DatabaseException(final String message, final String sql, final SQLException cause) {
        super(message, cause);
        this.sql = sql;
    }

    private DatabaseException(final String message, final String sql) {
        super(message);
        this.sql = sql;
    }

    /** A failure of the library's own check (not the driver's): the message and the statement, no parameters. */
    static DatabaseException of(final String sql, final String reason) {
        return new DatabaseException(reason + " in: " + sql, sql);
    }

    /**
     * @param sql     the statement as written by the caller (with {@code {prefix}} replaced)
     * @param cause   the driver's exception
     * @param dialect decides whether the driver's text may be repeated: SQLite's never quotes a parameter, MySQL's
     *                does ("Duplicate entry 'Steve' ...", "... syntax ... near 'WHERE name = 'Steve''"), and a
     *                parameter is player data
     */
    static DatabaseException of(final String sql, final SQLException cause, final Dialect dialect) {
        final String state = cause.getSQLState();
        final StringBuilder message = new StringBuilder("SQL statement failed (SQLState ")
                .append(state == null ? "?" : state).append(", code ").append(cause.getErrorCode()).append(')');
        if (dialect == Dialect.SQLITE && cause.getMessage() != null) {
            message.append(": ").append(cause.getMessage());
        }
        message.append(" in: ").append(sql);
        return new DatabaseException(message.toString(), sql, cause);
    }

    /** @return the statement that failed, as the caller wrote it with {@code {prefix}} replaced; no parameters */
    public String sql() {
        return sql;
    }
}
