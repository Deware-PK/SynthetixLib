package com.github.dewarepk.synthetixlib.dialog.file;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A dialog file after parsing: immutable and shared by every view of the dialog.
 *
 * <pre>{@code
 * title: '<gold>Sell <item-name>'
 * type: confirmation
 * body:
 *   - message: '<gray>Starting price'
 * inputs:
 *   price: { type: number, label: 'Price', min: 1, max: 1000000 }
 * buttons:
 *   yes: { label: '<green>List it', click: [action:sell] }
 *   no:  { label: '<red>Cancel' }
 * errors:
 *   price: '<red>Price must be between <min> and <max>'
 * }</pre>
 *
 * @param title              MiniMessage title
 * @param externalTitle      title of the button that opens this dialog from a list or the pause menu
 * @param kind               the kind of dialog
 * @param canCloseWithEscape whether Escape closes it, default true
 * @param pause              pauses a single player game; ignored on servers
 * @param body               text and items above the inputs
 * @param inputs             the inputs by key, in order
 * @param buttons            the buttons of this kind
 * @param errors             MiniMessage line per input key, shown when server side validation of that input fails
 */
public record DialogFile(String title, Optional<String> externalTitle, DialogKind kind, boolean canCloseWithEscape,
                         boolean pause, List<DialogBody> body, Map<String, DialogInput> inputs,
                         DialogButtons buttons, Map<String, String> errors) {

    public DialogFile {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(externalTitle, "externalTitle");
        Objects.requireNonNull(kind, "kind");
        body = List.copyOf(Objects.requireNonNull(body, "body"));
        inputs = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(inputs, "inputs")));
        Objects.requireNonNull(buttons, "buttons");
        errors = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(errors, "errors")));
    }

    /** @return a notice with the default button and no text: what a dialog falls back to when its file is unusable */
    public static DialogFile empty() {
        return new DialogFile("", Optional.empty(), DialogKind.NOTICE, true, false, List.of(), Map.of(),
                DialogButtons.defaults(DialogKind.NOTICE), Map.of());
    }
}
