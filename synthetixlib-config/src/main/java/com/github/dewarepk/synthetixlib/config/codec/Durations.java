package com.github.dewarepk.synthetixlib.config.codec;

import java.time.Duration;
import java.util.Locale;

/**
 * Text form of {@link Duration}: groups of {@code <number><unit>} with units {@code d h m s ms us ns},
 * e.g. {@code 250ms}, {@code 30s}, {@code 1h30m}, {@code 2d}. Only non-negative durations are supported.
 *
 * @apiNote Internal: public only so the other SynthetixLib modules (command arguments, cooldown messages) share one
 *          syntax with config files. Not part of the supported API.
 */
@org.jetbrains.annotations.ApiStatus.Internal
public final class Durations {

    private static final long SECONDS_PER_MINUTE = 60;
    private static final long SECONDS_PER_HOUR = 3_600;
    private static final long SECONDS_PER_DAY = 86_400;
    private static final long NANOS_PER_MILLI = 1_000_000;
    private static final long NANOS_PER_MICRO = 1_000;
    private static final int MAX_DIGITS = 18;
    private static final String HINT = " (examples: 250ms, 30s, 5m, 1h30m, 2d)";

    private Durations() {
    }

    /**
     * @param text e.g. {@code 1h30m}
     * @return the duration
     * @throws IllegalArgumentException with a user readable message
     */
    public static Duration parse(final String text) {
        final String s = text.strip().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            throw new IllegalArgumentException("duration is empty" + HINT);
        }
        long seconds = 0;
        long nanos = 0;
        int i = 0;
        try {
            while (i < s.length()) {
                if (Character.isWhitespace(s.charAt(i))) {
                    i++;
                    continue;
                }
                final int numberStart = i;
                while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                    i++;
                }
                if (i == numberStart) {
                    throw new IllegalArgumentException("invalid duration '" + text.strip() + "'" + HINT);
                }
                if (i - numberStart > MAX_DIGITS) {
                    throw new ArithmeticException();
                }
                final long amount = Long.parseLong(s.substring(numberStart, i));
                final int unitStart = i;
                while (i < s.length() && s.charAt(i) >= 'a' && s.charAt(i) <= 'z') {
                    i++;
                }
                final String unit = s.substring(unitStart, i);
                switch (unit) {
                    case "d" -> seconds = Math.addExact(seconds, Math.multiplyExact(amount, SECONDS_PER_DAY));
                    case "h" -> seconds = Math.addExact(seconds, Math.multiplyExact(amount, SECONDS_PER_HOUR));
                    case "m" -> seconds = Math.addExact(seconds, Math.multiplyExact(amount, SECONDS_PER_MINUTE));
                    case "s" -> seconds = Math.addExact(seconds, amount);
                    case "ms" -> nanos = Math.addExact(nanos, Math.multiplyExact(amount, NANOS_PER_MILLI));
                    case "us" -> nanos = Math.addExact(nanos, Math.multiplyExact(amount, NANOS_PER_MICRO));
                    case "ns" -> nanos = Math.addExact(nanos, amount);
                    case "" -> throw new IllegalArgumentException(
                            "missing unit after " + amount + " in '" + text.strip() + "'" + HINT);
                    default -> throw new IllegalArgumentException(
                            "unknown unit '" + unit + "' in '" + text.strip() + "', use d, h, m, s, ms, us or ns");
                }
            }
            return Duration.ofSeconds(seconds, nanos);
        } catch (final ArithmeticException e) {
            throw new IllegalArgumentException("duration '" + text.strip() + "' is too large");
        }
    }

    /**
     * Canonical form: the largest units first, zero groups omitted ({@code 90m} becomes {@code 1h30m}),
     * {@code 0s} for zero. {@code parse(format(d)).equals(d)} for every non-negative duration.
     *
     * @param duration a non-negative duration
     * @return the text form
     * @throws IllegalArgumentException if {@code duration} is negative
     */
    public static String format(final Duration duration) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException("negative durations are not supported: " + duration);
        }
        final long total = duration.getSeconds();
        final int nano = duration.getNano();
        if (total == 0 && nano == 0) {
            return "0s";
        }
        final StringBuilder out = new StringBuilder();
        append(out, total / SECONDS_PER_DAY, "d");
        append(out, total % SECONDS_PER_DAY / SECONDS_PER_HOUR, "h");
        append(out, total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE, "m");
        append(out, total % SECONDS_PER_MINUTE, "s");
        append(out, nano / NANOS_PER_MILLI, "ms");
        append(out, nano % NANOS_PER_MILLI / NANOS_PER_MICRO, "us");
        append(out, nano % NANOS_PER_MICRO, "ns");
        return out.toString();
    }

    private static void append(final StringBuilder out, final long amount, final String unit) {
        if (amount != 0) {
            out.append(amount).append(unit);
        }
    }
}
