package com.github.dewarepk.synthetixlib.gui;

import static com.github.dewarepk.synthetixlib.gui.FakeStacks.OPS;
import static com.github.dewarepk.synthetixlib.gui.FakeStacks.diamond;
import static com.github.dewarepk.synthetixlib.gui.FakeStacks.dirt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.gui.FakeStacks.Stack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Shift-click distribution, draining and handing back: the numbers must always add up. */
class InputsTest {

    private static List<@Nullable Stack> contents(final Stack... stacks) {
        return new ArrayList<>(Arrays.asList(stacks));
    }

    private static long total(final List<@Nullable Stack> contents) {
        return Inputs.count(OPS, contents);
    }

    // ------------------------------------------------------------------------------------------ distribute

    @Test
    void aShiftClickFillsStacksThatAreThereBeforeEmptySlots() {
        final SlotMap<Stack> menu = FakeStacks.menu("#aaa#", 8);
        final List<@Nullable Stack> items = contents(null, null, diamond(5), null, null);

        final Inputs.Distribution<Stack> result = Inputs.distribute(menu, OPS, items, diamond(10));

        assertEquals(diamond(8), items.get(2));        // 5 + 3
        assertEquals(diamond(7), items.get(1));        // the other 7, in the first empty slot
        assertNull(items.get(3));
        assertNull(result.remaining());
        assertEquals(Set.of(1, 2), result.changed());
    }

    @Test
    void whatDoesNotFitStaysInTheSourceAndNothingIsLost() {
        final SlotMap<Stack> menu = FakeStacks.menu("#aa#", 8);
        final List<@Nullable Stack> items = contents(null, diamond(6), null, null);

        final Inputs.Distribution<Stack> result = Inputs.distribute(menu, OPS, items, diamond(20));

        assertEquals(diamond(8), items.get(1)); // 6 + 2
        assertEquals(diamond(8), items.get(2));
        assertEquals(diamond(10), result.remaining()); // 20 - 2 - 8
        assertNull(items.get(3));
    }

    @Test
    void remainingPlusMovedEqualsWhatWasMovedForEveryAmount() {
        final SlotMap<Stack> menu = FakeStacks.menu("#aab#", 8);
        for (int existing = 0; existing <= 8; existing++) {
            for (int moving = 1; moving <= 64; moving++) {
                final List<@Nullable Stack> items = contents(null, existing == 0 ? null : diamond(existing), null, null,
                        null);
                final long before = total(items);

                final Inputs.Distribution<Stack> result = Inputs.distribute(menu, OPS, items, diamond(moving));

                final long after = total(items);
                final long left = result.remaining() == null ? 0 : result.remaining().amount();
                assertEquals(moving, (after - before) + left, "existing " + existing + ", moving " + moving);
                for (final Stack stack : items) {
                    assertTrue(stack == null || stack.amount() <= 8, "never above max-amount: " + items);
                }
            }
        }
    }

    @Test
    void aShiftClickNeverGoesIntoALockedSlotOrToAnItemTheSlotRefuses() {
        final SlotMap<Stack> menu = FakeStacks.menu("#a#", 8);
        final List<@Nullable Stack> items = contents(null, null, null);

        final Inputs.Distribution<Stack> dirtResult = Inputs.distribute(menu, OPS, items, dirt(5));

        assertEquals(dirt(5), dirtResult.remaining());
        assertEquals(Set.of(), dirtResult.changed());
        assertEquals(contents(null, null, null), items);

        final Inputs.Distribution<Stack> diamondResult = Inputs.distribute(menu, OPS, items, diamond(30));
        assertNull(items.get(0));
        assertNull(items.get(2));
        assertEquals(diamond(8), items.get(1));
        assertEquals(diamond(22), diamondResult.remaining());
    }

    @Test
    void aShiftClickDoesNotMergeOntoADifferentKind() {
        final SlotMap<Stack> menu = FakeStacks.menu("12", 8); // both take anything
        final List<@Nullable Stack> items = contents(dirt(10), null);

        final Inputs.Distribution<Stack> result = Inputs.distribute(menu, OPS, items, diamond(4));

        assertEquals(dirt(10), items.get(0));
        assertEquals(diamond(4), items.get(1));
        assertNull(result.remaining());
    }

