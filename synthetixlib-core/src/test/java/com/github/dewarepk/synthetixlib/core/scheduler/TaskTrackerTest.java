package com.github.dewarepk.synthetixlib.core.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class TaskTrackerTest {

    /** Stands in for Paper's task; only records cancel(). */
    private static final class FakeTask implements ScheduledTask {
        private final boolean repeating;
        private volatile boolean cancelled;

        FakeTask(final boolean repeating) {
            this.repeating = repeating;
        }

        @Override
        public Plugin getOwningPlugin() {
            return null;
        }

        @Override
        public boolean isRepeatingTask() {
            return repeating;
        }

        @Override
        public CancelledState cancel() {
            cancelled = true;
            return null;
        }

        @Override
        public ExecutionState getExecutionState() {
            return null;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    private static final class CapturingHandler extends Handler {
        final List<LogRecord> records = new ArrayList<>();

        @Override
        public synchronized void publish(final LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private final Logger logger = Logger.getAnonymousLogger();
    private final CapturingHandler logs = new CapturingHandler();
    private final TaskTracker tracker = new TaskTracker(logger, false);

    TaskTrackerTest() {
        logger.setUseParentHandlers(false);
        logger.addHandler(logs);
    }

    /** Submits a task whose Paper side is a FakeTask; the test drives execution by hand. */
    private TaskHandle submit(final boolean repeating, final Runnable body, final List<FakeTask> fakes) {
        return tracker.submit("test", repeating, ignored -> body.run(), null, handle -> {
            final FakeTask fake = new FakeTask(repeating);
            fakes.add(fake);
            return fake;
        });
    }

    @Test
    void oneShotRunsOnceThenIsUntracked() {
        final AtomicInteger runs = new AtomicInteger();
        final TaskHandle handle = submit(false, runs::incrementAndGet, new ArrayList<>());
        assertEquals(1, tracker.liveCount());
        assertFalse(handle.isDone());

        handle.execute(null);

        assertEquals(1, runs.get());
        assertTrue(handle.isDone());
        assertFalse(handle.isCancelled());
        assertEquals(0, tracker.liveCount());

        handle.execute(null);
        assertEquals(1, runs.get(), "a finished one-shot never runs again");
    }

    @Test
    void timerStaysTrackedUntilCancelled() {
        final AtomicInteger runs = new AtomicInteger();
        final List<FakeTask> fakes = new ArrayList<>();
        final TaskHandle handle = submit(true, runs::incrementAndGet, fakes);

        handle.execute(null);
        handle.execute(null);
        assertEquals(2, runs.get());
        assertEquals(1, tracker.liveCount());
        assertFalse(handle.isDone());

        handle.cancel();
        assertTrue(handle.isCancelled());
        assertTrue(handle.isDone());
        assertTrue(fakes.get(0).cancelled);
        assertEquals(0, tracker.liveCount());

        handle.execute(null);
        assertEquals(2, runs.get(), "cancelled timer must not run");
    }

    @Test
    void timerCanCancelItselfFromItsBody() {
        final AtomicInteger runs = new AtomicInteger();
        final List<FakeTask> fakes = new ArrayList<>();
        final TaskHandle handle = tracker.submit("test", true, task -> {
            if (runs.incrementAndGet() == 3) {
                task.cancel();
            }
        }, null, h -> {
            final FakeTask fake = new FakeTask(true);
            fakes.add(fake);
            return fake;
        });

        for (int i = 0; i < 6; i++) {
            handle.execute(null);
        }
        assertEquals(3, runs.get());
        assertTrue(handle.isCancelled());
        assertTrue(fakes.get(0).cancelled);
        assertEquals(0, tracker.liveCount());
    }

    @Test
    void cancelBeforeRunSkipsBodyAndCancelsPaperTask() {
        final AtomicInteger runs = new AtomicInteger();
        final List<FakeTask> fakes = new ArrayList<>();
        final TaskHandle handle = submit(false, runs::incrementAndGet, fakes);

        handle.cancel();
        handle.cancel();
        handle.execute(null);

        assertEquals(0, runs.get());
        assertTrue(handle.isCancelled());
        assertTrue(fakes.get(0).cancelled);
        assertEquals(0, tracker.liveCount());
    }

    @Test
    void cancelAfterCompletionIsNoOp() {
        final TaskHandle handle = submit(false, () -> { }, new ArrayList<>());
        handle.execute(null);
        handle.cancel();
        assertFalse(handle.isCancelled());
        assertTrue(handle.isDone());
    }

    @Test
    void closeCancelsEverythingPendingAndRejectsNewTasks() {
        final List<FakeTask> fakes = new ArrayList<>();
        final TaskHandle a = submit(false, () -> { }, fakes);
        final TaskHandle b = submit(true, () -> { }, fakes);
        final TaskHandle finished = submit(false, () -> { }, fakes);
        finished.execute(null);

        tracker.close();

        assertTrue(a.isCancelled());
        assertTrue(b.isCancelled());
        assertFalse(finished.isCancelled());
        assertTrue(fakes.get(0).cancelled);
        assertTrue(fakes.get(1).cancelled);
        assertFalse(fakes.get(2).cancelled, "finished task is left alone");
        assertEquals(0, tracker.liveCount());
        assertTrue(tracker.isClosed());

        final AtomicInteger launches = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> tracker.submit("test", false, t -> { }, null, h -> {
            launches.incrementAndGet();
            return new FakeTask(false);
        }));
        assertEquals(0, launches.get(), "nothing may be scheduled after close");

        tracker.close();
    }

    @Test
    void closeBetweenRegisterAndAttachCancelsThePaperTask() {
        final FakeTask fake = new FakeTask(false);
        // The launch callback runs after register() and before attach(): close() lands exactly there.
        final TaskHandle handle = tracker.submit("test", false, t -> { }, null, h -> {
            tracker.close();
            return fake;
        });

        assertTrue(handle.isCancelled());
        assertTrue(fake.cancelled, "task created after close started must still be cancelled");
        assertEquals(0, tracker.liveCount());
    }

    @Test
    void launchFailureUntracksAndPropagates() {
        final AtomicInteger runs = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> tracker.submit("test", false,
                t -> runs.incrementAndGet(), null, h -> {
                    throw new IllegalArgumentException("paper said no");
                }));
        assertEquals(0, tracker.liveCount());
    }

    @Test
    void nullFromPaperMeansRetiredAndRunsCallbackOnce() {
        final AtomicInteger retired = new AtomicInteger();
        final AtomicInteger runs = new AtomicInteger();
        final TaskHandle handle = tracker.submit("test", false, t -> runs.incrementAndGet(),
                retired::incrementAndGet, h -> null);

        assertTrue(handle.isCancelled());
        assertEquals(1, retired.get());
        assertEquals(0, tracker.liveCount());
        handle.execute(null);
        assertEquals(0, runs.get());
        handle.retire(retired::incrementAndGet);
        assertEquals(1, retired.get(), "retired runs at most once");
    }

    @Test
    void nullFromPaperWithoutCallbackIsFine() {
        final TaskHandle handle = tracker.submit("test", false, t -> { }, null, h -> null);
        assertTrue(handle.isCancelled());
        assertEquals(0, tracker.liveCount());
    }

    @Test
    void retireAfterSchedulingCancelsAndUntracks() {
        final AtomicInteger retired = new AtomicInteger();
        final List<FakeTask> fakes = new ArrayList<>();
        final TaskHandle handle = submit(false, () -> { }, fakes);

        handle.retire(retired::incrementAndGet);

        assertTrue(handle.isCancelled());
        assertEquals(1, retired.get());
        assertTrue(fakes.get(0).cancelled);
        assertEquals(0, tracker.liveCount());
    }

    @Test
    void retireSkippedWhenAlreadyCancelledByCaller() {
        final AtomicInteger retired = new AtomicInteger();
        final TaskHandle handle = submit(false, () -> { }, new ArrayList<>());
        handle.cancel();
        handle.retire(retired::incrementAndGet);
        assertEquals(0, retired.get());
    }

    @Test
    void exceptionInTimerIsLoggedAndTimerKeepsRunning() {
        final AtomicInteger runs = new AtomicInteger();
        final TaskHandle handle = tracker.submit("global.runTimer", true, t -> {
            if (runs.incrementAndGet() == 1) {
                throw new IllegalStateException("boom");
            }
        }, null, h -> new FakeTask(true));

        handle.execute(null);
        handle.execute(null);

        assertEquals(2, runs.get());
        assertFalse(handle.isCancelled());
        assertEquals(1, tracker.liveCount());
        assertEquals(1, logs.records.size());
        final LogRecord record = logs.records.get(0);
        assertEquals(Level.SEVERE, record.getLevel());
        assertTrue(record.getMessage().contains("global.runTimer"));
        assertNotNull(record.getThrown());
        assertEquals("boom", record.getThrown().getMessage());
    }

    @Test
    void exceptionInOneShotIsLoggedAndTaskStillCompletes() {
        final TaskHandle handle = submit(false, () -> {
            throw new IllegalStateException("boom");
        }, new ArrayList<>());
        handle.execute(null);
        assertTrue(handle.isDone());
        assertEquals(0, tracker.liveCount());
        assertEquals(1, logs.records.size());
    }

    @Test
    void exceptionInRetiredCallbackIsLogged() {
        final TaskHandle handle = submit(false, () -> { }, new ArrayList<>());
        handle.retire(() -> {
            throw new IllegalStateException("boom");
        });
        assertTrue(handle.isCancelled());
        assertEquals(1, logs.records.size());
    }

    @Test
    void debugModeReportsWhereTheTaskWasScheduled() {
        final TaskTracker debugTracker = new TaskTracker(logger, true);
        final TaskHandle handle = debugTracker.submit("global.run", false, t -> {
            throw new IllegalStateException("boom");
        }, null, h -> new FakeTask(false));
        handle.execute(null);
        // Frames inside the scheduler package are skipped on purpose; this test lives in that package,
        // so the reported frame is whatever called into it (JUnit), which is enough to prove the capture works.
        assertTrue(logs.records.get(0).getMessage().contains("scheduled at"), logs.records.get(0).getMessage());
    }

    @Test
    void withoutDebugFlagNoOriginIsReported() {
        final TaskHandle handle = submit(false, () -> {
            throw new IllegalStateException("boom");
        }, new ArrayList<>());
        handle.execute(null);
        assertFalse(logs.records.get(0).getMessage().contains("scheduled at"));
    }

    /**
     * Many threads schedule while one thread closes. Afterwards every Paper task that was ever created
     * must be cancelled and nothing may be tracked: no task may leak past close().
     */
    @Test
    void concurrentSchedulingAndCloseNeverLeaksATask() throws Exception {
        for (int round = 0; round < 50; round++) {
            final TaskTracker raceTracker = new TaskTracker(logger, false);
            final ConcurrentLinkedQueue<FakeTask> created = new ConcurrentLinkedQueue<>();
            final int threads = 6;
            final CountDownLatch start = new CountDownLatch(1);
            final CountDownLatch finished = new CountDownLatch(threads);
            final AtomicInteger accepted = new AtomicInteger();
            final AtomicInteger rejected = new AtomicInteger();

            for (int t = 0; t < threads; t++) {
                final Thread thread = new Thread(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < 500; i++) {
                            try {
                                raceTracker.submit("race", i % 2 == 0, x -> { }, null, h -> {
                                    final FakeTask fake = new FakeTask(false);
                                    created.add(fake);
                                    return fake;
                                });
                                accepted.incrementAndGet();
                            } catch (final IllegalStateException closed) {
                                rejected.incrementAndGet();
                            }
                        }
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finished.countDown();
                    }
                });
                thread.start();
            }
            final Thread closer = new Thread(() -> {
                try {
                    start.await();
                    Thread.yield();
                    raceTracker.close();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            closer.start();
            start.countDown();
            finished.await();
            closer.join();

            assertTrue(raceTracker.isClosed());
            assertEquals(0, raceTracker.liveCount(), "round " + round + ": tasks left tracked after close");
            for (final FakeTask fake : created) {
                assertTrue(fake.cancelled, "round " + round + ": a Paper task survived close()");
            }
            assertEquals(threads * 500, accepted.get() + rejected.get());
        }
    }
}
