package com.github.dewarepk.synthetixlib.core.scheduler;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Set of live tasks plus a one-way "closed" switch. Pure logic, no server needed.
 *
 * <p>Close race: {@link #register} adds first and re-checks {@code closed} afterwards, while
 * {@link #close} sets {@code closed} first and snapshots afterwards. Whatever the interleaving, a task is
 * either rejected here, or visible in the snapshot returned by {@link #close}, so it gets cancelled.
 */
final class TaskRegistry<T> {

    private final Set<T> live = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    /** @return {@code false} if the registry is (or just got) closed; the task is then not tracked */
    boolean register(final T task) {
        if (closed) {
            return false;
        }
        live.add(task);
        if (closed) {
            live.remove(task);
            return false;
        }
        return true;
    }

    void unregister(final T task) {
        live.remove(task);
    }

    /** Closes the registry and returns the tasks that were live; later calls return what is left (usually nothing). */
    List<T> close() {
        closed = true;
        return List.copyOf(live);
    }

    boolean isClosed() {
        return closed;
    }

    int size() {
        return live.size();
    }
}
