package com.github.dewarepk.synthetixlib.config;

import java.io.InputStream;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/** What a {@link ConfigHandle} needs from its surroundings; a seam so reloads can be tested without a server. */
interface ConfigHost {

    boolean isClosed();

    Logger logger();

    /** Runs {@code task} off the server threads; throws {@code IllegalStateException} if closed. */
    void runAsync(Runnable task);

    /** Runs {@code task} on the global region thread; throws {@code IllegalStateException} if closed. */
    void runGlobal(Runnable task);

    /** @return a stream of a resource packaged in the plugin jar, or {@code null} if there is none; caller closes it */
    default @Nullable InputStream resource(final String path) {
        return null;
    }
}
