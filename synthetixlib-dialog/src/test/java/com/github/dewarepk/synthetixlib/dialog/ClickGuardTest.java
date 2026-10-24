package com.github.dewarepk.synthetixlib.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The per-player guard of registry-dialog clicks. */
class ClickGuardTest {

    private static final long INTERVAL = Duration.ofMillis(250).toNanos();

    @Test
    void aBurstOfClicksRunsOneHandlerAndTheNextOneRunsAfterTheInterval() {
        final ClickGuard guard = new ClickGuard();
        int runs = 0;
        for (int i = 0; i < 100; i++) {
            if (guard.enter(i * 1_000L, INTERVAL)) { // 100 clicks within 0.1 ms
                runs++;
                guard.leave(); // the handler is already over: only the interval holds the rest back
            }
        }
        assertEquals(1, runs);
        assertTrue(guard.enter(INTERVAL + 100_000L, INTERVAL));
    }

    @Test
    void aClickWhileTheHandlerStillRunsIsIgnoredEvenAfterTheInterval() {
        final ClickGuard guard = new ClickGuard();
        assertTrue(guard.enter(0, INTERVAL));
        assertFalse(guard.enter(10 * INTERVAL, INTERVAL)); // e.g. submitAsync not finished
        guard.leave();
        assertTrue(guard.enter(11 * INTERVAL, INTERVAL));
    }
}
