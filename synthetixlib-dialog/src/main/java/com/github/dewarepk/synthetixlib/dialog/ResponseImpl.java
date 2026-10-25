package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.dialog.file.DialogInput;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/** The answers of one click, checked against the limits of the inputs they belong to. */
record ResponseImpl(Map<String, DialogInput> inputs, Map<String, Object> answers, InputLimits limits)
        implements DialogResponse {

    /** Answers checked against the limits of the file. */
    ResponseImpl(final Map<String, DialogInput> inputs, final Map<String, Object> answers) {
        this(inputs, answers, InputLimits.file());
    }

    @Override
    public OptionalDouble number(final String key) {
        return numeric(key) instanceof Number n ? OptionalDouble.of(n.doubleValue()) : OptionalDouble.empty();
    }

    @Override
    public OptionalInt integer(final String key) {
        final Object value = numeric(key);
        if (value instanceof Double d && d == Math.rint(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
            return OptionalInt.of((int) (double) d);
        }
        if (value instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
            return OptionalInt.of((int) (long) l);
        }
        if (value instanceof BigDecimal big) {
            try {
                return OptionalInt.of(big.intValueExact());
            } catch (final ArithmeticException e) {
                return OptionalInt.empty();
            }
        }
        return OptionalInt.empty();
    }

    @Override
    public Optional<String> text(final String key) {
        return Optional.ofNullable(get(key, DialogInput.TextInput.class) instanceof String s ? s : null);
    }

    @Override
    public Optional<Boolean> bool(final String key) {
        return Optional.ofNullable(get(key, DialogInput.BoolInput.class) instanceof Boolean b ? b : null);
    }

    @Override
    public Optional<String> option(final String key) {
        return Optional.ofNullable(get(key, DialogInput.OptionInput.class) instanceof String s ? s : null);
    }

    /** The value of a number or number-field input: a Double, a Long or a BigDecimal. */
    private @Nullable Object numeric(final String key) {
        final Object slider = get(key, DialogInput.NumberInput.class);
        return slider != null ? slider : get(key, DialogInput.NumberFieldInput.class);
    }

    private @Nullable Object get(final String key, final Class<? extends DialogInput> kind) {
        final DialogInput input = key == null ? null : inputs.get(key);
        return kind.isInstance(input) ? FormValidator.clean(key, input, answers.get(key), limits) : null;
    }
}
