package com.github.dewarepk.synthetixlib.core.scheduler;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Folia-safe scheduler facade owned by a {@code SynthetixLib} instance. Wraps Paper's region-aware
 * schedulers (the same code works on Paper and Folia); the legacy {@code BukkitScheduler} is never used.
 *
 * <pre>{@code
 * Scheduler s = lib.scheduler();
 * s.global().runTimer(0, 20, task -> tick());
 * s.region(location).run(() -> block.setType(Material.GOLD_BLOCK));
 * s.entity(player).run(() -> player.sendMessage(msg));
 * s.async().run(() -> saveToDb());
 * }</pre>
 *
 * <p>Every task is tracked by the instance. {@link #close()} (called by {@code SynthetixLib#close()})
 * cancels all pending tasks and makes further scheduling throw {@link IllegalStateException}. A task that
 * is being scheduled concurrently with {@code close()} either throws or ends up cancelled; it never leaks.
 */
public interface Scheduler extends AutoCloseable {

    /**
     * Creates a scheduler for {@code plugin}. Normally obtained from {@code SynthetixLib#scheduler()}.
     *
     * <p>Setting the system property {@code synthetix.scheduler.debug=true} makes task failures also
     * report where the task was scheduled.
     *
     * @param plugin the owning plugin
     * @return a new scheduler
     * @apiNote Internal: use {@code SynthetixLib#scheduler()}
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    static Scheduler create(final Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return new SchedulerImpl(plugin, Boolean.getBoolean("synthetix.scheduler.debug"));
    }

    /**
     * Global region: world-independent game logic such as timers and broadcasts.
     *
     * @return the global-region scheduler
     */
    TickScheduler global();

    /**
     * The region that owns the chunk containing {@code location}. The location is read once; later
     * changes to it do not matter.
     *
     * @param location a location with a world
     * @return a scheduler for that chunk's region
     */
    TickScheduler region(Location location);

    /**
     * The region that owns the given chunk.
     *
     * @param world  the world
     * @param chunkX chunk x coordinate
     * @param chunkZ chunk z coordinate
     * @return a scheduler for that chunk's region
     */
    TickScheduler region(World world, int chunkX, int chunkZ);

    /**
     * Follows {@code entity} across regions; see {@link EntityTickScheduler} for removal behaviour.
     *
     * @param entity the entity
     * @return a scheduler bound to that entity
     */
    EntityTickScheduler entity(Entity entity);

    /**
     * Async scheduler (real time, off the server threads).
     *
     * @return the async scheduler
     */
    AsyncScheduler async();

    /** @return {@code true} if the current thread is the global region tick thread */
    boolean isGlobalThread();

    /**
     * @param location a location
     * @return {@code true} if the current thread owns the region at {@code location}
     */
    boolean owns(Location location);

    /**
     * @param entity an entity
     * @return {@code true} if the current thread owns {@code entity}
     */
    boolean owns(Entity entity);

    /**
     * Cancels every pending task and rejects further scheduling. Idempotent. Owned by
     * {@code SynthetixLib}: call {@code lib.close()} instead of closing the scheduler yourself.
     */
    @Override
    void close();
}