    @Test
    void aShiftClickWithNoInputSlotMovesNothing() {
        final SlotMap<Stack> menu = FakeStacks.menu("###", 8);
        final List<@Nullable Stack> items = contents(null, null, null);

        final Inputs.Distribution<Stack> result = Inputs.distribute(menu, OPS, items, diamond(3));

        assertEquals(diamond(3), result.remaining());
        assertEquals(contents(null, null, null), items);
    }

    @Test
    void movingNothingChangesNothing() {
        final SlotMap<Stack> menu = FakeStacks.menu("a", 8);
        final List<@Nullable Stack> items = contents((Stack) null);

        assertNull(Inputs.distribute(menu, OPS, items, diamond(0)).remaining());
        assertNull(Inputs.distribute(menu, OPS, items, null).remaining());
    }

    // ------------------------------------------------------------------------------------------ drain, read

    @Test
    void takeInputsHandsOutTheItemsOnceAndASecondCallFindsNothing() {
        final SlotMap<Stack> menu = FakeStacks.menu("#aa#", 64);
        final List<@Nullable Stack> items = contents(null, diamond(5), diamond(7), null);

        final List<Stack> first = Inputs.drain(OPS, items, menu.inputSlots());
        final List<Stack> second = Inputs.drain(OPS, items, menu.inputSlots());

        assertEquals(List.of(diamond(5), diamond(7)), first);
        assertEquals(List.of(), second);
        assertEquals(0, total(items));
    }

    @Test
    void drainTakesOnlyTheSlotsItIsGivenAndNeverALockedOne() {
        final SlotMap<Stack> menu = FakeStacks.menu("#ab#", 64);
        final List<@Nullable Stack> items = contents(dirt(1), diamond(5), diamond(7), dirt(2));

        final List<Stack> taken = Inputs.drain(OPS, items, menu.slotsOf('a'));

        assertEquals(List.of(diamond(5)), taken);
        assertEquals(contents(dirt(1), null, diamond(7), dirt(2)), items);
    }

    @Test
    void readCopiesAndLeavesTheContents() {
        final SlotMap<Stack> menu = FakeStacks.menu("aa", 64);
        final List<@Nullable Stack> items = contents(diamond(5), null);

        assertEquals(List.of(diamond(5)), Inputs.read(OPS, items, menu.inputSlots()));
        assertEquals(contents(diamond(5), null), items);
    }

    // ------------------------------------------------------------------------------------------ hand back

    /** A player's inventory with room for a fixed number of items. */
    private static final class Bag {
        int room;
        int held;
        int dropped;

        Bag(final int room) {
            this.room = room;
        }

        @Nullable Stack add(final Stack stack) {
            final int fits = Math.min(room, stack.amount());
            room -= fits;
            held += fits;
            return fits == stack.amount() ? null : OPS.withAmount(stack, stack.amount() - fits);
        }

        void drop(final Stack stack) {
            dropped += stack.amount();
        }
    }

    @Test
    void handingBackPutsWhatFitsInTheInventoryAndDropsTheRestNothingLostNothingDoubled() {
        for (int room = 0; room <= 20; room++) {
            final Bag bag = new Bag(room);
            final List<Stack> items = List.of(diamond(5), dirt(7), diamond(3));

            Inputs.handBack(OPS, items, bag::add, bag::drop);

            assertEquals(15, bag.held + bag.dropped, "room " + room);
            assertEquals(Math.min(room, 15), bag.held, "room " + room);
        }
    }

    @Test
    void handingBackAnEmptyListDoesNothing() {
        final Bag bag = new Bag(10);

        Inputs.handBack(OPS, List.of(), bag::add, bag::drop);

        assertEquals(0, bag.held + bag.dropped);
    }

    @Test
    void closingTwiceReturnsTheItemsOnceBecauseTheSecondDrainIsEmpty() {
        final SlotMap<Stack> menu = FakeStacks.menu("#aa#", 64);
        final List<@Nullable Stack> items = contents(null, diamond(5), diamond(7), null);
        final Bag bag = new Bag(100);

        for (int close = 0; close < 3; close++) {
            Inputs.handBack(OPS, Inputs.drain(OPS, items, menu.inputSlots()), bag::add, bag::drop);
        }

        assertEquals(12, bag.held + bag.dropped);
    }
}
