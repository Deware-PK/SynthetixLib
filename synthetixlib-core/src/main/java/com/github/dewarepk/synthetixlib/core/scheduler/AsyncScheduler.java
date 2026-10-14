package com.github.dewarepk.synthetixlib.core.scheduler;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Off-thread scheduling for IO, database and HTTP work. Uses real time ({@link Duration}, millisecond
 * precision), not ticks. Never touch the Bukkit world from these tasks; hop back with
 * {@link Scheduler#global()}, {@link Scheduler#region(org.bukkit.Location)} or
 * {@link Scheduler#entity(org.bukkit.entity.Entity)}.
 *
 * <pre>{@code
 * lib.scheduler().async().run(() -> saveToDb());
 * lib.scheduler().async().runLater(Duration.ofSeconds(5), () -> flush());
 * lib.scheduler().async().runTimer(Duration.ZERO, Duration.ofMinutes(1), task -> autosave());
 * }</pre>
 *
 * <p>Negative delays and periods below 1 ms throw {@link IllegalArgumentException}; sub-millisecond
 * delays are rounded up to 1 ms. Scheduling on a closed library instance throws
 * {@link IllegalStateException}. A task body that throws is logged and, for repeating tasks, keeps running.
 */
public interface AsyncScheduler {

    /**
     * Runs {@code task} on an async thread as soon as possible.
     *
     * @param task the code to run
     * @return a handle to the task
     */
    SynthetixTask run(Runnable task);

    /**
     * Runs {@code task} on an async thread after a delay.
     *
     * @param delay how long to wait; {@link Duration#ZERO} means as soon as possible
     * @param task  the code to run
     * @return a handle to the task
     */
    SynthetixTask runLater(Duration delay, Runnable task);

    /**
     * Runs {@code task} repeatedly on an async thread until it is cancelled.
     *
     * @param delay  time before the first run; {@link Duration#ZERO} means as soon as possible
     * @param period time between runs; at least 1 ms
     * @param task   the code to run; receives its own handle so it can cancel itself
     * @return a handle to the task
     */
    SynthetixTask runTimer(Duration delay, Duration period, Consumer<SynthetixTask> task);
}
