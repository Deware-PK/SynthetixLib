package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.dialog.file.DialogInput;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Re-checks the answers of a client against the limits of the dialog file: a modified client can send anything (design 4).
 * Pure, so it is unit-tested. A valid answer comes back in its clean form: text as sent, booleans, numbers as a
 * {@code Double} (a float from the client is turned into the double of its shortest decimal form, so {@code 0.1f}
 * is {@code 0.1}), option ids as sent, a {@code number-field} as the {@code Long} or {@code BigDecimal} its text means.
 * The limits that count are those of {@link InputLimits}: the ones code gave for the showing, else the file's.
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

    /** {@code 1234}, {@code 1,234}, {@code 1_234}, each with an optional sign; groups of exactly three digits. */
    private static final Pattern WHOLE = Pattern.compile("[+-]?(?:\\d+|\\d{1,3}(?:[,_]\\d{3})+)");
    /** Like {@link #WHOLE} with {@code .digits} after it. */
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:\\d+|\\d{1,3}(?:[,_]\\d{3})+)(?:\\.\\d+)?");

    private FormValidator() {
    }

    static Result validate(final Map<String, DialogInput> inputs, final Map<String, Object> answers) {
        return validate(inputs, answers, InputLimits.file());
    }

    static Result validate(final Map<String, DialogInput> inputs, final Map<String, Object> answers,
                           final InputLimits limits) {
        final Map<String, Object> clean = new LinkedHashMap<>();
        final List<String> bad = new ArrayList<>();
        inputs.forEach((key, input) -> {
            final Object value = clean(key, input, answers.get(key), limits);
            if (value == null) {
                bad.add(key);
            } else {
                clean.put(key, value);
            }
        });
        return new Result(clean, bad);
    }

    /** @return the clean answer, {@code null} if {@code raw} is missing, of the wrong kind or outside the limits of the input */
    static @Nullable Object clean(final String key, final DialogInput input, final @Nullable Object raw,
                                  final InputLimits limits) {
        if (raw == null) {
            return null;
        }
        return switch (input) {
            case DialogInput.BoolInput ignored -> raw instanceof Boolean ? raw : null;
            case DialogInput.TextInput text -> raw instanceof String s && text(text, s, limits.maxLength(key, text)) ? s
                    : null;
            case DialogInput.OptionInput option -> raw instanceof String id && option.options().containsKey(id) ? id : null;
            case DialogInput.NumberInput number -> raw instanceof Number n ? number(limits.bounds(key, number), n)
                    : null;
            case DialogInput.NumberFieldInput field -> raw instanceof String s
                    ? field(field, s, limits.bounds(key, field), limits.maxLength(key, field)) : null;
        };
    }

    private static boolean text(final DialogInput.TextInput input, final String value, final int limit) {
        if (value.length() > limit) {
            return false;
        }
        if (input.multiline().isEmpty()) {
            return value.indexOf('\n') < 0 && value.indexOf('\r') < 0;
        }
        final var maxLines = input.multiline().get().maxLines();
        return maxLines.isEmpty() || value.lines().count() + (value.endsWith("\n") ? 1 : 0) <= maxLines.get();
    }

    private static @Nullable Double number(final InputLimits.Bounds input, final Number raw) {
        final double value = exact(raw);
        if (!Double.isFinite(value)) {
            return null;
        }
        final double slack = EPSILON * Math.max(1, Math.max(Math.abs(input.min()), Math.abs(input.max())));
        if (value < input.min() - slack || value > input.max() + slack) {
            return null;
        }
        final double clamped = Math.max(input.min(), Math.min(input.max(), value));
        if (input.step().isPresent() && Math.abs(clamped - input.max()) > slack) {
            // the slider can always reach its maximum, even if it is not on the step grid
            final double steps = (clamped - input.min()) / input.step().getAsDouble();
            if (Math.abs(steps - Math.rint(steps)) > STEP_EPSILON) {
                return null;
            }
        }
        return clamped;
    }

    /**
     * The text of a number-field read as a number: {@code Long} for {@code integer}, {@code BigDecimal} for
     * {@code decimal}. Trimmed; {@code 1,000} and {@code 1_000} are fine; an exponent, {@code NaN}, a {@code .} in an
     * integer, a number that does not fit a long, or one outside {@code bounds} gives {@code null}.
     */
    private static @Nullable Object field(final DialogInput.NumberFieldInput input, final String raw,
                                          final InputLimits.Bounds bounds, final int maxLength) {
        if (raw.length() > maxLength) {
            return null;
        }
        final String text = raw.trim();
        if (!(input.isInteger() ? WHOLE : DECIMAL).matcher(text).matches()) {
            return null;
        }
        final String plain = text.replace(",", "").replace("_", "");
        final BigDecimal value;
        try {
            value = new BigDecimal(plain.startsWith("+") ? plain.substring(1) : plain);
        } catch (final NumberFormatException e) {
            return null;
        }
        if (Double.isInfinite(value.doubleValue())
                || Double.isFinite(bounds.min()) && value.compareTo(BigDecimal.valueOf(bounds.min())) < 0
                || Double.isFinite(bounds.max()) && value.compareTo(BigDecimal.valueOf(bounds.max())) > 0) {
            return null;
        }
        if (input.isInteger()) {
            final BigInteger whole = value.toBigInteger();
            return whole.bitLength() > 63 ? null : (Object) whole.longValue();
        }
        return value;
    }

    /** The double a player means: a float is read as its shortest decimal form. */
    static double exact(final Number number) {
        return number instanceof Float f ? Double.parseDouble(Float.toString(f)) : number.doubleValue();
    }
}
