package com.github.dewarepk.synthetixexample.selftest;

import static com.github.dewarepk.synthetixlib.selftest.SelfTest.require;

import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.core.scheduler.Scheduler;
import com.github.dewarepk.synthetixlib.core.scheduler.SynthetixTask;
import com.github.dewarepk.synthetixlib.selftest.SelfTest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.plugin.java.JavaPlugin;

/** Self-tests for {@code core.scheduler}. All waiting is done with futures; the server thread never blocks. */
final class CoreSchedulerSelfTests {

    private CoreSchedulerSelfTests() {
    }

    static List<SelfTest> all(final JavaPlugin plugin, final SynthetixLib lib) {
        final Scheduler scheduler = lib.scheduler();
        return List.of(
                SelfTest.async("core.scheduler.global.run", () -> {
                    final CompletableFuture<Void> done = new CompletableFuture<>();
                    scheduler.global().run(() -> complete(done, () ->
                            require(scheduler.isGlobalThread(), "global task must run on the global thread")));
                    return done;
                }),

                SelfTest.async("core.scheduler.global.later", () -> {
                    final CompletableFuture<Void> done = new CompletableFuture<>();
                    final int delay = 5;
                    final int startTick = plugin.getServer().getCurrentTick();
                    scheduler.global().runLater(delay, () -> complete(done, () -> {
                        final int elapsed = plugin.getServer().getCurrentTick() - startTick;
                        require(elapsed >= delay, "ran after " + elapsed + " ticks, expected >= " + delay);
                    }));
                    return done;
                }),

                SelfTest.async("core.scheduler.global.timer-cancel", () -> {
                    final CompletableFuture<Void> done = new CompletableFuture<>();
                    final AtomicInteger runs = new AtomicInteger();
                    final SynthetixTask timer = scheduler.global().runTimer(0, 1, task -> {
                        if (runs.incrementAndGet() == 3) {
                            task.cancel();
                        }
                    });
                    // Wait well past the 3rd run; a 4th run would show up as runs > 3.
                    scheduler.global().runLater(15, () -> complete(done, () -> {
                        require(runs.get() == 3, "timer ran " + runs.get() + " times, expected exactly 3");
                        require(timer.isCancelled() && timer.isDone(), "timer must be cancelled and done");
                    }));
                    return done;
                }),

                SelfTest.async("core.scheduler.region.run", () -> {
                    final CompletableFuture<Void> done = new CompletableFuture<>();
                    final Location location = spawn(plugin);
                    scheduler.region(location).run(() -> complete(done, () ->
                            require(scheduler.owns(location), "region task must run on the owning thread")));
                    return done;
                }),

                SelfTest.async("core.scheduler.region.later", () -> {
                    final CompletableFuture<Void> done = new CompletableFuture<>();
                    final Location location = spawn(plugin);
                    scheduler.region(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4)
                            .runLater(2, () -> complete(done, () ->
                                    require(scheduler.owns(location), "delayed region task on wrong thread")));
                    return done;
                }),

                SelfTest.async("core.scheduler.entity.run", () -> {
                    final CompletableFuture<Void> done = new CompletableFuture<>();
                    final Location location = spawn(plugin);
                    scheduler.region(location).run(() -> {
                        try {
                            final ArmorStand stand = spawnMarker(location);
                            scheduler.entity(stand).run(() -> complete(done, () -> {
                                require(scheduler.owns(stand), "entity task must run on the entity's thread");
                                stand.remove();
                            }));
                        } catch (final Throwable t) {
                            done.completeExceptionally(t);
                        }
                    });
                    return done;
                }),

                SelfTest.async("core.scheduler.entity.retired", () -> {
                    final CompletableFuture<Void> done = new CompletableFuture<>();
                    final Location location = spawn(plugin);
                    scheduler.region(location).run(() -> {
                        try {
                            final ArmorStand stand = spawnMarker(location);
                            stand.remove();
                            final AtomicBoolean ran = new AtomicBoolean();
                            final AtomicInteger retired = new AtomicInteger();
                            final SynthetixTask task = scheduler.entity(stand)
                                    .run(() -> ran.set(true), retired::incrementAndGet);
                            require(task != null && task.isCancelled(), "task for a removed entity must be cancelled");
                            // Give a wrongly scheduled body or a double-invoked retired callback time to show up.
                            scheduler.global().runLater(5, () -> complete(done, () -> {
                                require(!ran.get(), "task body ran for a removed entity");
                                require(retired.get() == 1, "retired ran " + retired.get() + " times, expected 1");
                            }));
                        } catch (final Throwable t) {
                            done.completeExceptionally(t);
                        }
                    });
                    return done;
                }),

                SelfTest.async("core.scheduler.async.run", () -> {
                    final CompletableFuture<Void> done = new CompletableFuture<>();
                    scheduler.async().run(() -> complete(done, () ->
                            require(!scheduler.isGlobalThread(), "async task must not run on the global thread")));
                    return done;
                }),

                SelfTest.async("core.scheduler.async.later", () -> {
                    final CompletableFuture<Void> done = new CompletableFuture<>();
                    final long start = System.nanoTime();
                    scheduler.async().runLater(Duration.ofMillis(200), () -> complete(done, () -> {
                        final long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
                        require(elapsedMs >= 190, "ran after " + elapsedMs + " ms, expected >= 200");
                    }));
                    return done;
                }),

                SelfTest.async("core.scheduler.async.timer", () -> {
                    final CompletableFuture<Void> done = new CompletableFuture<>();
                    final AtomicInteger runs = new AtomicInteger();
                    scheduler.async().runTimer(Duration.ZERO, Duration.ofMillis(20), task -> {
                        if (runs.incrementAndGet() == 3) {
                            task.cancel();
                            done.complete(null);
                        }
                    });
                    return done;
                }),

                SelfTest.async("core.scheduler.close-cancels", () -> {
                    final CompletableFuture<Void> done = new CompletableFuture<>();
                    // A separate instance: closing it must not affect `lib`.
                    final SynthetixLib other = SynthetixLib.init(plugin);
                    final AtomicInteger ran = new AtomicInteger();
                    final SynthetixTask later = other.scheduler().global().runLater(5, ran::incrementAndGet);
                    final SynthetixTask timer = other.scheduler().global().runTimer(1, 1, t -> ran.incrementAndGet());
                    final SynthetixTask async = other.scheduler().async()
                            .runLater(Duration.ofMillis(100), ran::incrementAndGet);

                    other.close();

                    require(later.isCancelled() && timer.isCancelled() && async.isCancelled(),
                            "close() must cancel every pending task");
                    boolean rejected = false;
                    try {
                        other.scheduler().global().run(ran::incrementAndGet);
                    } catch (final IllegalStateException expected) {
                        rejected = true;
                    }
                    require(rejected, "scheduling after close() must throw IllegalStateException");
                    require(!lib.isClosed(), "closing another instance must not close this one");

                    scheduler.global().runLater(15, () -> complete(done, () ->
                            require(ran.get() == 0, "a task ran " + ran.get() + " time(s) after close()")));
                    return done;
                }),

                SelfTest.async("core.scheduler.exception-isolated", () -> {
                    final CompletableFuture<Void> done = new CompletableFuture<>();
                    final AtomicInteger runs = new AtomicInteger();
                    scheduler.global().runTimer(1, 1, task -> {
                        final int n = runs.incrementAndGet();
                        if (n == 1) {
                            // Expected: shows up in the log as SEVERE, the timer must survive it.
                            throw new IllegalStateException("expected failure from core.scheduler.exception-isolated");
                        }
                        if (n == 3) {
                            task.cancel();
                            done.complete(null);
                        }
                    });
                    return done;
                })
        );
    }

    /** Completes {@code done} with the outcome of {@code check}. */
    private static void complete(final CompletableFuture<Void> done, final SelfTest.Check check) {
        try {
            check.run();
            done.complete(null);
        } catch (final Throwable t) {
            done.completeExceptionally(t);
        }
    }

    private static Location spawn(final JavaPlugin plugin) {
        final World world = plugin.getServer().getWorlds().get(0);
        return world.getSpawnLocation();
    }

    /** Must be called on the thread owning {@code location}. */
    private static ArmorStand spawnMarker(final Location location) {
        return location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setMarker(true);
            stand.setInvisible(true);
            stand.setPersistent(false);
        });
    }
}
