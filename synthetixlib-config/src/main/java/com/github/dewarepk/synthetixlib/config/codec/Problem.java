package com.github.dewarepk.synthetixlib.config.codec;

import java.util.Objects;

/**
 * One decode problem or warning at a location in the data tree.
 *
 * @param path    dotted path with list indices, e.g. {@code zones.spawn.radius} or {@code rewards[1].chance};
 *                empty for the root
 * @param message human readable description, e.g. {@code must be >= 1 (was 0)}
 */
public record Problem(String path, String message) {

    public Problem {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
    }

    /** @return {@code path: message}, or just the message for the root */
    @Override
    public String toString() {
        return path.isEmpty() ? message : path + ": " + message;
    }
}
