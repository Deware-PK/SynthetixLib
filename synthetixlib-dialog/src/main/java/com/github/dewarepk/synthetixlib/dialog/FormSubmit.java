package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.config.codec.DecodeException;
import com.github.dewarepk.synthetixlib.config.codec.Problem;
import com.github.dewarepk.synthetixlib.dialog.file.DialogInput;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.Nullable;

/**
 * Turns the answers of a click into the record of a {@code submit}: the limits of the file first ({@link FormValidator}),
 * then the config codecs (kebab-case keys, {@code @Range}, {@code @NotEmpty}, enums, int / long from a whole number).
 * Free of the server, so it is unit-tested.
 */
final class FormSubmit {

    /**
     * An input whose answer was refused.
     *
     * @param key    the input key; empty if the problem belongs to no input
     * @param markup the {@code errors.<key>} line of the file, {@code null} to use the default message
     * @param min    {@code <min>} of the line: the smallest value of a number input, else empty
     * @param max    {@code <max>} of the line
     * @param value  {@code <value>} of the line: what the player sent, as plain text
     * @param extra  more tags of the line, from the {@link FieldError} of a validator
     */
    record Failure(String key, @Nullable String markup, String min, String max, String value, TagResolver extra) {
    }

    /**
     * @param form     the record, {@code null} if the answers were refused
     * @param failures why, empty when {@code form} is set
     */
    record Outcome(@Nullable Object form, List<Failure> failures) {
    }

    private FormSubmit() {
    }

    static Outcome decode(final Codecs codecs, final Map<String, DialogInput> inputs, final Map<String, String> lines,
                          final Class<?> form, final Map<String, Object> answers) {
        return decode(codecs, inputs, lines, form, answers, InputLimits.file());
    }

    /** @param limits what the showing really used (see {@link InputLimits}) */
    static Outcome decode(final Codecs codecs, final Map<String, DialogInput> inputs, final Map<String, String> lines,
                          final Class<?> form, final Map<String, Object> answers, final InputLimits limits) {
        final FormValidator.Result checked = FormValidator.validate(inputs, answers, limits);
        final Map<String, Failure> failures = new LinkedHashMap<>();
        for (final String key : checked.bad()) {
            failures.put(key, failure(key, inputs, lines, answers, limits));
        }
        if (failures.isEmpty()) {
            try {
                return new Outcome(codecs.decode(checked.clean(), form), List.of());
            } catch (final DecodeException e) {
                for (final Problem problem : e.problems()) {
                    final String key = keyOf(problem.path());
                    failures.putIfAbsent(key, failure(key, inputs, lines, answers, limits));
                }
            }
        }
        return new Outcome(null, new ArrayList<>(failures.values()));
    }

    /** {@code price} for {@code price} and {@code price.x} / {@code price[0]}. */
    private static String keyOf(final String path) {
        int end = path.length();
        for (final char c : new char[] {'.', '['}) {
            final int at = path.indexOf(c);
            if (at >= 0) {
                end = Math.min(end, at);
            }
        }
        return path.substring(0, end);
    }

    /**
     * The lines for the errors a validator of the code returned: {@code <min> <max> <value>} are those of the input the
     * error names, so the text can say what the player saw.
     */
    static List<Failure> failures(final List<FieldError> errors, final Map<String, DialogInput> inputs,
                                  final Map<String, Object> answers, final InputLimits limits) {
        final List<Failure> out = new ArrayList<>();
        for (final FieldError error : errors) {
            out.add(failure(error.key(), error.markup(), error.resolver(), inputs, answers, limits));
        }
        return out;
    }

    private static Failure failure(final String key, final Map<String, DialogInput> inputs,
                                   final Map<String, String> lines, final Map<String, Object> answers,
                                   final InputLimits limits) {
        return failure(key, lines.get(key), TagResolver.empty(), inputs, answers, limits);
    }

    private static Failure failure(final String key, final @Nullable String markup, final TagResolver extra,
                                   final Map<String, DialogInput> inputs, final Map<String, Object> answers,
                                   final InputLimits limits) {
        final DialogInput input = inputs.get(key);
        String min = "";
        String max = "";
        if (input instanceof DialogInput.NumberInput || input instanceof DialogInput.NumberFieldInput) {
            final InputLimits.Bounds bounds = limits.bounds(key, input);
            min = Double.isFinite(bounds.min()) ? format(bounds.min()) : "";
            max = Double.isFinite(bounds.max()) ? format(bounds.max()) : "";
        }
        final Object sent = answers.get(key);
        final String value = sent instanceof Float f ? format(FormValidator.exact(f)) : sent == null ? "" : sent.toString();
        return new Failure(key, markup, min, max, value, extra);
    }

    /** 5.0 as {@code 5}, 2.5 as {@code 2.5}, never with an exponent. */
    static String format(final double number) {
        if (!Double.isFinite(number)) {
            return Double.toString(number);
        }
        return number == Math.rint(number) && Math.abs(number) < 1e15 ? Long.toString((long) number)
                : BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
    }

    /**
     * Runs the handler of a submit: now, or on {@code async} for {@code submitAsync}.
     *
     * @param after called once the handler is over (also when it threw), on the thread the handler ran on
     */
    static <T> void run(final BiConsumer<Object, T> handler, final Object form, final T click, final boolean async,
                        final Executor executor, final Consumer<RuntimeException> failed, final Runnable after) {
        if (!async) {
            handler.accept(form, click);
            return;
        }
        executor.execute(() -> {
            try {
                handler.accept(form, click);
            } catch (final RuntimeException e) {
                failed.accept(e);
            } finally {
                after.run();
            }
        });
    }
}
