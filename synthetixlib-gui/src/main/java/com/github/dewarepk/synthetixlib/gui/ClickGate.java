package com.github.dewarepk.synthetixlib.gui;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lets one click handler run at a time in a view (design section 4): a click that arrives while another handler of the
 * same view is still running is ignored. Per view, so one viewer's slow action never blocks another viewer.
 */
final class ClickGate {

    private final AtomicBoolean busy = new AtomicBoolean();

    /** @return {@code true} if the caller may run a handler and must call {@link #leave()} afterwards; else ignore the click */
    boolean tryEnter() {
        return busy.compareAndSet(false, true);
    }

    void leave() {
        busy.set(false);
    }
}
