package com.github.dewarepk.synthetixlib.dialog.file;

import com.github.dewarepk.synthetixlib.config.annotation.TypeName;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One input of a dialog, keyed by its name in {@code inputs:}; the {@code type:} key picks the record. The key is
 * what a {@code submit} form record calls the component (kebab-case).
 *
 * <pre>{@code
 * inputs:
 *   price:  { type: number, label: 'Price', min: 1, max: 1000000, step: 1, initial: 100 }
 *   note:   { type: text, label: 'Note', max-length: 64, multiline: { max-lines: 3, height: 48 } }
 *   hide:   { type: bool, label: 'Hide my name' }
 *   money:  { type: number-field, label: 'Price', format: decimal, min: 0.01, max: 1000000000, max-length: 18 }
 *   length:
 *     type: option
 *     label: 'Duration'
 *     options:
 *       1h:  { display: '1 hour' }
 *       12h: { display: '12 hours', initial: true }
 * }</pre>
 */
public sealed interface DialogInput {

    /** @return the label shown next to the input (MiniMessage) */
    String label();

    /**
     * A line of text.
     *
     * @param label     MiniMessage label
     * @param width     width in pixels, 1 to 1024
     * @param initial   text the input starts with
     * @param maxLength most characters, at least 1; 32 if absent
     * @param multiline several lines instead of one
     */
    @TypeName("text")
    record TextInput(String label, Optional<Integer> width, Optional<String> initial, Optional<Integer> maxLength,
                     Optional<Multiline> multiline) implements DialogInput {
        public TextInput {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(width, "width");
            Objects.requireNonNull(initial, "initial");
            Objects.requireNonNull(maxLength, "maxLength");
            Objects.requireNonNull(multiline, "multiline");
        }
    }

    /**
     * A check box.
     *
     * @param label   MiniMessage label
     * @param initial whether it starts checked, default false
     */
    @TypeName("bool")
    record BoolInput(String label, Optional<Boolean> initial) implements DialogInput {
        public BoolInput {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(initial, "initial");
        }
    }

    /**
     * A button that cycles through choices; the answer is the id (the key) of the chosen option.
     *
     * @param label   MiniMessage label
     * @param width   width in pixels, 1 to 1024
     * @param options choices in order, by id; at most one has {@code initial: true}
     */
    @TypeName("option")
    record OptionInput(String label, Optional<Integer> width, Map<String, OptionEntry> options)
            implements DialogInput {
        public OptionInput {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(width, "width");
            options = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(options, "options")));
        }
    }

    /**
     * A slider.
     *
     * @param label   MiniMessage label
     * @param width   width in pixels, 1 to 1024
     * @param min     smallest value
     * @param max     largest value, greater than {@code min}
     * @param step    distance between values, greater than 0; smooth if absent
     * @param initial start value, inside {@code min..max}; the middle if absent
     * @param format  client label format such as {@code '%s: %s coins'}
     */
    @TypeName("number")
    record NumberInput(String label, Optional<Integer> width, double min, double max, Optional<Double> step,
                       Optional<Double> initial, Optional<String> format) implements DialogInput {
        public NumberInput {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(width, "width");
            Objects.requireNonNull(step, "step");
            Objects.requireNonNull(initial, "initial");
            Objects.requireNonNull(format, "format");
        }
    }

    /**
     * A typed number: a text input that the server reads as a number, for money and any wide range a slider cannot
     * show. The player may write {@code 1,000} or {@code 1_000}; no exponent, no {@code NaN}.
     *
     * @param label     MiniMessage label
     * @param width     width in pixels, 1 to 1024
     * @param format    {@code integer} (whole numbers, a long) or {@code decimal}; integer if absent
     * @param min       smallest value; none if absent
     * @param max       largest value; none if absent
     * @param initial   start value
     * @param maxLength most characters typed, at least 1; 18 if absent
     * @param decimals  most digits after the dot, 0 to 10, only with {@code format: decimal}; no limit if absent
     */
    @TypeName("number-field")
    record NumberFieldInput(String label, Optional<Integer> width, Optional<NumberFormat> format, Optional<Double> min,
                            Optional<Double> max, Optional<Double> initial, Optional<Integer> maxLength,
                            Optional<Integer> decimals) implements DialogInput {
        public NumberFieldInput {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(width, "width");
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(min, "min");
            Objects.requireNonNull(max, "max");
            Objects.requireNonNull(initial, "initial");
            Objects.requireNonNull(maxLength, "maxLength");
            Objects.requireNonNull(decimals, "decimals");
        }

        /** @return {@code true} unless the file says {@code format: decimal} */
        public boolean isInteger() {
            return format.orElse(NumberFormat.INTEGER) == NumberFormat.INTEGER;
        }
    }

    /** What a {@link NumberFieldInput} accepts, written as {@code format:}. */
    enum NumberFormat {
        /** Whole numbers; the record gets a {@code long} or {@code int}. */
        INTEGER,
        /** Numbers with a {@code .}; the record gets a {@code double} or {@code BigDecimal}. */
        DECIMAL
    }

    /**
     * One choice of an {@link OptionInput}.
     *
     * @param display text of the choice (MiniMessage); the id if absent
     * @param initial whether it is selected at the start
     */
    record OptionEntry(Optional<String> display, Optional<Boolean> initial) {
        public OptionEntry {
            Objects.requireNonNull(display, "display");
            Objects.requireNonNull(initial, "initial");
        }

        /** @return {@code true} if this choice starts selected */
        public boolean isInitial() {
            return initial.orElse(false);
        }
    }

    /**
     * Settings of a multi-line text input.
     *
     * @param maxLines most lines, at least 1
     * @param height   height in pixels, at least 1
     */
    record Multiline(Optional<Integer> maxLines, Optional<Integer> height) {
        public Multiline {
            Objects.requireNonNull(maxLines, "maxLines");
            Objects.requireNonNull(height, "height");
        }
    }
}
