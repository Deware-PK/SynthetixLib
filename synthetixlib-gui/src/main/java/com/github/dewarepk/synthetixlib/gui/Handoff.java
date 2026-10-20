package com.github.dewarepk.synthetixlib.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Re-opening a menu with a new inventory (the title or the shape changed) without losing or doubling a thing: the items
 * of the input slots move to the same slots of the new inventory, what has no place there is handed back by the caller,
 * and the item on the cursor is taken off the cursor before the old inventory closes (the server would put it in the
 * player's inventory) and put back after the new one opened.
 */
final class Handoff {

    /**
     * @param opened   {@code false} if the new inventory could not be opened; then nothing moved and the old inventory is
     *                 still the one the player sees
     * @param overflow items of input slots that have no slot in the new inventory, to hand back; empty unless opened
     */
    record Result<S>(boolean opened, List<S> overflow) {
    }

    private Handoff() {
    }

    /**
     * @param from       slots of the old inventory
     * @param fromContents the old inventory's contents; read only, so a failed re-open leaves the old inventory as it was
     * @param to         slots of the new inventory
     * @param toContents the new inventory's contents; the input items are put in before {@code open} is called, the caller
     *                   writes the list to the new inventory inside {@code open}
     * @param takeCursor removes the item from the cursor and returns it, {@code null} if there is none
     * @param setCursor  puts an item on the cursor; called with what {@code takeCursor} returned
     * @param open       opens the new inventory, {@code false} if it did not open
     */
    static <S> Result<S> reopen(final StackOps<S> ops, final SlotMap<S> from, final List<@Nullable S> fromContents,
                                final SlotMap<S> to, final List<@Nullable S> toContents,
                                final Supplier<@Nullable S> takeCursor, final Consumer<@Nullable S> setCursor,
                                final BooleanSupplier open) {
        final List<S> overflow = new ArrayList<>();
        final Set<Character> chars = new LinkedHashSet<>(from.chars());
        for (final char c : chars) {
            final int[] fromSlots = from.slotsOf(c);
            final int[] toSlots = to.slotsOf(c);
            for (int i = 0; i < fromSlots.length; i++) {
                final S stack = fromContents.get(fromSlots[i]);
                if (ops.isEmpty(stack)) {
                    continue;
                }
                final SlotMap.Input<S> rule = i < toSlots.length ? to.input(toSlots[i]) : null;
                if (rule == null || !rule.accepts().test(stack)) {
                    overflow.add(ops.copy(stack));
                    continue;
                }
                final int fits = Math.min(ops.amount(stack), Math.min(rule.maxAmount(), ops.maxStack(stack)));
                toContents.set(toSlots[i], ops.withAmount(stack, fits));
                if (fits < ops.amount(stack)) {
                    overflow.add(ops.withAmount(stack, ops.amount(stack) - fits));
                }
            }
        }
        final S held = takeCursor.get();
        final boolean opened = open.getAsBoolean();
        setCursor.accept(held); // on the new inventory, or on the old one if it could not be opened
        return opened ? new Result<>(true, overflow) : new Result<>(false, List.of());
    }
}
