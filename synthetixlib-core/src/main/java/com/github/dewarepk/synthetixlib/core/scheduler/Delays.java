package com.github.dewarepk.synthetixlib.core.scheduler;

import java.time.Duration;
import java.util.Objects;

/** Argument validation and delay mapping. Pure functions, no server needed. */
final class Delays {

    /** Largest millisecond value that still fits in nanoseconds inside Paper's async scheduler. */
    static final long MAX_MILLIS = Long.MAX_VALUE / 1_000_000L;

    private Delays() {
    }

    /**
     * Paper needs at least 1 tick for delayed and fixed-rate tasks; {@code 0} ("now") maps to 1,
     * which is the same moment as an immediate run (next tick).
     */
    static long delayTicks(final long ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("delay must be >= 0 ticks, was " + ticks);
        }
        return Math.max(1L, ticks);
    }

    static long periodTicks(final long ticks) {
        if (ticks < 1) {
            throw new IllegalArgumentException("period must be >= 1 tick, was " + ticks);
        }
        return ticks;
    }

    /** Milliseconds for an async delay: {@code >= 0}, sub-millisecond rounds up to 1, huge values are capped. */
    static long delayMillis(final Duration delay) {
        Objects.requireNonNull(delay, "delay");
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must not be negative, was " + delay);
        }
        long millis;
        try {
            millis = delay.toMillis();
        } catch (final ArithmeticException overflow) {
            return MAX_MILLIS;
        }
        if (millis == 0 && !delay.isZero()) {
            millis = 1;
        }
        return Math.min(millis, MAX_MILLIS);
    }

    /** First-run delay of an async timer: {@link #delayMillis} but never 0 (0 maps to 1 ms). */
    static long timerStartMillis(final Duration delay) {
        return Math.max(1L, delayMillis(delay));
    }

    static long periodMillis(final Duration period) {
        Objects.requireNonNull(period, "period");
        if (period.isNegative() || period.isZero()) {
            throw new IllegalArgumentException("period must be >= 1 ms, was " + period);
        }
        return delayMillis(period);
    }
}
