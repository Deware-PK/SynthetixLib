package com.github.dewarepk.synthetixlib.command;

import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

/** The handler behind {@code helpSub()}: lists the subcommands of one node that the sender may use. */
final class Help implements CommandHandler {

    private final String path;
    private final List<Node> children;

    /**
     * @param path     space-separated names from the root down to the node that is explained
     * @param children the subcommands of that node, the help node itself included (it is skipped)
     */
    Help(final String path, final List<Node> children) {
        this.path = path;
        this.children = children;
    }

    @Override
    public void handle(final CommandCall call) {
        final CommandCallImpl impl = (CommandCallImpl) call;
        impl.replyBuiltIn(CommandMessages::helpHeader, Placeholder.unparsed("command", command(call.label(), path)));
        final CommandSender sender = call.sender();
        for (final Node child : children) {
            if (child.handler() == this || !allowed(sender, child)) {
                continue;
            }
            impl.replyBuiltIn(CommandMessages::helpLine,
                    Placeholder.unparsed("command", command(call.label(), child.path())),
                    Placeholder.unparsed("usage", child.usage() == null ? "" : child.usage()),
                    Placeholder.unparsed("description", child.description() == null ? "" : child.description()));
        }
    }

    private static boolean allowed(final CommandSender sender, final Node node) {
        for (final String permission : node.permissions()) {
            if (!sender.hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }

    /** The path with the alias actually typed in place of the root name. */
    private static String command(final String label, final String path) {
        final int space = path.indexOf(' ');
        return space < 0 ? label : label + path.substring(space);
    }
}
