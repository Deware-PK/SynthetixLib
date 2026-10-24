package com.github.dewarepk.synthetixlib.dialog.file;

import java.util.Objects;
import java.util.Optional;

/**
 * One button of a dialog. Texts are MiniMessage strings, parsed when the dialog is shown.
 *
 * <pre>{@code
 * yes: { label: '<green>List it', tooltip: '<gray>Puts the lot on sale', width: 150, click: [action:sell] }
 * }</pre>
 *
 * @param label   text on the button
 * @param tooltip text shown when the mouse is over the button
 * @param width   width in pixels, 1 to 1024; the client default if absent
 * @param click   what a click does; absent or empty means the button only closes the dialog
 */
public record DialogButton(String label, Optional<String> tooltip, Optional<Integer> width,
                           Optional<ClickActions> click) {

    public DialogButton {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(tooltip, "tooltip");
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(click, "click");
    }

    /** @return what a click does; nothing if the file gives no {@code click} */
    public ClickActions clicks() {
        return click.orElseGet(ClickActions::none);
    }
}
