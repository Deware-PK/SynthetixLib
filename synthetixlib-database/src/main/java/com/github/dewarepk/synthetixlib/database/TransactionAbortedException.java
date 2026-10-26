package com.github.dewarepk.synthetixlib.database;

import org.jspecify.annotations.Nullable;

/**
 * Thrown by a {@link Database#transaction transaction} body to roll back on purpose, for example when another player
 * bid first or the lot has just ended. The transaction rolls back and the future fails with this exception; it is
 * logged at {@code FINE} (one line), never at {@code SEVERE}. Subclass it to carry your own details.
 *
 * <p>For an expected outcome prefer {@link Transaction#rollbackOnly()} and a result type: no exception at all.
 *
 * <pre>{@code
 * db.transaction(tx -> {
 *     if (tx.update("UPDATE {prefix}lots SET state = 'SOLD' WHERE id = ? AND state = 'RUNNING'", id) == 0) {
 *         throw new TransactionAbortedException("lot " + id + " is no longer running");
 *     }
 *     return null;
 * });
 * }</pre>
 *
 * <p>The plain constructor fills no stack trace: this is control flow, so it stays cheap.
 *
 * @since 0.9.1
 */
public class TransactionAbortedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** @param message why the transaction was rolled back */
    public TransactionAbortedException(final String message) {
        this(message, null);
    }

    /**
     * @param message why the transaction was rolled back
     * @param cause   what made it roll back, or {@code null}; with a cause the stack trace is filled
     */
    public TransactionAbortedException(final String message, final @Nullable Throwable cause) {
        super(message, cause, false, cause != null);
    }
}
