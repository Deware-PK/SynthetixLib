package com.github.dewarepk.synthetixlib.selftest;

import org.jspecify.annotations.Nullable;

/** What a run needs from the server; {@link ServerHost} is the real one, tests use a fake. */
interface SelfTestHost {

    /** Calls {@code action} once when the server finished loading. */
    void onServerLoad(Runnable action);

    void runGlobal(Runnable action);

    void runGlobalLater(long delayTicks, Runnable action);

    void info(String message);

    void severe(String message, @Nullable Throwable error);

    String minecraftVersion();

    void writeResult(String json);

    void shutdown();
}
