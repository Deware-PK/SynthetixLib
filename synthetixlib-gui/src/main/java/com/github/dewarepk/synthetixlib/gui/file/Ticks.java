package com.github.dewarepk.synthetixlib.gui.file;

import com.github.dewarepk.synthetixlib.config.codec.Durations;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reads {@code update-every}: server ticks ({@code 20t}) or a duration ({@code 1s}, {@code 500ms}, {@code 1m30s}).
 *
 * <pre>{@code
 * update-every: 20t     # same as 1s
 * }</pre>
 */
public final class Ticks {

    private static final Pattern TICKS = Pattern.compile("(\\d{1,9})t");
    private static final long NANOS_PER_TICK = 50_000_000L;
    /** One day. */
    private static final int MAX = 20 * 60 * 60 * 24;

    private Ticks() {
    }

    /**
     * @param text e.g. {@code 20t} or {@code 1s}; a duration is rounded up to whole ticks
     * @return a number of ticks from 1 to one day
     * @throws IllegalArgumentException with a message fit for a config error
     */
    public static int parse(final String text) {
        final String s = text.strip().toLowerCase(Locale.ROOT);
        final var ticks = TICKS.matcher(s);
        final long value;
        if (ticks.matches()) {
            value = Long.parseLong(ticks.group(1));
        } else {
            final Duration duration = Durations.parse(s);
            if (duration.getSeconds() > MAX / 20) {
                throw new IllegalArgumentException("must be at most one day (was '" + text.strip() + "')");
            }
            final long nanos = duration.getSeconds() * 1_000_000_000L + duration.getNano();
            value = (nanos + NANOS_PER_TICK - 1) / NANOS_PER_TICK;
        }
        if (value < 1) {
            throw new IllegalArgumentException("must be at least 1t (was '" + text.strip() + "')");
        }
        if (value > MAX) {
            throw new IllegalArgumentException("must be at most one day (was '" + text.strip() + "')");
        }
        return (int) value;
    }
}
