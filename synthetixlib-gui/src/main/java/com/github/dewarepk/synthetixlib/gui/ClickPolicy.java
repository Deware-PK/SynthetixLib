package com.github.dewarepk.synthetixlib.gui;

import java.util.Map;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.jspecify.annotations.Nullable;

/**
 * The click policy of a menu (design section 4), a pure function: what may happen to the slots of a menu when a player
 * clicks, shift-clicks, double-clicks, presses a number key, drags and so on. It knows nothing about the server; the
 * listener turns an event into the facts below and does what the verdict says.
 *
 * <p>Everything is denied unless a rule below allows it. A slot is locked unless the menu file marks it as an input
 * slot; only input slots take part in a click that moves items, and they accept only the items and amounts their
 * menu item allows. Every rule that allows something is written against what the server will do for the
 * {@link InventoryAction}, so a click type or action the policy does not know is denied.
 *
 * <ul>
 *   <li>Locked slot: every click is denied (bug #2 and #3 start here).</li>
 *   <li>Input slot: take out, drop and shift out are allowed; putting in (place, swap with the cursor, number key,
 *       off-hand key) is allowed only for an accepted item that keeps the slot within {@code max-amount}.</li>
 *   <li>Double-click ({@code COLLECT_TO_CURSOR}) is denied as soon as the menu has any locked slot, wherever the
 *       player clicked: the server would gather the matching items of locked slots too (bug #2).</li>
 *   <li>Shift-click in the player's own inventory is never left to the server, which would put the stack in the first
 *       free slot of the menu: the verdict is {@link Verdict#REDO_SHIFT} and the stack goes to input slots only
 *       (bug #3).</li>
 *   <li>Other clicks in the player's own inventory are allowed only while the menu has an input slot, so a player can
 *       pick an item up and put it in; they cannot reach the menu.</li>
 *   <li>Dropping the cursor outside the window is allowed: the cursor holds the player's own item, never a menu item.</li>
 * </ul>
 */
final class ClickPolicy {

    /** What the listener does with an event. */
    enum Verdict {
        /** Leave the event to the server. */
        ALLOW,
        /** Cancel it; nothing moves. */
        DENY,
        /** Cancel it and move the clicked stack into the input slots ourselves (a shift-click from the player's inventory). */
        REDO_SHIFT
    }

    /** Where a click landed. */
    enum Region {
        /** A slot of the menu. */
        TOP,
        /** A slot of the player's own inventory shown below the menu. */
        BOTTOM,
        /** Outside the window. */
        OUTSIDE
    }

    /**
     * The facts of one click.
     *
     * @param click   the click type
     * @param action  what the server will do
     * @param region  where the click landed
     * @param slot    the slot of the menu for {@link Region#TOP}, else ignored
     * @param cursor  the stack on the cursor, {@code null} or empty if none
     * @param current the stack in the clicked slot, {@code null} or empty if none
     * @param hotbar  the stack the number key or the off-hand key points at, {@code null} or empty if none; only read for
     *                {@code NUMBER_KEY} and {@code SWAP_OFFHAND}
     */
    record Click<S>(ClickType click, InventoryAction action, Region region, int slot, @Nullable S cursor,
                    @Nullable S current, @Nullable S hotbar) {
    }

    private ClickPolicy() {
    }

    /**
     * Decides a click.
     *
     * @param slots the slots of the menu
     * @param ops   how to read stacks
     * @param click the facts
     * @return what to do with the event
     */
    static <S> Verdict decide(final SlotMap<S> slots, final StackOps<S> ops, final Click<S> facts) {
        final Click<S> click = normalised(facts);
        if (click.region() == Region.OUTSIDE) {
            return outside(click);
        }
        if (!plausible(click)) {
            return Verdict.DENY;
        }
        if (click.click() == ClickType.DOUBLE_CLICK || click.action() == InventoryAction.COLLECT_TO_CURSOR) {
            return collect(slots, click);
        }
        return click.region() == Region.TOP ? top(slots, ops, click) : bottom(slots, click);
    }

    /**
     * {@code HOTBAR_MOVE_AND_READD} is a number-key swap too. The constant is deprecated for removal, so it is matched by name
     * (a server that dropped it must still load this class).
     */
    private static <S> Click<S> normalised(final Click<S> facts) {
        if (!facts.action().name().equals("HOTBAR_MOVE_AND_READD")) {
            return facts;
        }
        return new Click<>(facts.click(), InventoryAction.HOTBAR_SWAP, facts.region(), facts.slot(), facts.cursor(),
                facts.current(), facts.hotbar());
    }

    /**
     * Decides a drag (several slots filled with the cursor).
     *
     * @param slots         the slots of the menu
     * @param ops           how to read stacks
     * @param topResults    for every slot of the menu the drag touches, the stack that slot would hold afterwards
     * @param touchesBottom {@code true} if the drag touches the player's own inventory as well
     * @return {@code ALLOW} or {@code DENY}
     */
    static <S> Verdict decideDrag(final SlotMap<S> slots, final StackOps<S> ops, final Map<Integer, S> topResults,
                                  final boolean touchesBottom) {
        if (topResults.isEmpty() && !touchesBottom) {
            return Verdict.DENY;
        }
        for (final Map.Entry<Integer, S> entry : topResults.entrySet()) {
            final SlotMap.Input<S> input = slots.input(entry.getKey());
            final S result = entry.getValue();
            if (input == null || ops.isEmpty(result) || !input.accepts().test(result)
                    || ops.amount(result) > input.maxAmount()) {
                return Verdict.DENY; // one locked or refused slot spoils the whole drag
            }
        }
        if (touchesBottom && !slots.hasInput()) {
            return Verdict.DENY;
        }
        return Verdict.ALLOW;
    }

