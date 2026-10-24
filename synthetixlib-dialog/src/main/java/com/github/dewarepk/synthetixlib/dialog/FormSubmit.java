package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.config.codec.DecodeException;
import com.github.dewarepk.synthetixlib.config.codec.Problem;
import com.github.dewarepk.synthetixlib.dialog.file.DialogInput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
     */
    record Failure(String key, @Nullable String markup, String min, String max, String value) {
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
        final FormValidator.Result checked = FormValidator.validate(inputs, answers);
        final Map<String, Failure> failures = new LinkedHashMap<>();
        for (final String key : checked.bad()) {
            failures.put(key, failure(key, inputs, lines, answers));
        }
        if (failures.isEmpty()) {
            try {
                return new Outcome(codecs.decode(checked.clean(), form), List.of());
            } catch (final DecodeException e) {
                for (final Problem problem : e.problems()) {
                    final String key = keyOf(problem.path());
                    failures.putIfAbsent(key, failure(key, inputs, lines, answers));
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

    private static Failure failure(final String key, final Map<String, DialogInput> inputs,
                                   final Map<String, String> lines, final Map<String, Object> answers) {
        final DialogInput input = inputs.get(key);
        String min = "";
        String max = "";
        if (input instanceof DialogInput.NumberInput number) {
            min = format(number.min());
            max = format(number.max());
        }
        final Object sent = answers.get(key);
        final String value = sent instanceof Float f ? format(FormValidator.exact(f)) : sent == null ? "" : sent.toString();
        return new Failure(key, lines.get(key), min, max, value);
    }

    /** 5.0 as {@code 5}, 2.5 as {@code 2.5}. */
    static String format(final double number) {
        return number == Math.rint(number) && Math.abs(number) < 1e15 ? Long.toString((long) number)
                : Double.toString(number);
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
