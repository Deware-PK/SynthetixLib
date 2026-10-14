package com.github.dewarepk.synthetixlib.core.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Objects;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** Shared validation + tracking for the tick-based schedulers; subclasses only call Paper. */
abstract class BaseTickScheduler implements TickScheduler {

    private final TaskTracker tracker;
    private final String kind;

    BaseTickScheduler(final TaskTracker tracker, final String kind) {
        this.tracker = tracker;
        this.kind = kind;
    }

    abstract @Nullable ScheduledTask launchNow(TaskHandle handle, @Nullable Runnable retired);

    abstract @Nullable ScheduledTask launchLater(TaskHandle handle, long delayTicks, @Nullable Runnable retired);

    abstract @Nullable ScheduledTask launchTimer(TaskHandle handle, long delayTicks, long periodTicks,
                                                 @Nullable Runnable retired);

    @Override
    public final SynthetixTask run(final Runnable task) {
        return submitRun(task, null);
    }

    @Override
    public final SynthetixTask runLater(final long delayTicks, final Runnable task) {
        return submitLater(delayTicks, task, null);
    }

    @Override
    public final SynthetixTask runTimer(final long delayTicks, final long periodTicks,
                                        final Consumer<SynthetixTask> task) {
        return submitTimer(delayTicks, periodTicks, task, null);
    }

    final SynthetixTask submitRun(final Runnable task, final @Nullable Runnable retired) {
        Objects.requireNonNull(task, "task");
        return tracker.submit(kind + ".run", false, ignored -> task.run(), retired,
                handle -> launchNow(handle, retired));
    }

    final SynthetixTask submitLater(final long delayTicks, final Runnable task, final @Nullable Runnable retired) {
        Objects.requireNonNull(task, "task");
        final long delay = Delays.delayTicks(delayTicks);
        return tracker.submit(kind + ".runLater", false, ignored -> task.run(), retired,
                handle -> launchLater(handle, delay, retired));
    }

    final SynthetixTask submitTimer(final long delayTicks, final long periodTicks,
                                    final Consumer<SynthetixTask> task, final @Nullable Runnable retired) {
        Objects.requireNonNull(task, "task");
        final long delay = Delays.delayTicks(delayTicks);
        final long period = Delays.periodTicks(periodTicks);
        return tracker.submit(kind + ".runTimer", true, task, retired,
                handle -> launchTimer(handle, delay, period, retired));
    }
}
