package com.github.dewarepk.synthetixlib.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Writes a wait for people: the two largest units, seconds rounded up. Not static, so the words come from the
 * language of whoever reads it.
 *
 * <pre>{@code
 * TimeFormat time = TimeFormat.of(messages.units());
 * time.format(Duration.ofSeconds(72));      // "1m 12s"
 * time.format(Duration.ofHours(1));         // "1h 0m"
 * time.format(Duration.ofSeconds(183_600)); // "2d 3h"
 * }</pre>
 */
public final class TimeFormat {

    private static final long MINUTE = 60;
    private static final long HOUR = 3_600;
    private static final long DAY = 86_400;

    private final TimeUnits units;

    private TimeFormat(final TimeUnits units) {
        this.units = units;
    }

    /**
     * @param units the unit words
     * @return a formatter writing them
     */
    public static TimeFormat of(final TimeUnits units) {
        return new TimeFormat(Objects.requireNonNull(units, "units"));
    }

    /** @return a formatter with the English words {@code d h m s} */
    public static TimeFormat english() {
        return new TimeFormat(TimeUnits.defaults());
    }

    /**
     * @param duration the time, a part of a second counts as a whole one; negative counts as zero
     * @return e.g. {@code 59s}, {@code 1m 12s}, {@code 1h 0m}, {@code 2d 3h}
     */
    public String format(final Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            return 0 + units.s();
        }
        final long total = duration.getSeconds() + (duration.getNano() > 0 ? 1 : 0);
        if (total >= DAY) {
            return total / DAY + units.d() + " " + total % DAY / HOUR + units.h();
        }
        if (total >= HOUR) {
            return total / HOUR + units.h() + " " + total % HOUR / MINUTE + units.m();
        }
        if (total >= MINUTE) {
            return total / MINUTE + units.m() + " " + total % MINUTE + units.s();
        }
        return total + units.s();
    }
}
