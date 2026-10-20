package com.github.dewarepk.synthetixlib.gui;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.jspecify.annotations.Nullable;

/**
 * The open view of every viewer and the menus they can go {@code back} to. One per {@link GuiService}, never static
 * (bug #4): it is cleaned when a viewer quits, closes the menu or the service closes.
 *
 * @param <V> a view
 * @param <B> what {@code back} needs to open a menu again
 */
final class ViewRegistry<V, B> {

    /** Most menus a viewer can step back through; the oldest are forgotten. */
    static final int MAX_BACK = 32;

    private final Map<UUID, V> current = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<B>> back = new ConcurrentHashMap<>();

    @Nullable V get(final UUID viewer) {
        return current.get(viewer);
    }

    void put(final UUID viewer, final V view) {
        current.put(viewer, view);
    }

    /** @return the view that was removed, {@code null} if the viewer had none */
    @Nullable V remove(final UUID viewer) {
        return current.remove(viewer);
    }

    /** @return {@code true} if {@code view} was the viewer's current one and is gone now */
    boolean remove(final UUID viewer, final V view) {
        return current.remove(viewer, view);
    }

    void push(final UUID viewer, final B entry) {
        final Deque<B> stack = back.computeIfAbsent(viewer, ignored -> new ConcurrentLinkedDeque<>());
        stack.addFirst(entry);
        while (stack.size() > MAX_BACK) {
            stack.pollLast();
        }
    }

    /** @return the menu to go back to, {@code null} if there is none */
    @Nullable B pop(final UUID viewer) {
        final Deque<B> stack = back.get(viewer);
        return stack == null ? null : stack.pollFirst();
    }

    void clearBack(final UUID viewer) {
        back.remove(viewer);
    }

    int backSize(final UUID viewer) {
        final Deque<B> stack = back.get(viewer);
        return stack == null ? 0 : stack.size();
    }

    int size() {
        return current.size();
    }

    /** Forgets everything and returns the views that were open. */
    List<V> clearAll() {
        final List<V> views = new ArrayList<>(current.values());
        current.clear();
        back.clear();
        return views;
    }
}
