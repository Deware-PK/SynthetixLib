package com.github.dewarepk.synthetixlib.gui;

import static com.github.dewarepk.synthetixlib.gui.FakeStacks.OPS;
import static com.github.dewarepk.synthetixlib.gui.FakeStacks.diamond;
import static com.github.dewarepk.synthetixlib.gui.FakeStacks.dirt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.gui.FakeStacks.Stack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Re-opening a menu (title or shape changed): input items and the cursor must come out exactly as they went in. */
class HandoffTest {

    private static List<@Nullable Stack> contents(final int size, final Object... slotsAndStacks) {
        final List<@Nullable Stack> list = new ArrayList<>(Arrays.asList(new Stack[size]));
        for (int i = 0; i < slotsAndStacks.length; i += 2) {
            list.set((Integer) slotsAndStacks[i], (Stack) slotsAndStacks[i + 1]);
        }
        return list;
    }

    private static long total(final List<@Nullable Stack> contents) {
        return Inputs.count(OPS, contents);
    }

    private static long total(final List<Stack> overflow, final AtomicReference<@Nullable Stack> cursor) {
        return Inputs.count(OPS, overflow) + Inputs.count(OPS, java.util.Collections.singletonList(cursor.get()));
    }

    @Test
    void inputItemsAndTheCursorSurviveAReopenWithTheSameShape() {
        final SlotMap<Stack> menu = FakeStacks.menu("#aa#b", 64);
        final List<@Nullable Stack> old = contents(5, 1, diamond(5), 2, diamond(7), 4, diamond(9));
        final List<@Nullable Stack> next = contents(5);
        final AtomicReference<@Nullable Stack> cursor = new AtomicReference<>(dirt(13));
        final long before = total(old) + 13;

        final Handoff.Result<Stack> result = Handoff.reopen(OPS, menu, old, menu, next,
                () -> cursor.getAndSet(null), cursor::set, () -> true);

        assertTrue(result.opened());
        assertEquals(List.of(), result.overflow());
        assertEquals(contents(5, 1, diamond(5), 2, diamond(7), 4, diamond(9)), next);
        assertEquals(dirt(13), cursor.get());
        assertEquals(before, total(next) + total(result.overflow(), cursor) );
    }

    @Test
    void theCursorIsOffTheCursorWhileTheOldInventoryCloses() {
        final SlotMap<Stack> menu = FakeStacks.menu("a", 64);
        final AtomicReference<@Nullable Stack> cursor = new AtomicReference<>(dirt(13));
        final AtomicReference<@Nullable Stack> seenWhileOpening = new AtomicReference<>(dirt(-1));

        Handoff.reopen(OPS, menu, contents(1), menu, contents(1), () -> cursor.getAndSet(null), cursor::set, () -> {
            seenWhileOpening.set(cursor.get());
            return true;
        });

        assertEquals(null, seenWhileOpening.get(), "the server would have put it in the inventory otherwise");
        assertEquals(dirt(13), cursor.get());
    }

    @Test
    void itemsOfASlotThatIsGoneComeBackAsOverflow() {
        final SlotMap<Stack> before = FakeStacks.menu("aaab", 64);
        final SlotMap<Stack> after = FakeStacks.menu("a#b#", 64); // one 'a' left, 'b' moved
        final List<@Nullable Stack> old = contents(4, 0, diamond(1), 1, diamond(2), 2, diamond(3), 3, diamond(4));
        final List<@Nullable Stack> next = contents(4);
        final AtomicReference<@Nullable Stack> cursor = new AtomicReference<>();

        final Handoff.Result<Stack> result = Handoff.reopen(OPS, before, old, after, next,
                () -> cursor.getAndSet(null), cursor::set, () -> true);

        assertEquals(contents(4, 0, diamond(1), 2, diamond(4)), next);
        assertEquals(List.of(diamond(2), diamond(3)), result.overflow());
        assertEquals(10, total(next) + total(result.overflow(), cursor));
    }

