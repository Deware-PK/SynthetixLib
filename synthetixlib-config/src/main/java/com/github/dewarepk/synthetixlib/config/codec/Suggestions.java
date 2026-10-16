package com.github.dewarepk.synthetixlib.config.codec;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** "Did you mean" support for keys that name a registry entry. */
final class Suggestions {

    static final int COUNT = 3;

    private Suggestions() {
    }

    /** @return up to {@link #COUNT} of {@code candidates} closest to {@code input} by edit distance, best first */
    static List<String> closest(final String input, final Collection<String> candidates) {
        return candidates.stream()
                .map(candidate -> new Scored(candidate, distance(input, candidate)))
                .sorted(Comparator.comparingInt(Scored::distance).thenComparing(Scored::candidate))
                .limit(COUNT)
                .map(Scored::candidate)
                .toList();
    }

    /** @return {@code unknown <noun> "<input>"}, plus the closest candidates if there are any */
    static String unknown(final String noun, final String input, final Collection<String> candidates) {
        final List<String> closest = closest(input, candidates);
        final String base = "unknown " + noun + " \"" + input + "\"";
        return closest.isEmpty() ? base : base + ", did you mean " + String.join(", ", closest) + "?";
    }

    /** Levenshtein distance. */
    static int distance(final String a, final String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                final int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            final int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private record Scored(String candidate, int distance) {
    }
}
