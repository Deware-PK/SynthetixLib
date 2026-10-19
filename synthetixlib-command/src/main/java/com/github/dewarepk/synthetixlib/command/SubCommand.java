package com.github.dewarepk.synthetixlib.command;

/**
 * One subcommand in its own class: its arguments, modifiers and handler live together. Keep dependencies as fields
 * and {@link Arg} handles as instance fields.
 *
 * <pre>{@code
 * final class BidCommand implements SubCommand {
 *     private final Arg<Long> amount = Arg.of("amount", Args.longRange(1, 1_000_000));
 *
 *     public SubSpec spec() {
 *         return SubSpec.literal("bid").argument(amount).executes(this::run);
 *     }
 *
 *     private void run(CommandCall ctx) {
 *         ctx.reply("<green>Bid " + ctx.get(amount));
 *     }
 * }
 * }</pre>
 */
public interface SubCommand {

    /**
     * Called once, when the parent spec is built. Must not return {@code null}.
     *
     * @return the subcommand node
     */
    SubSpec spec();
}
