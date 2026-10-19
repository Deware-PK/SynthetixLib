package com.github.dewarepk.synthetixlib.command;

/**
 * A subcommand node, built with the fluent methods of {@link SpecBuilder}; returned by {@link SubCommand#spec()} or
 * passed to {@link SpecBuilder#sub(SubSpec)}. Frozen into immutable nodes when the root is built.
 *
 * <pre>{@code
 * SubSpec.literal("cancel")
 *         .permission("auction.admin")
 *         .argument(lot)
 *         .executes(ctx -> auctions.cancel(ctx.get(lot), ctx.sender()))
 * }</pre>
 */
public final class SubSpec extends SpecBuilder<SubSpec> {

    private SubSpec(final String name) {
        super(name);
    }

    /**
     * @param name the literal, {@code [a-z0-9_-]+}
     * @return a new subcommand builder
     * @throws IllegalArgumentException if {@code name} is not valid
     */
    public static SubSpec literal(final String name) {
        return new SubSpec(name);
    }

    @Override
    SubSpec self() {
        return this;
    }
}