    // ------------------------------------------------------------------------------------------ rules

    private static Verdict outside(final Click<?> c) {
        final boolean dropsCursor = c.action() == InventoryAction.DROP_ALL_CURSOR
                || c.action() == InventoryAction.DROP_ONE_CURSOR;
        final boolean fromTheWindowBorder = switch (c.click()) {
            case WINDOW_BORDER_LEFT, WINDOW_BORDER_RIGHT, LEFT, RIGHT, CREATIVE -> true;
            default -> false;
        };
        return dropsCursor && fromTheWindowBorder ? Verdict.ALLOW : Verdict.DENY;
    }

    /** A click type and action that cannot be a click inside the window, or that never does anything we allow. */
    private static boolean plausible(final Click<?> c) {
        switch (c.click()) {
            case UNKNOWN, MIDDLE, WINDOW_BORDER_LEFT, WINDOW_BORDER_RIGHT -> {
                return false;
            }
            default -> { }
        }
        switch (c.action()) {
            case UNKNOWN, NOTHING, CLONE_STACK, DROP_ALL_CURSOR, DROP_ONE_CURSOR -> {
                return false;
            }
            default -> { }
        }
        final boolean hotbarAction = c.action() == InventoryAction.HOTBAR_SWAP;
        final boolean hotbarClick = c.click() == ClickType.NUMBER_KEY || c.click() == ClickType.SWAP_OFFHAND;
        if (hotbarAction != hotbarClick) {
            return false;
        }
        return c.action() != InventoryAction.MOVE_TO_OTHER_INVENTORY
                || c.click() == ClickType.SHIFT_LEFT || c.click() == ClickType.SHIFT_RIGHT;
    }

    /** Double-click: the server gathers the cursor's item from every slot of the window, so no locked slot may exist. */
    private static Verdict collect(final SlotMap<?> slots, final Click<?> c) {
        if (slots.hasLocked()) {
            return Verdict.DENY;
        }
        return c.region() == Region.TOP && !slots.isInput(c.slot()) ? Verdict.DENY : Verdict.ALLOW;
    }

    private static <S> Verdict top(final SlotMap<S> slots, final StackOps<S> ops, final Click<S> c) {
        final SlotMap.Input<S> input = slots.input(c.slot());
        if (input == null) {
            return Verdict.DENY;
        }
        return switch (c.action()) {
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE, DROP_ALL_SLOT, DROP_ONE_SLOT,
                 MOVE_TO_OTHER_INVENTORY -> Verdict.ALLOW;
            case PLACE_ALL, PLACE_SOME, PLACE_ONE -> place(ops, input, c);
            case SWAP_WITH_CURSOR -> {
                final S cursor = c.cursor();
                yield !ops.isEmpty(cursor) && input.accepts().test(cursor) && ops.amount(cursor) <= input.maxAmount()
                        ? Verdict.ALLOW : Verdict.DENY;
            }
            case HOTBAR_SWAP -> {
                final S hotbar = c.hotbar();
                yield ops.isEmpty(hotbar) || input.accepts().test(hotbar) && ops.amount(hotbar) <= input.maxAmount()
                        ? Verdict.ALLOW : Verdict.DENY;
            }
            default -> Verdict.DENY;
        };
    }

    private static <S> Verdict place(final StackOps<S> ops, final SlotMap.Input<S> input, final Click<S> c) {
        final S cursor = c.cursor();
        if (ops.isEmpty(cursor) || !input.accepts().test(cursor)) {
            return Verdict.DENY;
        }
        final S current = c.current();
        final boolean occupied = !ops.isEmpty(current);
        if (occupied && !ops.sameKind(cursor, current)) {
            return Verdict.DENY; // that is a swap, not a place: the facts do not add up
        }
        final int existing = occupied ? ops.amount(current) : 0;
        final int added = switch (c.action()) {
            case PLACE_ALL -> ops.amount(cursor);
            case PLACE_ONE -> 1;
            default -> Math.max(ops.maxStack(cursor) - existing, 0); // PLACE_SOME fills the stack up
        };
        return existing + added <= input.maxAmount() ? Verdict.ALLOW : Verdict.DENY;
    }

    private static Verdict bottom(final SlotMap<?> slots, final Click<?> c) {
        if (!slots.hasInput()) {
            return Verdict.DENY;
        }
        return switch (c.action()) {
            case MOVE_TO_OTHER_INVENTORY -> Verdict.REDO_SHIFT;
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE, PLACE_ALL, PLACE_SOME, PLACE_ONE,
                 SWAP_WITH_CURSOR, DROP_ALL_SLOT, DROP_ONE_SLOT, HOTBAR_SWAP -> Verdict.ALLOW;
            default -> Verdict.DENY;
        };
    }
}
