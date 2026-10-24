package com.github.dewarepk.synthetixlib.dialog;

/**
 * Lets one registry-dialog click of a player through at a time, and not sooner than a minimum interval after the last
 * one that got through. A client can send these clicks at any rate; the guard keeps a flood from running handlers.
 */
final class ClickGuard {

    private boolean busy;
    private boolean any;
    private long last;

    /**
     * @param nowNanos      a monotonic time ({@link System#nanoTime()})
     * @param intervalNanos the least time between two clicks that get through
     * @return {@code true} if the click may run; then {@link #leave()} must follow when its handler is over
     */
    synchronized boolean enter(final long nowNanos, final long intervalNanos) {
        if (busy || (any && nowNanos - last < intervalNanos)) {
            return false;
        }
        busy = true;
        any = true;
        last = nowNanos;
        return true;
    }

    /** The handler of the click that got through is over (including its async part). */
    synchronized void leave() {
        busy = false;
    }
}
