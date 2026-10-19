package com.github.dewarepk.synthetixlib.command;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.Nullable;

/**
 * The {@link CommandSyntaxException}s our converters throw: the message comes from {@link CommandMessages} in the
 * language of the sender, so Brigadier shows it red under the input. Without a host (an {@link ArgType} used outside
 * a {@link CommandService}) the exception carries the plain reason or input instead.
 */
final class ArgErrors {

    private ArgErrors() {
    }

    /**
     * An argument that could not be converted: {@link CommandMessages#invalidValue()}.
     *
     * @param host   the surroundings, {@code null} outside a service
     * @param sender who typed it, {@code null} if unknown (the default language is used)
     * @param input  the text that was typed
     * @param reason why it is invalid
     */
    static CommandSyntaxException invalid(final @Nullable CommandHost host, final @Nullable CommandSender sender,
                                          final String input, final String reason) {
        if (host == null) {
            return plain(reason);
        }
        final TagResolver resolvers = TagResolver.resolver(
                sender(sender),
                Placeholder.unparsed("input", input),
                Placeholder.unparsed("reason", reason));
        return new SimpleCommandExceptionType(
                host.toMessage(host.render(sender, CommandMessages::invalidValue, resolvers))).create();
    }

    /**
     * A player that does not exist: {@link CommandMessages#playerNotFound()}.
     *
     * @param label the alias typed, {@code null} while parsing (it is not known yet)
     */
    static CommandSyntaxException playerNotFound(final @Nullable CommandHost host,
                                                 final @Nullable CommandSender sender,
                                                 final @Nullable String label, final String input) {
        if (host == null) {
            return plain(input);
        }
        final TagResolver resolvers = TagResolver.resolver(
                sender(sender),
                label == null ? TagResolver.empty() : Placeholder.unparsed("label", label),
                Placeholder.unparsed("input", input));
        return new SimpleCommandExceptionType(
                host.toMessage(host.render(sender, CommandMessages::playerNotFound, resolvers))).create();
    }

    private static TagResolver sender(final @Nullable CommandSender sender) {
        return sender == null ? TagResolver.empty() : Placeholder.unparsed("sender", sender.getName());
    }

    private static CommandSyntaxException plain(final String text) {
        return new SimpleCommandExceptionType(new LiteralMessage(text)).create();
    }
}
