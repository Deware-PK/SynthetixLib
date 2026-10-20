package com.github.dewarepk.synthetixlib.gui;

import org.jspecify.annotations.Nullable;

/**
 * What the click policy and the input math need to know about an item stack, so they can be written (and tested)
 * without a server. {@link BukkitStacks} is the real one.
 *
 * @param <S> the stack type; values are never changed in place, a changed stack is a new one
 */
interface StackOps<S> {

    /** @return {@code true} for {@code null}, air and a stack of nothing */
    boolean isEmpty(@Nullable S stack);

    /** @return how many items are in the stack; only called for a stack that is not empty */
    int amount(S stack);

    /** @return the most items one stack of this kind holds */
    int maxStack(S stack);

    /** @return {@code true} if the two stacks are the same kind of item and would merge into one stack */
    boolean sameKind(S a, S b);

    /** @return a stack equal to {@code stack} that nobody else holds */
    S copy(S stack);

    /** @return a stack like {@code stack} but with {@code amount} items */
    S withAmount(S stack, int amount);
}
