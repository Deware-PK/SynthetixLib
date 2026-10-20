package com.github.dewarepk.synthetixlib.database;

/**
 * Delivered by the future of a call that reached a {@link Database} after {@link Database#close()} began, or that was
 * still queued when the shutdown timeout ran out. Never thrown at the call site. It is an {@link IllegalStateException},
 * so code that only cares that the call failed can treat it like any other.
 */
public final class DatabaseClosedException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    DatabaseClosedException(final String message) {
        super(message);
    }

    DatabaseClosedException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
