package com.github.dewarepk.synthetixlib.dialog.file;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code buttons:} section after parsing. Which keys it may have depends on the {@link DialogKind}:
 *
 * <pre>{@code
 * confirmation:  yes, no
 * notice:        ok
 * multi-action:  any number of named buttons (file order), exit, columns
 * list:          dialogs: [id, ...], exit, columns, button-width
 * links:         exit, columns, button-width
 * }</pre>
 *
 * @param named       {@code yes} and {@code no}, or {@code ok}, or the buttons of a multi-action dialog, by name in file
 *                    order; empty for list and links
 * @param exit        the exit button (multi-action, list, links)
 * @param columns     number of columns, at least 1
 * @param buttonWidth width of the buttons of a list or links dialog, 1 to 1024
 * @param dialogs     ids of the dialogs a list dialog links to
 */
public record DialogButtons(Map<String, DialogButton> named, Optional<DialogButton> exit, Optional<Integer> columns,
                            Optional<Integer> buttonWidth, List<String> dialogs) {

    public DialogButtons {
        named = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(named, "named")));
        Objects.requireNonNull(exit, "exit");
        Objects.requireNonNull(columns, "columns");
        Objects.requireNonNull(buttonWidth, "buttonWidth");
        dialogs = List.copyOf(Objects.requireNonNull(dialogs, "dialogs"));
    }

    /**
     * The buttons a dialog of this kind has when the file gives none (or an unusable one): the client's own labels,
     * and no click behaviour, so a click only closes the dialog.
     *
     * @param kind the kind of dialog
     * @return the default buttons
     */
    public static DialogButtons defaults(final DialogKind kind) {
        final Map<String, DialogButton> named = new LinkedHashMap<>();
        switch (kind) {
            case NOTICE -> named.put("ok", fallback("gui.ok"));
            case CONFIRMATION -> {
                named.put("yes", fallback("gui.yes"));
                named.put("no", fallback("gui.no"));
            }
            default -> { }
        }
        return new DialogButtons(named, Optional.empty(), Optional.empty(), Optional.empty(), List.of());
    }

    static DialogButton fallback(final String langKey) {
        return new DialogButton("<lang:" + langKey + ">", Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** @return every button by its path in the file ({@code buttons.yes}, {@code buttons.exit}), in file order */
    public Map<String, DialogButton> byPath() {
        final Map<String, DialogButton> out = new LinkedHashMap<>();
        named.forEach((key, button) -> out.put("buttons." + key, button));
        exit.ifPresent(button -> out.put("buttons.exit", button));
        return out;
    }
}
