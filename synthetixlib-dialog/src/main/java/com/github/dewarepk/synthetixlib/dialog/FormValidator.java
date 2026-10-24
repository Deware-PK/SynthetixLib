package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.dialog.file.DialogInput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Re-checks the answers of a client against the limits of the dialog file: a modified client can send anything (design 4).
 * Pure, so it is unit-tested. A valid answer comes back in its clean form: text as sent, booleans, numbers as a
 * {@code Double} (a float from the client is turned into the double of its shortest decimal form, so {@code 0.1f}
 * is {@code 0.1}), option ids as sent.
 */
final class FormValidator {

    /** Relative slack for numbers: the client works in floats. */
    private static final double EPSILON = 1e-6;
    /** Slack for "on the step", in steps. */
    private static final double STEP_EPSILON = 1e-4;

    /**
     * @param clean the valid answers by input key
     * @param bad   keys of the inputs that were missing or broke a limit, in file order
     */
    record Result(Map<String, Object> clean, List<String> bad) {
    }

    private FormValidator() {
    }

    static Result validate(final Map<String, DialogInput> inputs, final Map<String, Object> answers) {
        final Map<String, Object> clean = new LinkedHashMap<>();
        final List<String> bad = new ArrayList<>();
        inputs.forEach((key, input) -> {
            final Object value = clean(input, answers.get(key));
            if (value == null) {
                bad.add(key);
            } else {
                clean.put(key, value);
            }
        });
        return new Result(clean, bad);
    }

    /** @return the clean answer, {@code null} if {@code raw} is missing, of the wrong kind or outside the limits of the input */
    static @Nullable Object clean(final DialogInput input, final @Nullable Object raw) {
        if (raw == null) {
            return null;
        }
        return switch (input) {
            case DialogInput.BoolInput ignored -> raw instanceof Boolean ? raw : null;
            case DialogInput.TextInput text -> raw instanceof String s && text(text, s) ? s : null;
            case DialogInput.OptionInput option -> raw instanceof String id && option.options().containsKey(id) ? id : null;
            case DialogInput.NumberInput number -> raw instanceof Number n ? number(number, n) : null;
        };
    }

    private static boolean text(final DialogInput.TextInput input, final String value) {
        if (value.length() > input.maxLength().orElse(DialogParser.DEFAULT_TEXT_LENGTH)) {
            return false;
        }
        if (input.multiline().isEmpty()) {
            return value.indexOf('\n') < 0 && value.indexOf('\r') < 0;
        }
        final var maxLines = input.multiline().get().maxLines();
        return maxLines.isEmpty() || value.lines().count() + (value.endsWith("\n") ? 1 : 0) <= maxLines.get();
    }

    private static @Nullable Double number(final DialogInput.NumberInput input, final Number raw) {
        final double value = exact(raw);
        if (!Double.isFinite(value)) {
            return null;
        }
        final double slack = EPSILON * Math.max(1, Math.max(Math.abs(input.min()), Math.abs(input.max())));
        if (value < input.min() - slack || value > input.max() + slack) {
            return null;
        }
        final double clamped = Math.max(input.min(), Math.min(input.max(), value));
        if (input.step().isPresent() && input.step().get() > 0 && Math.abs(clamped - input.max()) > slack) {
            // the slider can always reach its maximum, even if it is not on the step grid
            final double steps = (clamped - input.min()) / input.step().get();
            if (Math.abs(steps - Math.rint(steps)) > STEP_EPSILON) {
                return null;
            }
        }
        return clamped;
    }

    /** The double a player means: a float is read as its shortest decimal form. */
    static double exact(final Number number) {
        return number instanceof Float f ? Double.parseDouble(Float.toString(f)) : number.doubleValue();
    }
}
