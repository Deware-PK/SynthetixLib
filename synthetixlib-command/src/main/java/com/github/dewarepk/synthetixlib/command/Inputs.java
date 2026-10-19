package com.github.dewarepk.synthetixlib.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.tree.RootCommandNode;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/** Helpers around the typed input and the placeholders available in every message. */
final class Inputs {

    private Inputs() {
    }

    /** @return the input without the leading slash */
    static String input(final String raw) {
        int start = 0;
        while (start < raw.length() && raw.charAt(start) == '/') {
            start++;
        }
        return raw.substring(start);
    }

    /**
     * The name the player typed for the command, without {@code namespace:}. A context that starts at the dispatcher
     * root parsed that name itself; one that starts elsewhere was reached through a redirect (alias,
     * {@code namespace:name}, {@code /execute ... run}), so the name is the token right before the context's range.
     *
     * @param context the context that is being executed
     * @return the alias actually used
     */
    static String label(final CommandContext<?> context) {
        final String raw = context.getInput();
        final List<? extends ParsedCommandNode<?>> nodes = context.getNodes();
        if (nodes.isEmpty()) {
            return stripNamespace(Inputs.input(raw).split(" ", 2)[0]);
        }
        final StringRange first = nodes.get(0).getRange();
        if (!(context.getRootNode() instanceof RootCommandNode<?>)) {
            int end = first.getStart();
            while (end > 0 && raw.charAt(end - 1) == ' ') {
                end--;
            }
            int start = end;
            while (start > 0 && raw.charAt(start - 1) != ' ') {
                start--;
            }
            if (start < end) {
                return stripNamespace(raw.substring(Math.max(start, leadingSlashes(raw)), end));
            }
        }
        return stripNamespace(first.get(raw));
    }

    private static int leadingSlashes(final String raw) {
        int count = 0;
        while (count < raw.length() && raw.charAt(count) == '/') {
            count++;
        }
        return count;
    }

    private static String stripNamespace(final String token) {
        return token.substring(token.indexOf(':') + 1);
    }

    /** @return {@code <label>} and {@code <sender>} */
    static TagResolver builtins(final String label, final CommandSender sender) {
        return TagResolver.resolver(
                Placeholder.unparsed("label", label),
                Placeholder.unparsed("sender", sender.getName()));
    }
}
