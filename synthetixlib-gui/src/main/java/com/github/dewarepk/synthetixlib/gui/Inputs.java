package com.github.dewarepk.synthetixlib.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * The arithmetic on the contents of input slots, written on lists of stacks so it needs no server. A list holds one entry
 * per slot of the top inventory ({@code null} for an empty slot). Nothing here ever creates or loses an item: what a
 * method takes out of a list it returns, and what it puts in comes out of what it was given.
 */
final class Inputs {

    /**
     * The result of {@link #distribute}.
     *
     * @param remaining what did not fit, {@code null} if everything was moved
     * @param changed   the slots that now hold more than before
     */
    record Distribution<S>(@Nullable S remaining, Set<Integer> changed) {
    }

    private Inputs() {
    }

    /**
     * Shift-click: moves as much of {@code moving} as fits into input slots, the way the server does for a chest (first
     * onto stacks of the same kind, then into empty slots) but never into a locked slot, never an item the slot does
     * not accept and never beyond {@code max-amount}. {@code contents} is changed in place.
     *
     * @param moving the stack that is moved; not changed
     * @return what is left of it and which slots changed
     */
    static <S> Distribution<S> distribute(final SlotMap<S> slots, final StackOps<S> ops, final List<@Nullable S> contents,
                                          final S moving) {
        final Set<Integer> changed = new LinkedHashSet<>();
        int left = ops.isEmpty(moving) ? 0 : ops.amount(moving);
        if (left == 0) {
            return new Distribution<>(null, changed);
        }
        final int total = left;
        final int[] inputSlots = slots.inputSlots();
        for (final int slot : inputSlots) { // onto stacks that are there already
            final SlotMap.Input<S> input = slots.input(slot);
            final S there = contents.get(slot);
            if (left == 0 || ops.isEmpty(there) || !input.accepts().test(moving) || !ops.sameKind(there, moving)) {
                continue;
            }
            final int room = Math.min(input.maxAmount(), ops.maxStack(there)) - ops.amount(there);
            final int put = Math.min(Math.max(room, 0), left);
            if (put > 0) {
                contents.set(slot, ops.withAmount(there, ops.amount(there) + put));
                changed.add(slot);
                left -= put;
            }
        }
        for (final int slot : inputSlots) { // then into empty slots
            final SlotMap.Input<S> input = slots.input(slot);
            if (left == 0) {
                break;
            }
            if (!ops.isEmpty(contents.get(slot)) || !input.accepts().test(moving)) {
                continue;
            }
            final int put = Math.min(Math.min(input.maxAmount(), ops.maxStack(moving)), left);
            contents.set(slot, ops.withAmount(moving, put));
            changed.add(slot);
            left -= put;
        }
        if (left > total) {
            throw new IllegalStateException("moved a negative amount"); // cannot happen; keeps the invariant visible
        }
        return new Distribution<>(left == 0 ? null : ops.withAmount(moving, left), changed);
    }

    /**
     * Empties the given slots of {@code contents} and returns what was in them. Calling it again returns nothing, so what
     * was taken is handed out exactly once.
     *
     * @param slotsToEmpty the slots to take, in the order the items are returned
     * @return copies of the non-empty stacks that were taken, in the order of {@code slotsToEmpty}
     */
    static <S> List<S> drain(final StackOps<S> ops, final List<@Nullable S> contents, final int[] slotsToEmpty) {
        final List<S> taken = new ArrayList<>();
        for (final int slot : slotsToEmpty) {
            final S stack = contents.get(slot);
            if (!ops.isEmpty(stack)) {
                taken.add(ops.copy(stack));
            }
            contents.set(slot, null);
        }
        return taken;
    }

    /** @return copies of the non-empty stacks in the given slots, in order; {@code contents} is not changed */
    static <S> List<S> read(final StackOps<S> ops, final List<@Nullable S> contents, final int[] slotsToRead) {
        final List<S> found = new ArrayList<>();
        for (final int slot : slotsToRead) {
            final S stack = contents.get(slot);
            if (!ops.isEmpty(stack)) {
                found.add(ops.copy(stack));
            }
        }
        return found;
    }

    /**
     * Gives items back to a player: each goes to {@code add}, which returns what did not fit, and that is dropped.
     *
     * @param add  puts a stack into the player's inventory and returns the part that did not fit, {@code null} or empty if
     *             all of it did
     * @param drop drops a stack at the player
     */
    static <S> void handBack(final StackOps<S> ops, final List<S> items, final UnaryOperator<S> add,
                             final Consumer<S> drop) {
        for (final S item : items) {
            if (ops.isEmpty(item)) {
                continue;
            }
            final S rest = add.apply(ops.copy(item));
            if (!ops.isEmpty(rest)) {
                drop.accept(rest);
            }
        }
    }

    /** @return the sum of the amounts of the non-empty stacks */
    static <S> long count(final StackOps<S> ops, final Iterable<@Nullable S> stacks) {
        long sum = 0;
        for (final S stack : stacks) {
            if (!ops.isEmpty(stack)) {
                sum += ops.amount(stack);
            }
        }
        return sum;
    }
}
