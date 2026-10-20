package com.github.dewarepk.synthetixlib.database;

import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Notices a server thread that waits for the database ({@code join()} / {@code get()} on a result): that freezes the
 * server for as long as the statement takes. It only warns, with the stack of the waiting code. Waiting is fine while the
 * plugin enables or disables, because there is nothing else to wait for.
 */
final class ServerThreadGuard {

    private static final String ADVICE = "A server thread is waiting for the database (join/get). The server is frozen "
            + "until the statement is done. Use thenAccept/thenApply and go back to the thread you need with "
            + "lib.scheduler(); waiting is only fine in onEnable and onDisable.";

    private final BooleanSupplier onServerThread;
    private final BooleanSupplier waitingAllowed;
    private final Logger logger;

    /**
     * @param onServerThread whether the calling thread is a server (tick) thread
     * @param waitingAllowed whether waiting is fine right now (plugin enabling or disabling)
     */
    ServerThreadGuard(final BooleanSupplier onServerThread, final BooleanSupplier waitingAllowed, final Logger logger) {
        this.onServerThread = onServerThread;
        this.waitingAllowed = waitingAllowed;
        this.logger = logger;
    }

    /** @return a guard that never warns, for code that runs without a server */
    static ServerThreadGuard disabled() {
        return new ServerThreadGuard(() -> false, () -> true, Logger.getAnonymousLogger());
    }

    /** Call right before blocking the current thread. */
    void beforeBlocking() {
        if (onServerThread.getAsBoolean() && !waitingAllowed.getAsBoolean()) {
            logger.log(Level.WARNING, ADVICE, new Throwable("waiting here"));
        }
    }
}
