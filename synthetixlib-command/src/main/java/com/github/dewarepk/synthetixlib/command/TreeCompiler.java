package com.github.dewarepk.synthetixlib.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Collection;
import java.util.Locale;
import java.util.function.Function;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/** Turns the immutable {@link Node} tree into fresh Brigadier nodes. Pure: no state, called on every COMMANDS event. */
final class TreeCompiler {

    private TreeCompiler() {
    }

    static LiteralCommandNode<CommandSourceStack> compile(final Node root, final CommandHost host) {
        return literal(root, host).build();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> literal(final Node node, final CommandHost host) {
        final LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal(node.name());
        final String permission = node.permission();
        if (permission != null) {
            literal.requires(source -> source.getSender().hasPermission(permission));
        }
        final Executor executor = node.handler() == null ? null : new Executor(node, host);
        final int required = node.requiredCount();

        // Arguments chain behind the literal; the handler sits on every node where all required ones are typed.
        ArgumentBuilder<CommandSourceStack, ?> tail = null;
        for (int i = node.steps().size() - 1; i >= 0; i--) {
            final ArgumentBuilder<CommandSourceStack, ?> argument = argument(node.steps().get(i).arg(), host);
            if (executor != null && i + 1 >= required) {
                argument.executes(executor);
            }
            if (tail != null) {
                argument.then(tail);
            }
            tail = argument;
        }
        if (executor != null && required == 0) {
            literal.executes(executor);
        }
        if (tail != null) {
            literal.then(tail);
        }
        for (final Node child : node.children()) {
            literal.then(literal(child, host));
        }
        return literal;
    }

    private static <T> RequiredArgumentBuilder<CommandSourceStack, T> typed(final String name,
                                                                          final ArgumentType<T> type) {
        return Commands.argument(name, type);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ?> argument(final Arg<?> arg, final CommandHost host) {
        final RequiredArgumentBuilder<CommandSourceStack, ?> builder = typed(arg.name(),
                ArgTypeImpl.nativeOf(arg.type(), host));
        final Function<CommandSourceStack, ? extends Collection<String>> suggestions = arg.effectiveSuggestions();
        if (suggestions != null) {
            builder.suggests(provider(suggestions, host));
        }
        return builder;
    }

    private static SuggestionProvider<CommandSourceStack> provider(
            final Function<CommandSourceStack, ? extends Collection<String>> suggestions, final CommandHost host) {
        return (context, builder) -> {
            if (!host.isClosed()) {
                final String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
                try {
                    for (final String candidate : suggestions.apply(context.getSource())) {
                        if (candidate.toLowerCase(Locale.ROOT).startsWith(typed)) {
                            builder.suggest(candidate);
                        }
                    }
                } catch (final RuntimeException e) {
                    host.logger().log(Level.WARNING, "Suggestions failed for /" + context.getInput(), e);
                }
            }
            return builder.buildFuture();
        };
    }
}
