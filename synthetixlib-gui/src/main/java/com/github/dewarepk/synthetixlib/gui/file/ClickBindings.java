package com.github.dewarepk.synthetixlib.gui.file;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What an item does when it is clicked: one list that answers any click, or one list per {@link ClickKind}.
 *
 * <pre>{@code
 * click: [page-next]                      # any click
 *
 * click:                                  # per click type; other clicks do nothing
 *   left:  [open:my-listings]
 *   right: [close, 'command:auction sell']
 * }</pre>
 *
 * @param any    run for every click when {@code byKind} is empty
 * @param byKind run for the click types listed; a click type that is not listed does nothing
 */
public record ClickBindings(List<ClickAction> any, Map<ClickKind, List<ClickAction>> byKind) {

    public ClickBindings {
        any = List.copyOf(Objects.requireNonNull(any, "any"));
        final Map<ClickKind, List<ClickAction>> copy = new EnumMap<>(ClickKind.class);
        Objects.requireNonNull(byKind, "byKind").forEach((kind, actions) -> copy.put(kind, List.copyOf(actions)));
        byKind = Collections.unmodifiableMap(copy);
    }

    /** @return bindings that do nothing */
    public static ClickBindings none() {
        return new ClickBindings(List.of(), Map.of());
    }

    /** @return bindings that run {@code actions} for every click */
    public static ClickBindings any(final List<ClickAction> actions) {
        return new ClickBindings(actions, Map.of());
    }

    /** @return {@code true} if no click runs anything */
    public boolean isEmpty() {
        return any.isEmpty() && byKind.values().stream().allMatch(List::isEmpty);
    }

    /**
     * @param kind the click type
     * @return what to run for it, in order; empty if nothing
     */
    public List<ClickAction> actions(final ClickKind kind) {
        return byKind.isEmpty() ? any : byKind.getOrDefault(kind, List.of());
    }

    /** @return every action of every click type, for checks over the whole file */
    public List<ClickAction> all() {
        final List<ClickAction> out = new ArrayList<>(any);
        byKind.values().forEach(out::addAll);
        return out;
    }
}
