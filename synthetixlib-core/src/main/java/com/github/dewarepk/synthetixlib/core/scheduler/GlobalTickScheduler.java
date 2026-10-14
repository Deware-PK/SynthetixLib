package com.github.dewarepk.synthetixlib.core.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/** Wraps Paper's {@code GlobalRegionScheduler}. */
final class GlobalTickScheduler extends BaseTickScheduler {

    private final Plugin plugin;

    GlobalTickScheduler(final Plugin plugin, final TaskTracker tracker) {
        super(tracker, "global");
        this.plugin = plugin;
    }

    @Override
    @Nullable ScheduledTask launchNow(final TaskHandle handle, final @Nullable Runnable retired) {
        return plugin.getServer().getGlobalRegionScheduler().run(plugin, handle::execute);
    }

    @Override
    @Nullable ScheduledTask launchLater(final TaskHandle handle, final long delayTicks,
                                        final @Nullable Runnable retired) {
        return plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, handle::execute, delayTicks);
    }

    @Override
    @Nullable ScheduledTask launchTimer(final TaskHandle handle, final long delayTicks, final long periodTicks,
                                        final @Nullable Runnable retired) {
        return plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, handle::execute, delayTicks, periodTicks);
    }
}
