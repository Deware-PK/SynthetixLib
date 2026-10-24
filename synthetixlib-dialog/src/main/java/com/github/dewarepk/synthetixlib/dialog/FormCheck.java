package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.config.codec.DecodeException;
import com.github.dewarepk.synthetixlib.config.codec.Problem;
import com.github.dewarepk.synthetixlib.dialog.file.DialogInput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tells whether a {@code submit} record fits the inputs of a dialog file, without reflection: sample answers of the
 * right kind for every input are decoded into the record with the config codecs, the same way a real answer will be,
 * so the checker and the real thing cannot disagree. A record component without an input is "missing"; a number input
 * into a {@code boolean} is "expected a boolean"; an option id that an enum does not know is "unknown value".
 *
 * <p>The scalar codecs accept numbers and booleans for a {@code String}, so for number and bool inputs a second
 * probe puts text that is neither where the answer goes: a component that still accepts it is not numeric / boolean.
 * An input that the record does not read is fine: extra inputs are ignored.
 */
final class FormCheck {

    private static final String POISON = "not a number or a boolean";

    private FormCheck() {
    }

    /**
     * @param codecs the codecs that will read the answers
     * @param name   the name of the submit, for the messages
     * @param form   the record class
     * @param inputs the inputs of the file, by key
     * @return one problem per mismatch with the path {@code inputs.<key>}; empty if the record fits
     */
    static List<Problem> check(final Codecs codecs, final String name, final Class<?> form,
                               final Map<String, DialogInput> inputs) {
        final String where = "submit '" + name + "' (" + form.getSimpleName() + "): ";
        final Set<String> seen = new LinkedHashSet<>();
        final List<Problem> out = new ArrayList<>();
        int rounds = 2;
        for (final DialogInput input : inputs.values()) {
            if (input instanceof DialogInput.OptionInput option) {
                rounds = Math.max(rounds, option.options().size());
            }
        }
        for (int round = 0; round < rounds; round++) {
            for (final Problem problem : probe(codecs, form, sample(inputs, round, null))) {
                if (seen.add(problem.path() + "|" + problem.message())) {
                    out.add(report(where, problem));
                }
            }
        }
        for (final Map.Entry<String, DialogInput> entry : inputs.entrySet()) {
            final String what = switch (entry.getValue()) {
                case DialogInput.NumberInput ignored -> "a number input needs an int, long, float or double component";
                case DialogInput.BoolInput ignored -> "a bool input needs a boolean component";
                default -> null;
            };
            if (what == null) {
                continue;
            }
            // only a component that exists can be of the wrong kind: without the answer it must be "missing"
            final Map<String, Object> without = sample(inputs, 0, null);
            without.remove(entry.getKey());
            final boolean exists = probe(codecs, form, without).stream()
                    .anyMatch(problem -> problem.path().equals(entry.getKey()));
            final boolean refused = probe(codecs, form, sample(inputs, 0, entry.getKey())).stream()
                    .anyMatch(problem -> problem.path().equals(entry.getKey()));
            final boolean alreadyReported = out.stream().anyMatch(problem -> problem.path().equals("inputs."
                    + entry.getKey()));
            if (exists && !refused && !alreadyReported) {
                out.add(new Problem("inputs." + entry.getKey(), where + what + ", it accepts text"));
            }
        }
        return out;
    }

    private static Problem report(final String where, final Problem problem) {
        final String path = problem.path().isEmpty() ? "inputs" : "inputs." + problem.path();
        if (problem.message().equals("missing")) {
            return new Problem(path, where + "the record needs a component '" + problem.path()
                    + "' but the file has no input with this key");
        }
        return new Problem(path, where + problem.message());
    }

    private static List<Problem> probe(final Codecs codecs, final Class<?> form, final Map<String, Object> answers) {
        try {
            codecs.decode(answers, form);
            return List.of();
        } catch (final DecodeException e) {
            return e.problems();
        } catch (final RuntimeException e) {
            return List.of(new Problem("", "the record cannot be read: " + e.getMessage()));
        }
    }

    /** A valid answer for every input; {@code poisoned} (if any) gets text of no kind instead. */
    private static Map<String, Object> sample(final Map<String, DialogInput> inputs, final int round,
                                              final String poisoned) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final Map.Entry<String, DialogInput> entry : inputs.entrySet()) {
            if (entry.getKey().equals(poisoned)) {
                out.put(entry.getKey(), POISON);
                continue;
            }
            out.put(entry.getKey(), switch (entry.getValue()) {
                case DialogInput.TextInput ignored -> "x";
                case DialogInput.BoolInput ignored -> Boolean.TRUE;
                case DialogInput.NumberInput number -> round % 2 == 0 ? number.min() : number.max();
                case DialogInput.OptionInput option -> option.options().keySet().stream()
                        .skip(round % option.options().size()).findFirst().orElse("");
            });
        }
        return out;
    }
}
