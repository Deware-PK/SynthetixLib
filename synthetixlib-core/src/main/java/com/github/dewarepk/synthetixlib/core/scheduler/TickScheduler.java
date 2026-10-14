package com.github.dewarepk.synthetixlib.core.scheduler;

import java.util.function.Consumer;

/**
 * Tick-based scheduling on one thread context (global region, a region, or an entity). Obtained from
 * {@link Scheduler}.
 *
 * <pre>{@code
 * lib.scheduler().global().run(() -> broadcast());
 * lib.scheduler().global().runLater(40, () -> broadcast());
 * lib.scheduler().region(location).run(() -> block.setType(Material.GOLD_BLOCK));
 * }</pre>
 *
 * <p>Delays are in server ticks. {@code 0} is allowed and means "as soon as possible" (the next tick);
 * negative delays and periods below {@code 1} throw {@link IllegalArgumentException}. Scheduling on a
 * closed library instance throws {@link IllegalStateException}. A task body that throws is logged and,
 * for repeating tasks, keeps running.
 */
public interface TickScheduler {

    /**
     * Runs {@code task} on the next tick.
     *
     * @param task the code to run
     * @return a handle to the task
     */
    SynthetixTask run(Runnable task);

    /**
     * Runs {@code task} after a delay.
     *
     * @param delayTicks ticks to wait; {@code 0} means the next tick
     * @param task       the code to run
     * @return a handle to the task
     */
    SynthetixTask runLater(long delayTicks, Runnable task);

    /**
     * Runs {@code task} repeatedly until it is cancelled.
     *
     * <pre>{@code
     * lib.scheduler().global().runTimer(0, 20, task -> {
     *     if (done) task.cancel();
     * });
     * }</pre>
     *
     * @param delayTicks  ticks before the first run; {@code 0} means the next tick
     * @param periodTicks ticks between runs; at least {@code 1}
     * @param task        the code to run; receives its own handle so it can cancel itself
     * @return a handle to the task
     */
    SynthetixTask runTimer(long delayTicks, long periodTicks, Consumer<SynthetixTask> task);
}
