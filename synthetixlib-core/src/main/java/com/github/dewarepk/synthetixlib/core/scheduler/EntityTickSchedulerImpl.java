package com.github.dewarepk.synthetixlib.core.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.function.Consumer;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Wraps {@code Entity#getScheduler()}. Paper returns {@code null} when the entity is already removed;
 * {@link TaskTracker} turns that into a cancelled task and runs {@code retired}. When the entity is
 * removed later, Paper calls the retired hook below, which cancels our handle (so it is untracked).
 */
final class EntityTickSchedulerImpl extends BaseTickScheduler implements EntityTickScheduler {

    private final Plugin plugin;
    private final Entity entity;

    EntityTickSchedulerImpl(final Plugin plugin, final TaskTracker tracker, final Entity entity) {
        super(tracker, "entity");
        this.plugin = plugin;
        this.entity = entity;
    }

    @Override
    public SynthetixTask run(final Runnable task, final @Nullable Runnable retired) {
        return submitRun(task, retired);
    }

    @Override
    public SynthetixTask runLater(final long delayTicks, final Runnable task, final @Nullable Runnable retired) {
        return submitLater(delayTicks, task, retired);
    }

    @Override
    public SynthetixTask runTimer(final long delayTicks, final long periodTicks,
                                  final Consumer<SynthetixTask> task, final @Nullable Runnable retired) {
        return submitTimer(delayTicks, periodTicks, task, retired);
    }

    @Override
    @Nullable ScheduledTask launchNow(final TaskHandle handle, final @Nullable Runnable retired) {
        return entity.getScheduler().run(plugin, handle::execute, () -> handle.retire(retired));
    }

    @Override
    @Nullable ScheduledTask launchLater(final TaskHandle handle, final long delayTicks,
                                        final @Nullable Runnable retired) {
        return entity.getScheduler().runDelayed(plugin, handle::execute, () -> handle.retire(retired), delayTicks);
    }

    @Override
    @Nullable ScheduledTask launchTimer(final TaskHandle handle, final long delayTicks, final long periodTicks,
                                        final @Nullable Runnable retired) {
        return entity.getScheduler()
                .runAtFixedRate(plugin, handle::execute, () -> handle.retire(retired), delayTicks, periodTicks);
    }
}
