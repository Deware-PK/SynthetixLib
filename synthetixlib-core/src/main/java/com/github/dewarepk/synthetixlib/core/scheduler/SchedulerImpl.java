package com.github.dewarepk.synthetixlib.core.scheduler;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/** Default {@link Scheduler}: owns the {@link TaskTracker} and hands out the per-context schedulers. */
final class SchedulerImpl implements Scheduler {

    private final Plugin plugin;
    private final TaskTracker tracker;
    private final GlobalTickScheduler global;
    private final AsyncSchedulerImpl async;

    SchedulerImpl(final Plugin plugin, final boolean debug) {
        this.plugin = plugin;
        this.tracker = new TaskTracker(plugin.getLogger(), debug);
        this.global = new GlobalTickScheduler(plugin, tracker);
        this.async = new AsyncSchedulerImpl(plugin, tracker);
    }

    @Override
    public TickScheduler global() {
        return global;
    }

    @Override
    public TickScheduler region(final Location location) {
        Objects.requireNonNull(location, "location");
        final World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("location has no world");
        }
        return new RegionTickScheduler(plugin, tracker, world, location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    @Override
    public TickScheduler region(final World world, final int chunkX, final int chunkZ) {
        Objects.requireNonNull(world, "world");
        return new RegionTickScheduler(plugin, tracker, world, chunkX, chunkZ);
    }

    @Override
    public EntityTickScheduler entity(final Entity entity) {
        Objects.requireNonNull(entity, "entity");
        return new EntityTickSchedulerImpl(plugin, tracker, entity);
    }

    @Override
    public AsyncScheduler async() {
        return async;
    }

    @Override
    public boolean isGlobalThread() {
        return plugin.getServer().isGlobalTickThread();
    }

    @Override
    public boolean owns(final Location location) {
        return plugin.getServer().isOwnedByCurrentRegion(Objects.requireNonNull(location, "location"));
    }

    @Override
    public boolean owns(final Entity entity) {
        return plugin.getServer().isOwnedByCurrentRegion(Objects.requireNonNull(entity, "entity"));
    }

    @Override
    public void close() {
        tracker.close();
    }
}