    @Test
    void anItemTheNewSlotRefusesComesBackAsOverflow() {
        final SlotMap<Stack> before = new SlotMap<>(List.of(new SlotMap.Input<Stack>('x', stack -> true, 64)));
        final SlotMap<Stack> after = new SlotMap<>(List.of(
                new SlotMap.Input<Stack>('x', stack -> stack.kind().equals("diamond"), 64)));
        final List<@Nullable Stack> old = contents(1, 0, dirt(5));
        final List<@Nullable Stack> next = contents(1);

        final Handoff.Result<Stack> result = Handoff.reopen(OPS, before, old, after, next, () -> null, stack -> { },
                () -> true);

        assertEquals(contents(1), next);
        assertEquals(List.of(dirt(5)), result.overflow());
    }

    @Test
    void aLowerMaxAmountSendsTheExtraBackAsOverflow() {
        final SlotMap<Stack> before = FakeStacks.menu("a", 64);
        final SlotMap<Stack> after = FakeStacks.menu("a", 8);
        final List<@Nullable Stack> old = contents(1, 0, diamond(30));
        final List<@Nullable Stack> next = contents(1);

        final Handoff.Result<Stack> result = Handoff.reopen(OPS, before, old, after, next, () -> null, stack -> { },
                () -> true);

        assertEquals(contents(1, 0, diamond(8)), next);
        assertEquals(List.of(diamond(22)), result.overflow());
    }

    @Test
    void whenTheNewInventoryDoesNotOpenNothingMovesAndTheCursorGoesBack() {
        final SlotMap<Stack> menu = FakeStacks.menu("#aa#", 64);
        final List<@Nullable Stack> old = contents(4, 1, diamond(5), 2, diamond(7));
        final List<@Nullable Stack> oldBefore = new ArrayList<>(old);
        final AtomicReference<@Nullable Stack> cursor = new AtomicReference<>(dirt(3));
        final AtomicBoolean tried = new AtomicBoolean();

        final Handoff.Result<Stack> result = Handoff.reopen(OPS, menu, old, menu, contents(4),
                () -> cursor.getAndSet(null), cursor::set, () -> {
                    tried.set(true);
                    return false;
                });

        assertTrue(tried.get());
        assertFalse(result.opened());
        assertEquals(List.of(), result.overflow(), "the items are still in the old inventory, none handed back");
        assertEquals(oldBefore, old);
        assertEquals(dirt(3), cursor.get());
    }

    @Test
    void anEmptyCursorStaysEmpty() {
        final SlotMap<Stack> menu = FakeStacks.menu("a", 64);
        final AtomicReference<@Nullable Stack> cursor = new AtomicReference<>();

        Handoff.reopen(OPS, menu, contents(1), menu, contents(1), () -> cursor.getAndSet(null), cursor::set,
                () -> true);

        assertEquals(null, cursor.get());
    }

    @Test
    void everyCombinationOfAmountsKeepsTheTotalWhenTheShapeShrinks() {
        final SlotMap<Stack> before = FakeStacks.menu("aaa", 64);
        final SlotMap<Stack> after = FakeStacks.menu("a#a", 8);
        for (int a = 0; a <= 64; a += 7) {
            for (int b = 0; b <= 64; b += 9) {
                for (int c = 0; c <= 64; c += 11) {
                    final List<@Nullable Stack> old = contents(3, 0, a == 0 ? null : diamond(a),
                            1, b == 0 ? null : diamond(b), 2, c == 0 ? null : diamond(c));
                    final List<@Nullable Stack> next = contents(3);
                    final AtomicReference<@Nullable Stack> cursor = new AtomicReference<>(diamond(2));

                    final Handoff.Result<Stack> result = Handoff.reopen(OPS, before, old, after, next,
                            () -> cursor.getAndSet(null), cursor::set, () -> true);

                    assertEquals(a + b + c + 2, total(next) + total(result.overflow(), cursor),
                            a + "," + b + "," + c);
                }
            }
        }
    }
}
