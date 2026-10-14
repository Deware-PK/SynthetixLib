package com.github.dewarepk.synthetixlib.core.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/** Wraps Paper's {@code RegionScheduler} for one chunk. */
final class RegionTickScheduler extends BaseTickScheduler {

    private final Plugin plugin;
    private final World world;
    private final int chunkX;
    private final int chunkZ;

    RegionTickScheduler(final Plugin plugin, final TaskTracker tracker, final World world,
                        final int chunkX, final int chunkZ) {
        super(tracker, "region");
        this.plugin = plugin;
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    @Override
    @Nullable ScheduledTask launchNow(final TaskHandle handle, final @Nullable Runnable retired) {
        return plugin.getServer().getRegionScheduler().run(plugin, world, chunkX, chunkZ, handle::execute);
    }

    @Override
    @Nullable ScheduledTask launchLater(final TaskHandle handle, final long delayTicks,
                                        final @Nullable Runnable retired) {
        return plugin.getServer().getRegionScheduler()
                .runDelayed(plugin, world, chunkX, chunkZ, handle::execute, delayTicks);
    }

    @Override
    @Nullable ScheduledTask launchTimer(final TaskHandle handle, final long delayTicks, final long periodTicks,
                                        final @Nullable Runnable retired) {
        return plugin.getServer().getRegionScheduler()
                .runAtFixedRate(plugin, world, chunkX, chunkZ, handle::execute, delayTicks, periodTicks);
    }
}
