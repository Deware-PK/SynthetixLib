package com.github.dewarepk.synthetixlib.dialog.file;

import java.util.List;
import java.util.Objects;

/**
 * What a button does when it is clicked, in order. Read by the {@code click:} codec of the dialog service, which accepts
 * one text or a list of texts.
 *
 * <pre>{@code
 * click: [close, 'action:sell']
 * }</pre>
 *
 * @param actions the steps; empty if the button only closes the dialog
 */
public record ClickActions(List<ClickAction> actions) {

    public ClickActions {
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
    }

    /** @return actions that do nothing */
    public static ClickActions none() {
        return new ClickActions(List.of());
    }

    /** @return {@code true} if there is nothing to run */
    public boolean isEmpty() {
        return actions.isEmpty();
    }
}
