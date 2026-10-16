package com.github.dewarepk.synthetixlib.config.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DurationsTest {

    @ParameterizedTest(name = "{0} parses to {1} ms")
    @CsvSource({
            "250ms, 250",
            "30s, 30000",
            "5m, 300000",
            "1h30m, 5400000",
            "2d, 172800000",
            "1d12h, 129600000",
            "0s, 0",
            "90s, 90000",
            "1H30M, 5400000",
            "' 1h 30m ', 5400000",
            "1m30s250ms, 90250",
            "100h, 360000000",
    })
    void parsesValidText(final String text, final long millis) {
        assertEquals(Duration.ofMillis(millis), Durations.parse(text));
    }

    @Test
    void parsesSubMillisecondUnits() {
        assertEquals(Duration.ofNanos(1_500), Durations.parse("1us500ns"));
        assertEquals(Duration.ofNanos(7), Durations.parse("7ns"));
    }

    @ParameterizedTest(name = "[{0}] is rejected")
    @ValueSource(strings = {"", "  ", "abc", "5", "1.5h", "-5s", "5x", "30m1x", "5mins", "h", "1h 30", "1 2s"})
    void rejectsInvalidText(final String text) {
        assertThrows(IllegalArgumentException.class, () -> Durations.parse(text));
    }

    @Test
    void errorMessagesNameTheProblem() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Durations.parse("5mins"))
                .getMessage().contains("unknown unit 'mins'"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Durations.parse("5"))
                .getMessage().contains("missing unit"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Durations.parse("9999999999999999d"))
                .getMessage().contains("too large"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Durations.parse("99999999999999999999s"))
                .getMessage().contains("too large"));
    }

    @ParameterizedTest(name = "{0} ms formats as {1}")
    @CsvSource({
            "0, 0s",
            "250, 250ms",
            "1000, 1s",
            "1500, 1s500ms",
            "30000, 30s",
            "90000, 1m30s",
            "300000, 5m",
            "5400000, 1h30m",
            "86400000, 1d",
            "90061001, 1d1h1m1s1ms",
    })
    void formatsCanonically(final long millis, final String expected) {
        assertEquals(expected, Durations.format(Duration.ofMillis(millis)));
    }

    @Test
    void roundTripsExactly() {
        final List<Duration> samples = List.of(
                Duration.ZERO, Duration.ofNanos(1), Duration.ofNanos(999_999_999), Duration.ofMillis(1),
                Duration.ofSeconds(59), Duration.ofSeconds(3_601), Duration.ofDays(400).plusNanos(123_456_789),
                Duration.ofSeconds(Long.MAX_VALUE / 1_000), Duration.ofSeconds(1_234_567_890L, 987_654_321));
        for (final Duration sample : samples) {
            assertEquals(sample, Durations.parse(Durations.format(sample)), sample.toString());
        }
    }

    @Test
    void rejectsNegativeDurationOnFormat() {
        assertThrows(IllegalArgumentException.class, () -> Durations.format(Duration.ofSeconds(-1)));
    }
}
