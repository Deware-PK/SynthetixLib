package com.github.dewarepk.synthetixlib.core.scheduler;

import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * {@link TickScheduler} bound to an entity: tasks follow the entity across regions. Adds overloads
 * with a {@code retired} callback.
 *
 * <pre>{@code
 * lib.scheduler().entity(npc).run(() -> walk(), () -> log("npc gone"));
 * }</pre>
 *
 * <p>If the entity is removed before a task ran, the task is cancelled and {@code retired} (if given) runs
 * instead. If the entity is <em>already</em> removed when scheduling, the returned task is cancelled
 * ({@link SynthetixTask#isCancelled()}) and {@code retired} runs immediately on the calling thread.
 * {@code retired} does not run for tasks cancelled by the caller or by {@code SynthetixLib#close()}.
 */
public interface EntityTickScheduler extends TickScheduler {

    /**
     * Like {@link #run(Runnable)} with a retired callback.
     *
     * @param task    the code to run
     * @param retired runs if the entity is removed first; may be {@code null}
     * @return a handle to the task (never {@code null})
     */
    SynthetixTask run(Runnable task, @Nullable Runnable retired);

    /**
     * Like {@link #runLater(long, Runnable)} with a retired callback.
     *
     * @param delayTicks ticks to wait; {@code 0} means the next tick
     * @param task       the code to run
     * @param retired    runs if the entity is removed first; may be {@code null}
     * @return a handle to the task (never {@code null})
     */
    SynthetixTask runLater(long delayTicks, Runnable task, @Nullable Runnable retired);

    /**
     * Like {@link #runTimer(long, long, Consumer)} with a retired callback.
     *
     * @param delayTicks  ticks before the first run; {@code 0} means the next tick
     * @param periodTicks ticks between runs; at least {@code 1}
     * @param task        the code to run
     * @param retired     runs if the entity is removed first; may be {@code null}
     * @return a handle to the task (never {@code null})
     */
    SynthetixTask runTimer(long delayTicks, long periodTicks, Consumer<SynthetixTask> task,
                           @Nullable Runnable retired);
}
