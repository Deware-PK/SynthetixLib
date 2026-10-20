package com.github.dewarepk.synthetixlib.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The asynchronous lists of one view: asks their source on an async thread, keeps the last answer, and hands a new answer
 * to the viewer's thread, unless the view ended meanwhile. Pure (executors are given), so the order of events is tested
 * without a server. {@link #poll} and the delivery run on the viewer's thread; only the source runs elsewhere.
 */
final class AsyncLoads {

    private final Executor async;
    private final Executor viewer;
    private final BooleanSupplier disposed;
    private final Consumer<String> warn;
    private final Function<String, List<?>> source;
    private final Runnable arrived;
    private final Map<String, List<?>> results = new HashMap<>();
    private final Set<String> pending = new HashSet<>();

    /**
     * @param async    runs the source
     * @param viewer   runs the delivery on the viewer's thread
     * @param disposed {@code true} once the view ended: answers are dropped
     * @param warn     gets a message when a source threw
     * @param source   asks the source of a list, by id
     * @param arrived  called on the viewer's thread after a new answer was stored
     */
    AsyncLoads(final Executor async, final Executor viewer, final BooleanSupplier disposed, final Consumer<String> warn,
               final Function<String, List<?>> source, final Runnable arrived) {
        this.async = async;
        this.viewer = viewer;
        this.disposed = disposed;
        this.warn = warn;
        this.source = source;
        this.arrived = arrived;
    }

    /**
     * @param reload ask the source again (if it is not being asked already); the old answer stays until the new one arrives
     * @return the last answer, {@code null} while the first one is on its way
     */
    @Nullable List<?> poll(final String listId, final boolean reload) {
        final List<?> known = results.get(listId);
        if ((known == null || reload) && !disposed.getAsBoolean() && pending.add(listId)) {
            start(listId);
        }
        return known;
    }

    private void start(final String listId) {
        try {
            async.execute(() -> {
                List<?> answer;
                try {
                    final List<?> got = source.apply(listId);
                    answer = got == null ? List.of() : new ArrayList<>(got);
                } catch (final RuntimeException e) {
                    warn.accept("the source of list '" + listId + "' threw " + e);
                    answer = List.of();
                }
                deliver(listId, answer);
            });
        } catch (final RuntimeException e) {
            pending.remove(listId); // the scheduler is closed: nothing will come
        }
    }

    private void deliver(final String listId, final List<?> answer) {
        try {
            viewer.execute(() -> {
                pending.remove(listId);
                if (disposed.getAsBoolean()) {
                    return; // the view was closed before the answer: drop it
                }
                results.put(listId, answer);
                arrived.run();
            });
        } catch (final RuntimeException e) {
            // the viewer's thread is gone: nothing to show
        }
    }
}
