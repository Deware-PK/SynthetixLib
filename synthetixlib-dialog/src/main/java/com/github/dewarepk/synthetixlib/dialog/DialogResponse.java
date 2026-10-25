package com.github.dewarepk.synthetixlib.dialog;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * What the player filled in at a click, by the input keys of the dialog file. Only answers that keep the limits of the
 * showing (the file's, or those code set with {@link DialogBuilder#inputRange} / {@link DialogBuilder#maxLength}) are
 * returned: text length and lines, number range and step, a number-field's text read as a number, option in the list;
 * a missing, wrong-kind or
 * out-of-limits answer is empty, never an exception. For a typed result use {@link DialogBuilder#submit} instead.
 *
 * <pre>{@code
 * .action("search", click -> {
 *     final String text = click.response().text("query").orElse("");
 *     final int amount = click.response().integer("amount").orElse(1);
 * })
 * }</pre>
 */
public interface DialogResponse {

    /** @return the value of a number (slider) or number-field input */
    OptionalDouble number(String key);

    /** @return the value of a number or number-field input, empty if it is not a whole number or does not fit an int */
    OptionalInt integer(String key);

    /** @return the text of a text input, as typed (never parsed as MiniMessage) */
    Optional<String> text(String key);

    /** @return the state of a bool input */
    Optional<Boolean> bool(String key);

    /** @return the id of the chosen option of an option input */
    Optional<String> option(String key);
}
