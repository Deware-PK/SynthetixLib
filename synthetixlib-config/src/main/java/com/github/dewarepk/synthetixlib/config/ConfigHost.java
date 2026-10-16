package com.github.dewarepk.synthetixlib.config;

import java.util.logging.Logger;

/** What a {@link ConfigHandle} needs from its surroundings; a seam so reloads can be tested without a server. */
interface ConfigHost {

    boolean isClosed();

    Logger logger();

    /** Runs {@code task} off the server threads; throws {@code IllegalStateException} if closed. */
    void runAsync(Runnable task);

    /** Runs {@code task} on the global region thread; throws {@code IllegalStateException} if closed. */
    void runGlobal(Runnable task);
}
