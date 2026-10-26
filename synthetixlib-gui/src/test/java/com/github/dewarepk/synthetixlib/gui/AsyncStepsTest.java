package com.github.dewarepk.synthetixlib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The order of events of a click with asynchronous steps: a gate (as {@link ClickGate}) that stays closed until the future
 * is done, with a hop queue and a timer the test advances by hand.
 */
class AsyncStepsTest {

    /** The view side: a gate, a queue for the viewer's thread, one timer, and a log of what was reported. */
    private static final class Fake implements AsyncSteps.Ports {

        final ClickGate gate = new ClickGate();
        final Queue<Runnable> hops = new ArrayDeque<>();
        final List<String> log = new ArrayList<>();
        boolean disposed;
        int finishedCalls;
        @Nullable Runnable timer;
        long timerTicks;
        boolean timerCancelled;

        @Override
        public void hop(final Runnable task) {
            hops.add(task);
        }

        @Override
        public Runnable later(final long ticks, final Runnable task) {
            timer = task;
            timerTicks = ticks;
            return () -> timerCancelled = true;
        }

        @Override
        public boolean disposed() {
            return disposed;
        }

        @Override
        public void waiting(final AsyncSteps.Step step, final boolean on) {
            log.add((on ? "busy " : "idle ") + step.name());
        }

        @Override
        public void failed(final AsyncSteps.Step step, final Throwable cause) {
            log.add("failed " + step.name() + " " + cause.getMessage());
        }

        @Override
        public void timedOut(final AsyncSteps.Step step) {
            log.add("timeout " + step.name());
        }

        @Override
        public void finished() {
            finishedCalls++;
            gate.leave();
        }

        /** A click: enters the gate like {@code MenuViewImpl#click} and runs the steps; {@code false} if it was ignored. */
        boolean click(final AsyncSteps.Step... steps) {
            if (!gate.tryEnter()) {
                return false;
            }
            AsyncSteps.run(List.of(steps), this);
            return true;
        }

        void runHops() {
            while (!hops.isEmpty()) {
                hops.remove().run();
            }
        }

        void fireTimer() {
            final Runnable task = timer;
            if (task != null && !timerCancelled) {
                task.run();
            }
        }
    }

