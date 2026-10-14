package com.github.dewarepk.synthetixlib.core.scheduler;

/**
 * Handle to a task scheduled through the {@link Scheduler}. Thread-safe; every method may be called
 * from any thread, including from inside the task body.
 *
 * <pre>{@code
 * SynthetixTask task = lib.scheduler().global().runTimer(0, 20, t -> tick());
 * // later, from any thread:
 * task.cancel();
 * }</pre>
 */
public interface SynthetixTask {

    /**
     * Cancels the task so it never runs (again). Has no effect on a body that is already running,
     * and none on a task that already finished. Idempotent.
     */
    void cancel();

    /**
     * @return {@code true} if the task was cancelled ({@link #cancel()}, {@code SynthetixLib#close()},
     *         or because its entity was removed) before it finished
     */
    boolean isCancelled();

    /**
     * @return {@code true} once the task will never run again: a one-shot task that has run, or any
     *         task that was cancelled (same meaning as {@link java.util.concurrent.Future#isDone()})
     */
    boolean isDone();
}
