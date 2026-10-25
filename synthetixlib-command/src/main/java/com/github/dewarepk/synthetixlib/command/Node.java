package com.github.dewarepk.synthetixlib.command;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.Nullable;

/**
 * One built command node, immutable: the only thing executions read. Permission, player-only and cooldown are already
 * resolved against the parent.
 *
 * @param name        the literal
 * @param path        space-separated names from the root, e.g. {@code auction bid}; the key of its cooldowns
 * @param description for Paper (root) and the help
 * @param usage       for the help
 * @param permission  effective permission, {@code null} if none
 * @param permissions every distinct permission on the path from the root to this node, root first; the executor
 *                    requires all of them because an alias redirect skips the {@code requires} of the target
 * @param playerOnly  effective player-only flag
 * @param cooldown    effective cooldown, {@code null} if none
 * @param steps       the arguments after the literal, required ones first
 * @param handler     what runs, {@code null} on pure groups
 * @param async       whether the handler runs on the async scheduler
 * @param children    the subcommands
 */
record Node(String name, String path, @Nullable String description, @Nullable String usage,
            @Nullable String permission, List<String> permissions, boolean playerOnly,
            @Nullable Cooldown cooldown, List<Step> steps, @Nullable CommandHandler handler, boolean async,
            List<Node> children) {

    /**
     * @param arg      the argument
     * @param optional whether the node also runs without it
     * @param fallback the value used when an optional argument is missing
     */
    record Step(Arg<?> arg, boolean optional, @Nullable Object fallback) {
    }

    /**
     * @param duration         how long the node is unavailable after a successful run, asked at every successful
     *                         run; {@link Duration#ZERO} means none this time
     * @param bypassPermission senders with it have no cooldown, {@code null} if none
     */
    record Cooldown(Function<CommandSender, Duration> duration, @Nullable String bypassPermission) {
    }

    Node {
        permissions = List.copyOf(permissions);
        steps = List.copyOf(steps);
        children = List.copyOf(children);
    }

    /** @return how many leading arguments must be typed before the handler may run */
    int requiredCount() {
        int count = 0;
        for (final Step step : steps) {
            if (step.optional()) {
                break;
            }
            count++;
        }
        return count;
    }

    @Nullable Step step(final String argName) {
        for (final Step step : steps) {
            if (step.arg().name().equals(argName)) {
                return step;
            }
        }
        return null;
    }
}
