package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.dialog.file.DialogInput;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The limits one showing of a dialog really used: number ranges and text lengths, taken from the code that registered
 * {@link DialogBuilder#inputRange} / {@link DialogBuilder#maxLength} where there is some, else from the file. The view
 * keeps them from the moment the dialog is rendered, so the answer is checked against the same values the player saw
 * ({@code <min>} and {@code <max>} of an error line too). Immutable.
 *
 * @param bounds  number and number-field inputs by key
 * @param lengths text and number-field inputs by key
 * @param decimals decimal number-field inputs by key, only those code set (the file's value is read from the input)
 */
record InputLimits(Map<String, Bounds> bounds, Map<String, Integer> lengths, Map<String, Integer> decimals) {

    InputLimits(final Map<String, Bounds> bounds, final Map<String, Integer> lengths) {
        this(bounds, lengths, Map.of());
    }


    /** @return no limits of its own: what the file says, for a view that was never rendered (a registry dialog, a test) */
    static InputLimits file() {
        return new InputLimits(Map.of(), Map.of(), Map.of());
    }

    /**
     * Smallest and largest value of a number input.
     *
     * @param min  smallest value; {@code -Infinity} for a {@code number-field} without {@code min}
     * @param max  largest value; {@code Infinity} for a {@code number-field} without {@code max}
     * @param step distance between the values of a slider
     */
    record Bounds(double min, double max, OptionalDouble step) {
    }

    /** @return the effective bounds of a number or number-field input */
    Bounds bounds(final String key, final DialogInput input) {
        final Bounds found = bounds.get(key);
        return found != null ? found : fromFile(input);
    }

    /** @return the effective {@code decimals} of a number-field input, {@code -1} if there is no limit */
    int decimals(final String key, final DialogInput input) {
        final Integer found = decimals.get(key);
        if (found != null) {
            return found;
        }
        return input instanceof DialogInput.NumberFieldInput field ? field.decimals().orElse(-1) : -1;
    }

    /** @return the effective {@code max-length} of a text or number-field input */
    int maxLength(final String key, final DialogInput input) {
        final Integer found = lengths.get(key);
        if (found != null) {
            return found;
        }
        return switch (input) {
            case DialogInput.TextInput text -> text.maxLength().orElse(DialogParser.DEFAULT_TEXT_LENGTH);
            case DialogInput.NumberFieldInput field -> field.maxLength().orElse(DialogParser.DEFAULT_NUMBER_LENGTH);
            default -> Integer.MAX_VALUE;
        };
    }

    static Bounds fromFile(final DialogInput input) {
        return switch (input) {
            case DialogInput.NumberInput number -> new Bounds(number.min(), number.max(),
                    number.step().map(OptionalDouble::of).orElse(OptionalDouble.empty()));
            case DialogInput.NumberFieldInput field -> new Bounds(field.min().orElse(Double.NEGATIVE_INFINITY),
                    field.max().orElse(Double.POSITIVE_INFINITY), OptionalDouble.empty());
            default -> new Bounds(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, OptionalDouble.empty());
        };
    }

    /**
     * Asks the registered functions once for this showing; a function that throws, or answers something the input
     * cannot use, is reported with {@code warn} and the value of the file stays.
     *
     * @param ranges  {@link DialogBuilder#inputRange} by key
     * @param lengths {@link DialogBuilder#maxLength} by key
     */
    static <C> InputLimits resolve(final String dialog, final Map<String, DialogInput> inputs,
                                   final Map<String, Function<DialogView<C>, Range>> ranges,
                                   final Map<String, Function<DialogView<C>, Integer>> lengths,
                                   final DialogView<C> view, final Consumer<String> warn) {
        return resolve(dialog, inputs, ranges, lengths, Map.of(), view, warn);
    }

    /** @param decimalLimits {@link DialogBuilder#inputDecimals} by key */
    static <C> InputLimits resolve(final String dialog, final Map<String, DialogInput> inputs,
                                   final Map<String, Function<DialogView<C>, Range>> ranges,
                                   final Map<String, Function<DialogView<C>, Integer>> lengths,
                                   final Map<String, Function<DialogView<C>, Integer>> decimalLimits,
                                   final DialogView<C> view, final Consumer<String> warn) {
        final var decimals = new LinkedHashMap<String, Integer>();
        final var bounds = new LinkedHashMap<String, Bounds>();
        final var maxLengths = new LinkedHashMap<String, Integer>();
        inputs.forEach((key, input) -> {
            final boolean number = input instanceof DialogInput.NumberInput
                    || input instanceof DialogInput.NumberFieldInput;
            final boolean text = input instanceof DialogInput.TextInput
                    || input instanceof DialogInput.NumberFieldInput;
            final var range = number ? ranges.get(key) : null;
            if (range != null) {
                final Range value = call(dialog, "inputRange", key, range, view, warn);
                if (value != null) {
                    final Bounds fromFile = fromFile(input);
                    if (input instanceof DialogInput.NumberInput && value.min() >= value.max()) {
                        warn.accept("[dialogs/" + dialog + ".yml] inputRange('" + key + "') gave " + value.min() + " to "
                                + value.max() + ": a slider needs min < max, the file's range is used");
                    } else {
                        bounds.put(key, new Bounds(value.min(), value.max(),
                                value.step().isPresent() ? value.step() : fromFile.step()));
                    }
                }
            }
            final var places = input instanceof DialogInput.NumberFieldInput field && !field.isInteger()
                    ? decimalLimits.get(key) : null;
            if (places != null) {
                final Integer value = call(dialog, "inputDecimals", key, places, view, warn);
                if (value != null && (value < 0 || value > DialogParser.MAX_DECIMALS)) {
                    warn.accept("[dialogs/" + dialog + ".yml] inputDecimals('" + key + "') gave " + value
                            + ": it must be between 0 and " + DialogParser.MAX_DECIMALS + ", the file's value is used");
                } else if (value != null) {
                    decimals.put(key, value);
                }
            }
            final var length = text ? lengths.get(key) : null;
            if (length != null) {
                final Integer value = call(dialog, "maxLength", key, length, view, warn);
                if (value != null && value < 1) {
                    warn.accept("[dialogs/" + dialog + ".yml] maxLength('" + key + "') gave " + value
                            + ": it must be at least 1, the file's value is used");
                } else if (value != null) {
                    maxLengths.put(key, value);
                }
            }
        });
        return new InputLimits(Map.copyOf(bounds), Map.copyOf(maxLengths), Map.copyOf(decimals));
    }

    private static <C, V> @Nullable V call(final String dialog, final String what, final String key,
                                           final Function<DialogView<C>, V> function, final DialogView<C> view,
                                           final Consumer<String> warn) {
        try {
            final V value = function.apply(view);
            if (value == null) {
                warn.accept("[dialogs/" + dialog + ".yml] " + what + "('" + key + "') gave nothing, the file's value is used");
            }
            return value;
        } catch (final RuntimeException e) {
            warn.accept("[dialogs/" + dialog + ".yml] " + what + "('" + key + "') threw " + e + ", the file's value is used");
            return null;
        }
    }
}
