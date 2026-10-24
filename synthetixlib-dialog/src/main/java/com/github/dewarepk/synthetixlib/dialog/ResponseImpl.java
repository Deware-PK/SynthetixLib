package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.dialog.file.DialogInput;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/** The answers of one click, checked against the limits of the inputs they belong to. */
record ResponseImpl(Map<String, DialogInput> inputs, Map<String, Object> answers) implements DialogResponse {

    @Override
    public OptionalDouble number(final String key) {
        return get(key, DialogInput.NumberInput.class) instanceof Double d ? OptionalDouble.of(d) : OptionalDouble.empty();
    }

    @Override
    public OptionalInt integer(final String key) {
        if (get(key, DialogInput.NumberInput.class) instanceof Double d && d == Math.rint(d)
                && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
            return OptionalInt.of((int) (double) d);
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

    private @Nullable Object get(final String key, final Class<? extends DialogInput> kind) {
        final DialogInput input = key == null ? null : inputs.get(key);
        return kind.isInstance(input) ? FormValidator.clean(input, answers.get(key)) : null;
    }
}
