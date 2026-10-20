package com.github.dewarepk.synthetixlib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** One click handler at a time per view. */
class ClickGateTest {

    @Test
    void aSecondClickWhileAHandlerRunsIsIgnoredAndTheNextOneAfterwardsIsNot() {
        final ClickGate gate = new ClickGate();

        assertTrue(gate.tryEnter());
        assertFalse(gate.tryEnter(), "a click during an action");
        gate.leave();
        assertTrue(gate.tryEnter());
    }

    @Test
    void twoThreadsAtTheSameTimeLetExactlyOneThrough() throws Exception {
        final ClickGate gate = new ClickGate();
        final CountDownLatch bothReady = new CountDownLatch(2);
        final CountDownLatch go = new CountDownLatch(1);
        final CountDownLatch secondTried = new CountDownLatch(1);
        final AtomicInteger entered = new AtomicInteger();
        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            final List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    bothReady.countDown();
                    go.await();
                    final boolean in = gate.tryEnter();
                    if (in) {
                        entered.incrementAndGet();
                        // hold the gate until the other thread had its try
                        secondTried.await(2, TimeUnit.SECONDS);
                        gate.leave();
                    } else {
                        secondTried.countDown();
                    }
                    return in;
                }));
            }
            assertTrue(bothReady.await(5, TimeUnit.SECONDS));
            go.countDown();
            int through = 0;
            for (final Future<Boolean> result : results) {
                if (result.get(5, TimeUnit.SECONDS)) {
                    through++;
                }
            }
            assertEquals(1, through);
            assertEquals(1, entered.get());
            assertTrue(gate.tryEnter(), "free again afterwards");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void neverTwoHandlersInsideAtOnceUnderPressure() throws Exception {
        final ClickGate gate = new ClickGate();
        final AtomicInteger inside = new AtomicInteger();
        final AtomicInteger worst = new AtomicInteger();
        final AtomicInteger ran = new AtomicInteger();
        final int threads = 8;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            final List<Future<?>> done = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                done.add(pool.submit(() -> {
                    for (int i = 0; i < 20_000; i++) {
                        if (gate.tryEnter()) {
                            worst.accumulateAndGet(inside.incrementAndGet(), Math::max);
                            ran.incrementAndGet();
                            inside.decrementAndGet();
                            gate.leave();
                        }
                    }
                }));
            }
            for (final Future<?> future : done) {
                future.get(30, TimeUnit.SECONDS);
            }
            assertEquals(1, worst.get());
            assertTrue(ran.get() > 0);
        } finally {
            pool.shutdownNow();
        }
    }
}
