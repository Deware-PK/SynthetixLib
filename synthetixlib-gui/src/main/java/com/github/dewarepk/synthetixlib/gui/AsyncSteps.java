package com.github.dewarepk.synthetixlib.gui;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;

/**
 * Runs the click steps of one item in order; a step that returns an unfinished future holds the later steps (and the
 * view's {@link ClickGate}) until the future completes, fails or times out. Pure: the thread hops and the timer are
 * {@link Ports}, so the order of events is tested without a server.
 *
 * <p>Everything here runs on the viewer's thread. Whichever of completion, failure and timeout comes first settles a step; the
 * others find it settled and do nothing, so a late answer can never release a gate that a newer click holds.
 */
final class AsyncSteps {

    /** One step of a click. */
    interface Step {

        /** @return what the step is called in logs, e.g. {@code action:claim} */
        String name();

        /** @return how long the step may hold the view; only asked for a step that returned an unfinished future */
        long timeoutTicks();

        /**
         * Runs the step on the viewer's thread. A synchronous step reports its own failures and returns {@code null}; an
         * asynchronous one may throw, which counts as a failed future.
         *
         * @return {@code null} if the step is finished, else the work to wait for
         */
        @Nullable CompletableFuture<?> run();
    }

    /** What the steps need from the view and the server. */
    interface Ports {

        /** Runs {@code task} on the viewer's thread, later; does nothing if that thread is gone. */
        void hop(Runnable task);

        /**
         * Runs {@code task} on the viewer's thread after {@code ticks}.
         *
         * @return cancels it
         */
        Runnable later(long ticks, Runnable task);

        /** @return {@code true} once the view ended: nothing may be drawn or sent any more */
        boolean disposed();

        /** A step started waiting ({@code true}) or stopped ({@code false}, only while the view lives). */
        void waiting(Step step, boolean on);

        /** The future of {@code step} failed, or {@code run} threw. */
        void failed(Step step, Throwable cause);

        /** {@code step} did not finish in time. */
        void timedOut(Step step);

        /** The click is over, for whatever reason: the gate opens again. Called exactly once. */
        void finished();
    }

    private AsyncSteps() {
    }

    /** Starts running {@code steps}; returns when every step is done or one waits. */
    static void run(final List<? extends Step> steps, final Ports ports) {
        next(steps, 0, ports);
    }

    private static void next(final List<? extends Step> steps, final int from, final Ports ports) {
        for (int i = from; i < steps.size(); i++) {
            final Step step = steps.get(i);
            final CompletableFuture<?> work;
            try {
                work = step.run();
            } catch (final RuntimeException e) {
                ports.failed(step, e);
                ports.finished();
                return;
            }
            if (work == null) {
                continue;
            }
            if (work.isDone()) {
                if (work.isCompletedExceptionally()) {
                    ports.failed(step, cause(work));
                    ports.finished();
                    return;
                }
                continue;
            }
            await(steps, i, work, ports);
            return;
        }
        ports.finished();
    }

    private static void await(final List<? extends Step> steps, final int index, final CompletableFuture<?> work,
                              final Ports ports) {
        final Step step = steps.get(index);
        final AtomicBoolean settled = new AtomicBoolean();
        ports.waiting(step, true);
        final Runnable cancelTimer = ports.later(step.timeoutTicks(), () -> {
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            if (!ports.disposed()) {
                ports.waiting(step, false);
                ports.timedOut(step);
            }
            ports.finished();
        });
        work.whenComplete((value, error) -> ports.hop(() -> {
            if (!settled.compareAndSet(false, true)) {
                return; // the timeout got there first
            }
            cancelTimer.run();
            if (ports.disposed()) {
                ports.finished(); // the view is gone: only its gate is released
                return;
            }
            ports.waiting(step, false);
            if (error != null) {
                ports.failed(step, error instanceof CompletionException && error.getCause() != null
                        ? error.getCause() : error);
                ports.finished();
                return;
            }
            next(steps, index + 1, ports);
        }));
    }

    private static Throwable cause(final CompletableFuture<?> failed) {
        return failed.isCancelled() ? new CancellationException("the future was cancelled") : failed.exceptionNow();
    }
}
