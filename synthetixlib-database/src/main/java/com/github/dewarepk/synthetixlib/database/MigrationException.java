package com.github.dewarepk.synthetixlib.database;

import org.jspecify.annotations.Nullable;

/**
 * The migrations cannot be loaded or applied: a malformed file name, a duplicate or missing version, a shipped file that
 * was edited after it ran, a database newer than the plugin, or a migration that failed. Thrown by
 * {@link Migrations} while loading and delivered by the future of {@link Database#migrate}. The message never contains
 * statement parameters.
 */
public final class MigrationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    MigrationException(final String message) {
        super(message);
    }

    MigrationException(final String message, final @Nullable Throwable cause) {
        super(message, cause);
    }
}
