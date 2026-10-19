package com.github.dewarepk.synthetixlib.command;

import com.github.dewarepk.synthetixlib.core.scheduler.Scheduler;
import java.util.function.BooleanSupplier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Where handlers and their follow-up work run: {@code executesAsync} on the async scheduler, {@code runOnSender} on the
 * thread that owns the sender. Does nothing once the service is closed, so a late task never touches a dead plugin.
 */
final class SenderThreads {

    private final Scheduler scheduler;
    private final BooleanSupplier closed;

    SenderThreads(final Scheduler scheduler, final BooleanSupplier closed) {
        this.scheduler = scheduler;
        this.closed = closed;
    }

    /**
     * Runs {@code task} on an async thread.
     *
     * @throws IllegalStateException if the service or the library is closed, so the task will never run (the caller
     *                               holds things, like an in-flight reservation, that it must give back)
     */
    void runAsync(final Runnable task) {
        if (closed.getAsBoolean()) {
            throw new IllegalStateException("CommandService is closed");
        }
        scheduler.async().run(task);
    }

    /**
     * Runs {@code task} on the entity scheduler of a player (dropped silently if the player is gone by then), else on
     * the global one.
     */
    void runOnSender(final CommandSender sender, final Runnable task) {
        if (closed.getAsBoolean()) {
            return;
        }
        try {
            if (sender instanceof Player player) {
                scheduler.entity(player).run(task);
            } else {
                scheduler.global().run(task);
            }
        } catch (final IllegalStateException e) {
            // the library closed between the check and the call: same as closed
        }
    }
}
