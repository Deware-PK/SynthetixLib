package com.github.dewarepk.synthetixlib.command;

import com.github.dewarepk.synthetixlib.config.codec.Durations;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Duration;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Runs the handler of one node. Holds only the immutable node and the host, so any number of threads can execute it
 * at the same time; everything per call lives in {@link CommandCallImpl}.
 */
final class Executor implements Command<CommandSourceStack> {

    private final Node node;
    private final CommandHost host;

    Executor(final Node node, final CommandHost host) {
        this.node = node;
        this.host = host;
    }

    @Override
    public int run(final CommandContext<CommandSourceStack> context) {
        if (host.isClosed()) {
            return 0;
        }
        final CommandSender sender = context.getSource().getSender();
        for (final String permission : node.permissions()) {
            if (!sender.hasPermission(permission)) {
                // Brigadier hides the node already; this covers paths that skip the check (alias redirects skip
                // the root's, so every permission down the path is required here).
                return 0;
            }
        }
        final String input = Inputs.input(context.getInput());
        final String label = Inputs.label(context);
        if (node.playerOnly() && !(sender instanceof Player)) {
            send(sender, host.render(sender, CommandMessages::playerOnly, Inputs.builtins(label, sender)));
            return 0;
        }

        final CommandCallImpl call = new CommandCallImpl(host, context, node, label, input);
        final Duration left = call.cooldownLeft();
        if (!left.isZero()) {
            send(sender, host.render(sender, CommandMessages::cooldown, TagResolver.resolver(
                    Inputs.builtins(label, sender), Placeholder.unparsed("time", formatWait(left)))));
            return 0;
        }

        if (node.async()) {
            return runAsync(call, sender, input, label);
        }
        if (!invoke(call, sender, input, label)) {
            return 0;
        }
        call.finished();
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Queues the handler of an {@code executesAsync} node. One run per player and node at a time: the reservation is
     * taken before anything else is done and given back on every path, after the cooldown has started.
     */
    private int runAsync(final CommandCallImpl call, final CommandSender sender, final String input,
                         final String label) {
        final UUID player = sender instanceof Player p ? p.getUniqueId() : null;
        Object token = null;   // console and command blocks have no UUID: no guard
        if (player != null) {
            token = host.cooldowns().reserve(player, node.path());
            if (token == null) {
                send(sender, host.render(sender, CommandMessages::busy, Inputs.builtins(label, sender)));
                return 0;
            }
        }
        final Object reservation = token;
        try {
            // Arguments are read here, on the command thread: selectors and positions must not be resolved off it.
            call.readAll();
        } catch (final CommandFailure failure) {
            release(player, reservation);
            send(sender, failure.message());
            return 0;
        } catch (final RuntimeException e) {
            release(player, reservation);
            fail(sender, input, label, e);
            return 0;
        }
        call.markAsync();
        try {
            host.runAsync(() -> {
                try {
                    if (invoke(call, sender, input, label)) {
                        // cooldown first, release second: in between a second run meets the cooldown or the guard
                        call.finished();
                    }
                } finally {
                    release(player, reservation);
                }
            });
        } catch (final IllegalStateException closed) {
            // the library closed between the check and the queueing: nothing will run, say nothing
            release(player, reservation);
            host.logger().log(Level.FINE, "Command \"/" + input + "\" was not queued", closed);
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private void release(final @Nullable UUID player, final @Nullable Object reservation) {
        if (player != null && reservation != null) {
            host.cooldowns().release(player, node.path(), reservation);
        }
    }

    /** @return whether the handler returned normally */
    private boolean invoke(final CommandCallImpl call, final CommandSender sender, final String input,
                           final String label) {
        try {
            node.handler().handle(call);
            return true;
        } catch (final CommandFailure failure) {
            send(sender, failure.message());
            return false;
        } catch (final Exception e) {
            fail(sender, input, label, e);
            return false;
        }
    }

    private void fail(final CommandSender sender, final String input, final String label, final Exception e) {
        host.logger().log(Level.SEVERE, "Command \"/" + input + "\" failed for " + sender.getName(), e);
        send(sender, host.render(sender, CommandMessages::error, Inputs.builtins(label, sender)));
    }

    /** The wait in the config duration format, rounded up to whole seconds. */
    static String formatWait(final Duration left) {
        final long seconds = left.getSeconds() + (left.getNano() > 0 ? 1 : 0);
        return Durations.format(Duration.ofSeconds(seconds));
    }

    private static void send(final CommandSender sender, final Component message) {
        if (!Component.empty().equals(message)) {
            sender.sendMessage(message);
        }
    }
}
