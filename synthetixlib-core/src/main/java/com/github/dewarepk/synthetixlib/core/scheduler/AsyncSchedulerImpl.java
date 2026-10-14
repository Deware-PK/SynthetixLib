package com.github.dewarepk.synthetixlib.core.scheduler;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;

/** Wraps Paper's async scheduler (not to be confused with this package's {@link AsyncScheduler}). */
final class AsyncSchedulerImpl implements AsyncScheduler {

    private static final String KIND = "async";

    private final Plugin plugin;
    private final TaskTracker tracker;

    AsyncSchedulerImpl(final Plugin plugin, final TaskTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
    }

    @Override
    public SynthetixTask run(final Runnable task) {
        Objects.requireNonNull(task, "task");
        return tracker.submit(KIND + ".run", false, ignored -> task.run(), null,
                handle -> plugin.getServer().getAsyncScheduler().runNow(plugin, handle::execute));
    }

    @Override
    public SynthetixTask runLater(final Duration delay, final Runnable task) {
        Objects.requireNonNull(task, "task");
        final long millis = Delays.delayMillis(delay);
        return tracker.submit(KIND + ".runLater", false, ignored -> task.run(), null, handle -> millis == 0
                ? plugin.getServer().getAsyncScheduler().runNow(plugin, handle::execute)
                : plugin.getServer().getAsyncScheduler()
                        .runDelayed(plugin, handle::execute, millis, TimeUnit.MILLISECONDS));
    }

    @Override
    public SynthetixTask runTimer(final Duration delay, final Duration period, final Consumer<SynthetixTask> task) {
        Objects.requireNonNull(task, "task");
        final long startMillis = Delays.timerStartMillis(delay);
        final long periodMillis = Delays.periodMillis(period);
        return tracker.submit(KIND + ".runTimer", true, task, null, handle -> plugin.getServer().getAsyncScheduler()
                .runAtFixedRate(plugin, handle::execute, startMillis, periodMillis, TimeUnit.MILLISECONDS));
    }
}
