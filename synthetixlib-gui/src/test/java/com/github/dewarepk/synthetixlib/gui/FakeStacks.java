package com.github.dewarepk.synthetixlib.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/** Item stacks that need no server: a kind, an amount and the most one stack holds. */
final class FakeStacks implements StackOps<FakeStacks.Stack> {

    record Stack(String kind, int amount, int max) {

        Stack(final String kind, final int amount) {
            this(kind, amount, 64);
        }
    }

    static final FakeStacks OPS = new FakeStacks();

    private FakeStacks() {
    }

    static Stack diamond(final int amount) {
        return new Stack("diamond", amount);
    }

    static Stack dirt(final int amount) {
        return new Stack("dirt", amount);
    }

    @Override
    public boolean isEmpty(final @Nullable Stack stack) {
        return stack == null || stack.amount() <= 0;
    }

    @Override
    public int amount(final Stack stack) {
        return stack.amount();
    }

    @Override
    public int maxStack(final Stack stack) {
        return stack.max();
    }

    @Override
    public boolean sameKind(final Stack a, final Stack b) {
        return a.kind().equals(b.kind());
    }

    @Override
    public Stack copy(final Stack stack) {
        return new Stack(stack.kind(), stack.amount(), stack.max());
    }

    @Override
    public Stack withAmount(final Stack stack, final int amount) {
        return new Stack(stack.kind(), amount, stack.max());
    }

    /**
     * A menu from a picture: {@code '#'} is a locked slot, a lower case letter an input slot that takes only diamonds up to
     * {@code max} and a digit an input slot that takes anything up to 64.
     */
    static SlotMap<Stack> menu(final String picture, final int max) {
        final List<SlotMap.@Nullable Input<Stack>> slots = new ArrayList<>();
        final Predicate<Stack> diamonds = stack -> stack.kind().equals("diamond");
        for (final char c : picture.toCharArray()) {
            if (c == '#') {
                slots.add(null);
            } else if (Character.isDigit(c)) {
                slots.add(new SlotMap.Input<>(c, stack -> true, 64));
            } else {
                slots.add(new SlotMap.Input<>(c, diamonds, max));
            }
        }
        return new SlotMap<>(slots);
    }
}