    private static AsyncSteps.Step step(final String name, final Supplier<@Nullable CompletableFuture<?>> body) {
        return new AsyncSteps.Step() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public long timeoutTicks() {
                return 600;
            }

            @Override
            public @Nullable CompletableFuture<?> run() {
                return body.get();
            }
        };
    }

    @Test
    void aSecondClickWhileTheFutureIsPendingIsIgnoredAndTheNextOneAfterwardsRuns() {
        final Fake view = new Fake();
        final List<String> ran = new ArrayList<>();
        final CompletableFuture<Object> work = new CompletableFuture<>();
        final AsyncSteps.Step claim = step("action:claim", () -> {
            ran.add("claim");
            return work;
        });

        assertTrue(view.click(claim));
        assertFalse(view.click(claim), "a click while the work is in flight");
        assertFalse(view.click(claim));
        assertEquals(List.of("claim"), ran);

        work.complete("done");
        assertFalse(view.click(claim), "completed on another thread, but the viewer's thread has not seen it yet");
        view.runHops();
        assertEquals(1, view.finishedCalls);
        assertTrue(view.timerCancelled, "the timeout is cancelled by the completion");

        assertTrue(view.click(step("action:claim", () -> {
            ran.add("claim again");
            return CompletableFuture.completedFuture(null);
        })));
        assertEquals(List.of("claim", "claim again"), ran);
    }

    @Test
    void aFutureThatIsDoneAlreadyNeverHoldsTheGate() {
        final Fake view = new Fake();

        assertTrue(view.click(step("action:fast", () -> CompletableFuture.completedFuture("x"))));

        assertEquals(1, view.finishedCalls);
        assertTrue(view.gate.tryEnter());
        assertTrue(view.log.isEmpty(), "no busy look for work that is done: " + view.log);
        assertTrue(view.hops.isEmpty());
    }

    @Test
    void laterStepsWaitForTheFutureAndRunInOrder() {
        final Fake view = new Fake();
        final List<String> ran = new ArrayList<>();
        final CompletableFuture<Object> work = new CompletableFuture<>();

        view.click(step("sound", () -> {
            ran.add("sound");
            return null;
        }), step("action:claim", () -> {
            ran.add("claim");
            return work;
        }), step("close", () -> {
            ran.add("close");
            return null;
        }));

        assertEquals(List.of("sound", "claim"), ran, "the step after an async one waits");
        work.complete(null);
        view.runHops();
        assertEquals(List.of("sound", "claim", "close"), ran);
        assertEquals(List.of("busy action:claim", "idle action:claim"), view.log);
        assertEquals(1, view.finishedCalls);
    }

    @Test
    void aFailedFutureReleasesTheGateAndSkipsTheLaterSteps() {
        final Fake view = new Fake();
        final List<String> ran = new ArrayList<>();
        final CompletableFuture<Object> work = new CompletableFuture<>();

        view.click(step("action:claim", () -> work), step("close", () -> {
            ran.add("close");
            return null;
        }));
        work.completeExceptionally(new IllegalStateException("db down"));
        view.runHops();

        assertTrue(ran.isEmpty());
        assertEquals(List.of("busy action:claim", "idle action:claim", "failed action:claim db down"), view.log);
        assertEquals(1, view.finishedCalls);
        assertTrue(view.gate.tryEnter());
    }

    @Test
    void aFutureThatFailedBeforeItWasReturnedAndAHandlerThatThrowsAreFailuresToo() {
        final Fake failedAlready = new Fake();
        failedAlready.click(step("action:a", () -> CompletableFuture.failedFuture(new IllegalStateException("boom"))),
                step("later", () -> {
                    throw new AssertionError("must be skipped");
                }));
        assertEquals(List.of("failed action:a boom"), failedAlready.log);
        assertEquals(1, failedAlready.finishedCalls);

        final Fake throwing = new Fake();
        throwing.click(step("action:b", () -> {
            throw new IllegalArgumentException("bad");
        }));
        assertEquals(List.of("failed action:b bad"), throwing.log);
        assertEquals(1, throwing.finishedCalls);
        assertTrue(throwing.gate.tryEnter());
    }

    @Test
    void aCompletionWrapperIsUnwrappedForTheLog() {
        final Fake view = new Fake();
        final CompletableFuture<Object> work = new CompletableFuture<>();
        view.click(step("action:claim", () -> work.thenApply(value -> value)));

        work.completeExceptionally(new IllegalStateException("inner"));
        view.runHops();

        assertEquals("failed action:claim inner", view.log.get(view.log.size() - 1));
    }

    @Test
    void theTimeoutReleasesTheGateOnceAndALateAnswerChangesNothing() {
        final Fake view = new Fake();
        final CompletableFuture<Object> work = new CompletableFuture<>();
        final List<String> ran = new ArrayList<>();

        view.click(step("action:claim", () -> work), step("close", () -> {
            ran.add("close");
            return null;
        }));
        assertEquals(600, view.timerTicks);
        view.fireTimer();

        assertEquals(List.of("busy action:claim", "idle action:claim", "timeout action:claim"), view.log);
        assertEquals(1, view.finishedCalls);
        assertTrue(ran.isEmpty(), "the later steps do not run after a timeout");

        // the next click holds the gate again; the late answer of the first one must not open it
        final CompletableFuture<Object> second = new CompletableFuture<>();
        assertTrue(view.click(step("action:claim", () -> second)));
        work.complete(null);
        view.runHops();
        assertEquals(1, view.finishedCalls, "the late answer did not release the gate of the second click");
        assertFalse(view.gate.tryEnter());
        assertTrue(ran.isEmpty());
    }

    @Test
    void aNullFutureIsAStepThatIsDone() {
        final Fake view = new Fake();
        final List<String> ran = new ArrayList<>();

        view.click(step("action:x", () -> null), step("next", () -> {
            ran.add("next");
            return null;
        }));

        assertEquals(List.of("next"), ran);
        assertEquals(1, view.finishedCalls);
    }

    @Test
    void aViewThatEndedBeforeTheAnswerOnlyReleasesItsGate() {
        final Fake view = new Fake();
        final List<String> ran = new ArrayList<>();
        final CompletableFuture<Object> work = new CompletableFuture<>();

        view.click(step("action:claim", () -> work), step("message", () -> {
            ran.add("message");
            return null;
        }));
        view.log.clear();
        view.disposed = true;
        work.complete(null);
        view.runHops();

        assertTrue(view.log.isEmpty(), "no refresh, no message: " + view.log);
        assertTrue(ran.isEmpty());
        assertEquals(1, view.finishedCalls);
    }

    @Test
    void aFailureAfterTheViewEndedIsStillReportedOnlyToTheLogNotAsAWaitingChange() {
        final Fake view = new Fake();
        final CompletableFuture<Object> work = new CompletableFuture<>();
        view.click(step("action:claim", () -> work));
        view.log.clear();
        view.disposed = true;

        work.completeExceptionally(new IllegalStateException("late"));
        view.runHops();

        assertTrue(view.log.isEmpty(), "nothing is drawn or sent for a dead view: " + view.log);
        assertEquals(1, view.finishedCalls);
    }

    @Test
    void theTimeoutOfADeadViewOnlyReleasesTheGate() {
        final Fake view = new Fake();
        view.click(step("action:claim", () -> new CompletableFuture<>()));
        view.log.clear();
        view.disposed = true;

        view.fireTimer();

        assertTrue(view.log.isEmpty());
        assertEquals(1, view.finishedCalls);
    }
}
