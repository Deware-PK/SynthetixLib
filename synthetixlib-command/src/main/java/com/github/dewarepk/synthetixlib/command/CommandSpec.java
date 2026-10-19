package com.github.dewarepk.synthetixlib.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A root command, immutable and without per-call state: one spec can run on any region thread at the same time.
 * Built with {@link #literal}, registered with {@link CommandService#register}.
 *
 * <pre>{@code
 * CommandSpec auction = CommandSpec.literal("auction")
 *         .aliases("ah", "auc")
 *         .description("Auction house")
 *         .permission("auction.use")
 *         .playerOnly()
 *         .executes(ctx -> gui.open(ctx.player()))
 *         .sub(new BidCommand(auctions))
 *         .build();
 * }</pre>
 */
public final class CommandSpec {

    private final Node root;
    private final List<String> aliases;

    private CommandSpec(final Node root, final List<String> aliases) {
        this.root = root;
        this.aliases = List.copyOf(aliases);
    }

    /**
     * @param name the command, {@code [a-z0-9_-]+}
     * @return a new builder
     * @throws IllegalArgumentException if {@code name} is not valid
     */
    public static Builder literal(final String name) {
        return new Builder(name);
    }

    /** @return the command name */
    public String name() {
        return root.name();
    }

    /** @return the other names it answers to */
    public List<String> aliases() {
        return aliases;
    }

    /** @return the description shown by Paper's help, or {@code null} */
    public @Nullable String description() {
        return root.description();
    }

    /** @return a fresh Brigadier node; called again on every COMMANDS event */
    LiteralCommandNode<CommandSourceStack> compile(final CommandHost host) {
        return TreeCompiler.compile(root, host);
    }

    /** Builder of the root: the shared methods of {@link SpecBuilder} plus aliases. */
    public static final class Builder extends SpecBuilder<Builder> {

        private final List<String> aliases = new ArrayList<>();

        private Builder(final String name) {
            super(name);
        }

        @Override
        Builder self() {
            return this;
        }

        /**
         * @param aliases more names for the command, each {@code [a-z0-9_-]+}
         * @return this builder
         */
        public Builder aliases(final String... aliases) {
            for (final String alias : aliases) {
                this.aliases.add(Names.check(alias, "alias"));
            }
            return this;
        }

        /**
         * @return the immutable spec
         * @throws IllegalArgumentException if a node is not usable, e.g. duplicate subcommand names
         */
        public CommandSpec build() {
            final Node root = toNode(null, List.of(), false, null, false, "");
            final Set<String> seen = new HashSet<>();
            seen.add(root.name());
            for (final String alias : aliases) {
                if (!seen.add(Objects.requireNonNull(alias))) {
                    throw new IllegalArgumentException("/" + root.name() + ": alias \"" + alias + "\" is used twice");
                }
            }
            return new CommandSpec(root, aliases);
        }
    }
}
