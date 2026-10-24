package com.github.dewarepk.synthetixlib.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Design section 4: one use per showing, nothing from an earlier showing, nothing after the end. */
class ViewStateTest {

    @Test
    void theFirstClickOfAShowingConsumesTheWholeView() {
        final ViewState state = new ViewState();
        final int generation = state.begin();
        assertTrue(state.tryConsume(generation));
        assertFalse(state.tryConsume(generation), "a replayed packet or a second button does nothing");
    }

    @Test
    void aClickBeforeAnyShowingDoesNothing() {
        assertFalse(new ViewState().tryConsume(0));
        assertFalse(new ViewState().tryConsume(1));
    }

    @Test
    void aNewShowingMakesTheButtonsOfTheOldOneDead() {
        final ViewState state = new ViewState();
        final int first = state.begin();
        final int second = state.begin();          // reopen: fresh callbacks
        assertFalse(state.tryConsume(first), "a stale dialog does nothing");
        assertTrue(state.tryConsume(second));
    }

    @Test
    void aShowingAfterAClickWorksAgain() {
        final ViewState state = new ViewState();
        assertTrue(state.tryConsume(state.begin()));
        final int again = state.begin();           // the handler asked for reopen()
        assertTrue(state.tryConsume(again));
    }

    @Test
    void nothingRunsAfterTheViewEnded() {
        final ViewState state = new ViewState();
        final int generation = state.begin();
        state.end();
        assertTrue(state.isEnded());
        assertFalse(state.tryConsume(generation));
    }

    @Test
    void ofManyThreadsClickingAtOnceExactlyOneWins() throws Exception {
        final ViewState state = new ViewState();
        final int generation = state.begin();
        final int threads = 16;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger winners = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    start.await();
                    if (state.tryConsume(generation)) {
                        winners.incrementAndGet();
                    }
                    return null;
                });
            }
            start.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1, winners.get());
    }
}
