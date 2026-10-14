package com.github.dewarepk.synthetixlib.core.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DelaysTest {

    @Test
    void tickDelayZeroMapsToOne() {
        assertEquals(1L, Delays.delayTicks(0));
        assertEquals(1L, Delays.delayTicks(1));
        assertEquals(40L, Delays.delayTicks(40));
    }

    @Test
    void negativeTickDelayRejected() {
        assertThrows(IllegalArgumentException.class, () -> Delays.delayTicks(-1));
        assertThrows(IllegalArgumentException.class, () -> Delays.delayTicks(Long.MIN_VALUE));
    }

    @Test
    void tickPeriodMustBePositive() {
        assertEquals(1L, Delays.periodTicks(1));
        assertThrows(IllegalArgumentException.class, () -> Delays.periodTicks(0));
        assertThrows(IllegalArgumentException.class, () -> Delays.periodTicks(-5));
    }

    @Test
    void asyncDelayMapping() {
        assertEquals(0L, Delays.delayMillis(Duration.ZERO));
        assertEquals(1L, Delays.delayMillis(Duration.ofNanos(1)), "sub-millisecond rounds up");
        assertEquals(1500L, Delays.delayMillis(Duration.ofMillis(1500)));
        assertEquals(60_000L, Delays.delayMillis(Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> Delays.delayMillis(Duration.ofMillis(-1)));
        assertThrows(NullPointerException.class, () -> Delays.delayMillis(null));
    }

    @Test
    void hugeAsyncDelayIsCapped() {
        assertEquals(Delays.MAX_MILLIS, Delays.delayMillis(Duration.ofSeconds(Long.MAX_VALUE)));
    }

    @Test
    void asyncTimerStartNeverZero() {
        assertEquals(1L, Delays.timerStartMillis(Duration.ZERO));
        assertEquals(250L, Delays.timerStartMillis(Duration.ofMillis(250)));
    }

    @Test
    void asyncPeriodMustBeAtLeastOneMilli() {
        assertEquals(1L, Delays.periodMillis(Duration.ofMillis(1)));
        assertEquals(1L, Delays.periodMillis(Duration.ofNanos(1)));
        assertThrows(IllegalArgumentException.class, () -> Delays.periodMillis(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> Delays.periodMillis(Duration.ofMillis(-1)));
    }
}
