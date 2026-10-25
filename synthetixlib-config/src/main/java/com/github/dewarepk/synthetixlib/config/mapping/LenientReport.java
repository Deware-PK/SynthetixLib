package com.github.dewarepk.synthetixlib.config.mapping;

import com.github.dewarepk.synthetixlib.config.codec.DecodeException;
import com.github.dewarepk.synthetixlib.config.codec.Problem;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Collects what a lenient decode had to replace. Not thread-safe; one per decode call. */
final class LenientReport {

    private final List<Problem> errors = new ArrayList<>();
    private final List<LenientResult.Missing> missing = new ArrayList<>();
    private final Deque<String> keys = new ArrayDeque<>();
    private final boolean fillMaps;
    private final Map<String, Object> supplement;

    /** A report for the plain mode: entries missing from a map are the user's to remove. */
    LenientReport() {
        this(false, Map.of());
    }

    /**
     * @param fillMaps   whether entries missing from a map are reported as missing too (locale files)
     * @param supplement data tree whose values replace the default of a missing map entry where it has one
     */
    LenientReport(final boolean fillMaps, final Map<String, Object> supplement) {
        this.fillMaps = fillMaps;
        this.supplement = supplement;
    }

    boolean fillMaps() {
        return fillMaps;
    }

    /** @return the supplement's value at the entered keys, or {@code null} if it has none */
    @Nullable Object supplementedHere() {
        Object node = supplement;
        for (final String step : keys) {
            node = node instanceof Map<?, ?> map ? map.get(step) : null;
        }
        return node;
    }

    /** Enters a data key; pair with {@link #exit()} in a {@code finally}. */
    void enter(final String key) {
        keys.addLast(key);
    }

    void exit() {
        keys.removeLast();
    }

    /** The entered key is absent from the data; {@code value} is its encoded default (never {@code null}). */
    void missing(final Object value) {
        missing.add(new LenientResult.Missing(List.copyOf(keys), value));
    }

    /** Records every problem of {@code e}; {@code outcome} says what happened, e.g. {@code using 8}. */
    void invalid(final DecodeException e, final String outcome) {
        for (final Problem problem : e.problems()) {
            errors.add(new Problem(problem.path(), problem.message() + " -> " + outcome));
        }
    }

    List<Problem> errors() {
        return List.copyOf(errors);
    }

    List<LenientResult.Missing> missing() {
        return List.copyOf(missing);
    }
}
