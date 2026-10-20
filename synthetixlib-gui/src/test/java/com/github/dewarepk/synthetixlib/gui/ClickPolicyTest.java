package com.github.dewarepk.synthetixlib.gui;

import static com.github.dewarepk.synthetixlib.gui.ClickPolicy.Region.BOTTOM;
import static com.github.dewarepk.synthetixlib.gui.ClickPolicy.Region.OUTSIDE;
import static com.github.dewarepk.synthetixlib.gui.ClickPolicy.Region.TOP;
import static com.github.dewarepk.synthetixlib.gui.ClickPolicy.Verdict.ALLOW;
import static com.github.dewarepk.synthetixlib.gui.ClickPolicy.Verdict.DENY;
import static com.github.dewarepk.synthetixlib.gui.ClickPolicy.Verdict.REDO_SHIFT;
import static com.github.dewarepk.synthetixlib.gui.FakeStacks.OPS;
import static com.github.dewarepk.synthetixlib.gui.FakeStacks.diamond;
import static com.github.dewarepk.synthetixlib.gui.FakeStacks.dirt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.github.dewarepk.synthetixlib.gui.ClickPolicy.Click;
import com.github.dewarepk.synthetixlib.gui.ClickPolicy.Region;
import com.github.dewarepk.synthetixlib.gui.ClickPolicy.Verdict;
import com.github.dewarepk.synthetixlib.gui.FakeStacks.Stack;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;

/**
 * The click policy, dupe-critical, so tested as a table: every {@link ClickType} times every {@link InventoryAction}
 * against a locked slot, an input slot with an accepted item, an input slot with a refused item, the player's own
 * inventory and the outside of the window. Every cell is compared with an oracle written down independently of the
 * policy, then the named bugs and the arithmetic are checked one by one.
 */
class ClickPolicyTest {

    /** {@code #a#}: locked, input (diamonds up to 64), locked. */
    private static final SlotMap<Stack> MENU = FakeStacks.menu("#a#", 64);
    private static final int LOCKED_SLOT = 0;
    private static final int INPUT_SLOT = 1;

    private static final Set<InventoryAction> HOTBAR = EnumSet.of(InventoryAction.HOTBAR_SWAP,
            InventoryAction.HOTBAR_MOVE_AND_READD);
    private static final Set<InventoryAction> PICKUPS = EnumSet.of(InventoryAction.PICKUP_ALL,
            InventoryAction.PICKUP_SOME, InventoryAction.PICKUP_HALF, InventoryAction.PICKUP_ONE);
    private static final Set<InventoryAction> PLACES = EnumSet.of(InventoryAction.PLACE_ALL,
            InventoryAction.PLACE_SOME, InventoryAction.PLACE_ONE);
    private static final Set<InventoryAction> SLOT_DROPS = EnumSet.of(InventoryAction.DROP_ALL_SLOT,
            InventoryAction.DROP_ONE_SLOT);
    private static final Set<InventoryAction> CURSOR_DROPS = EnumSet.of(InventoryAction.DROP_ALL_CURSOR,
            InventoryAction.DROP_ONE_CURSOR);

    private enum Column { LOCKED, INPUT_ACCEPTED, INPUT_REFUSED, BOTTOM, OUTSIDE }

    private static Click<Stack> click(final Column column, final ClickType type, final InventoryAction action) {
        return switch (column) {
            case LOCKED -> new Click<>(type, action, TOP, LOCKED_SLOT, diamond(3), diamond(2), diamond(3));
            case INPUT_ACCEPTED -> new Click<>(type, action, TOP, INPUT_SLOT, diamond(3), diamond(2), diamond(3));
            case INPUT_REFUSED -> new Click<>(type, action, TOP, INPUT_SLOT, dirt(3), null, dirt(3));
            case BOTTOM -> new Click<>(type, action, BOTTOM, 0, diamond(3), diamond(2), diamond(3));
            case OUTSIDE -> new Click<>(type, action, OUTSIDE, -999, diamond(3), null, null);
        };
    }

    private static Verdict decide(final Column column, final ClickType type, final InventoryAction action) {
        return ClickPolicy.decide(MENU, OPS, click(column, type, action));
    }

    private static Verdict decide(final SlotMap<Stack> menu, final Region region, final int slot, final ClickType type,
                                  final InventoryAction action, final Stack cursor, final Stack current,
                                  final Stack hotbar) {
        return ClickPolicy.decide(menu, OPS, new Click<>(type, action, region, slot, cursor, current, hotbar));
    }

    private static boolean isShift(final ClickType type) {
        return type == ClickType.SHIFT_LEFT || type == ClickType.SHIFT_RIGHT;
    }

    private static boolean isHotbarClick(final ClickType type) {
        return type == ClickType.NUMBER_KEY || type == ClickType.SWAP_OFFHAND;
    }

    /** What must happen, written as rules about the click and the column, not by calling the policy. */
    private static Verdict expected(final Column column, final ClickType type, final InventoryAction action) {
        if (type == ClickType.UNKNOWN) {
            return DENY;
        }
        if (column == Column.OUTSIDE) {
            final boolean fromBorder = EnumSet.of(ClickType.WINDOW_BORDER_LEFT, ClickType.WINDOW_BORDER_RIGHT,
                    ClickType.LEFT, ClickType.RIGHT, ClickType.CREATIVE).contains(type);
            return CURSOR_DROPS.contains(action) && fromBorder ? ALLOW : DENY;
        }
        if (EnumSet.of(ClickType.MIDDLE, ClickType.WINDOW_BORDER_LEFT, ClickType.WINDOW_BORDER_RIGHT).contains(type)) {
            return DENY;
        }
        if (EnumSet.of(InventoryAction.UNKNOWN, InventoryAction.NOTHING, InventoryAction.CLONE_STACK)
                .contains(action) || CURSOR_DROPS.contains(action)) {
            return DENY;
        }
        if (HOTBAR.contains(action) != isHotbarClick(type)) {
            return DENY;
        }
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY && !isShift(type)) {
            return DENY;
        }
        if (type == ClickType.DOUBLE_CLICK || action == InventoryAction.COLLECT_TO_CURSOR) {
            return DENY; // the menu has locked slots (bug #2)
        }
        final boolean takesOut = PICKUPS.contains(action) || SLOT_DROPS.contains(action)
                || action == InventoryAction.MOVE_TO_OTHER_INVENTORY;
        return switch (column) {
            case LOCKED -> DENY;
            case INPUT_ACCEPTED -> takesOut || PLACES.contains(action) || HOTBAR.contains(action)
                    || action == InventoryAction.SWAP_WITH_CURSOR ? ALLOW : DENY;
            case INPUT_REFUSED -> takesOut ? ALLOW : DENY;
            case BOTTOM -> action == InventoryAction.MOVE_TO_OTHER_INVENTORY ? REDO_SHIFT
                    : takesOut || PLACES.contains(action) || HOTBAR.contains(action)
                    || action == InventoryAction.SWAP_WITH_CURSOR ? ALLOW : DENY;
            case OUTSIDE -> throw new IllegalStateException();
        };
    }

    // ------------------------------------------------------------------------------------------ the full table

    @Test
    void everyCellOfTheTableIsAsTheOracleSays() {
        int cells = 0;
        for (final Column column : Column.values()) {
            for (final ClickType type : ClickType.values()) {
                for (final InventoryAction action : InventoryAction.values()) {
                    assertEquals(expected(column, type, action), decide(column, type, action),
                            column + " / " + type + " / " + action);
                    cells++;
                }
            }
        }
        assertEquals(5 * ClickType.values().length * InventoryAction.values().length, cells);
    }

    @Test
    void aLockedSlotNeverAllowsAnything() {
        for (final ClickType type : ClickType.values()) {
            for (final InventoryAction action : InventoryAction.values()) {
                assertEquals(DENY, decide(Column.LOCKED, type, action), type + " / " + action);
            }
        }
    }

    @Test
    void onlyAShiftClickInThePlayersInventoryIsRedone() {
        for (final Column column : Column.values()) {
            for (final ClickType type : ClickType.values()) {
                for (final InventoryAction action : InventoryAction.values()) {
                    if (decide(column, type, action) == REDO_SHIFT) {
                        assertEquals(Column.BOTTOM, column);
                        assertEquals(InventoryAction.MOVE_TO_OTHER_INVENTORY, action);
                        assertEquals(true, isShift(type));
                    }
                }
            }
        }
    }

    @Test
    void nothingIsAllowedForAnUnknownClickOrAnActionThatDoesNothing() {
        for (final Column column : Column.values()) {
            for (final InventoryAction action : InventoryAction.values()) {
                assertEquals(DENY, decide(column, ClickType.UNKNOWN, action), column + " / " + action);
            }
            for (final ClickType type : ClickType.values()) {
                for (final InventoryAction action : EnumSet.of(InventoryAction.UNKNOWN, InventoryAction.NOTHING,
                        InventoryAction.CLONE_STACK)) {
                    assertEquals(DENY, decide(column, type, action), column + " / " + type + " / " + action);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------ named bugs

    @Test
    void bug2_aDoubleClickIsDeniedWhereverItLandsWhileTheMenuHasALockedSlot() {
        for (final Column column : Column.values()) {
            if (column == Column.OUTSIDE) {
                continue;
            }
            assertEquals(DENY, decide(column, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR), "" + column);
            assertEquals(DENY, decide(column, ClickType.LEFT, InventoryAction.COLLECT_TO_CURSOR), "" + column);
            assertEquals(DENY, decide(column, ClickType.DOUBLE_CLICK, InventoryAction.PICKUP_ALL), "" + column);
            assertEquals(DENY, decide(column, ClickType.DOUBLE_CLICK, InventoryAction.NOTHING), "" + column);
        }
    }

    @Test
    void bug2_aDoubleClickIsAllowedOnlyWhenEverySlotIsAnInputSlot() {
        final SlotMap<Stack> allInput = FakeStacks.menu("ab", 64);

        assertEquals(ALLOW, decide(allInput, TOP, 0, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR,
                diamond(1), diamond(1), null));
        assertEquals(ALLOW, decide(allInput, BOTTOM, 0, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR,
                diamond(1), diamond(1), null));
        // one locked slot among them is enough
        assertEquals(DENY, decide(FakeStacks.menu("ab#", 64), TOP, 0, ClickType.DOUBLE_CLICK,
                InventoryAction.COLLECT_TO_CURSOR, diamond(1), diamond(1), null));
    }

    @Test
    void bug3_aShiftClickInThePlayersInventoryIsRedoneByUsNeverLeftToTheServer() {
        for (final ClickType type : EnumSet.of(ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT)) {
            assertEquals(REDO_SHIFT, decide(Column.BOTTOM, type, InventoryAction.MOVE_TO_OTHER_INVENTORY));
        }
        // no input slot to move it to: nothing happens at all
        assertEquals(DENY, decide(FakeStacks.menu("###", 64), BOTTOM, 0, ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY, null, diamond(5), null));
    }

    @Test
    void shiftClickOutOfALockedSlotIsDeniedAndOutOfAnInputSlotAllowed() {
        assertEquals(DENY, decide(Column.LOCKED, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY));
        assertEquals(ALLOW, decide(Column.INPUT_ACCEPTED, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY));
        assertEquals(ALLOW, decide(Column.INPUT_REFUSED, ClickType.SHIFT_RIGHT, InventoryAction.MOVE_TO_OTHER_INVENTORY));
    }

    @Test
    void numberKeyOffHandAndDropOnALockedSlotAreDenied() {
        assertEquals(DENY, decide(Column.LOCKED, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP));
        assertEquals(DENY, decide(Column.LOCKED, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_MOVE_AND_READD));
        assertEquals(DENY, decide(Column.LOCKED, ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP));
        assertEquals(DENY, decide(Column.LOCKED, ClickType.DROP, InventoryAction.DROP_ONE_SLOT));
        assertEquals(DENY, decide(Column.LOCKED, ClickType.CONTROL_DROP, InventoryAction.DROP_ALL_SLOT));
    }

    @Test
    void creativeClicksAreJudgedByTheirActionLikeAnyOther() {
        assertEquals(DENY, decide(Column.LOCKED, ClickType.CREATIVE, InventoryAction.CLONE_STACK));
        assertEquals(DENY, decide(Column.INPUT_ACCEPTED, ClickType.CREATIVE, InventoryAction.CLONE_STACK));
        assertEquals(DENY, decide(Column.LOCKED, ClickType.CREATIVE, InventoryAction.PICKUP_ALL));
        assertEquals(DENY, decide(Column.LOCKED, ClickType.CREATIVE, InventoryAction.PLACE_ALL));
        assertEquals(ALLOW, decide(Column.INPUT_ACCEPTED, ClickType.CREATIVE, InventoryAction.PICKUP_ALL));
        assertEquals(DENY, decide(Column.INPUT_REFUSED, ClickType.CREATIVE, InventoryAction.PLACE_ALL));
        assertEquals(ALLOW, decide(Column.OUTSIDE, ClickType.CREATIVE, InventoryAction.DROP_ALL_CURSOR));
    }

    @Test
    void middleClickAndTheWindowBorderNeverDoAnythingInsideTheWindow() {
        for (final Column column : EnumSet.of(Column.LOCKED, Column.INPUT_ACCEPTED, Column.INPUT_REFUSED, Column.BOTTOM)) {
            for (final InventoryAction action : InventoryAction.values()) {
                assertEquals(DENY, decide(column, ClickType.MIDDLE, action));
                assertEquals(DENY, decide(column, ClickType.WINDOW_BORDER_LEFT, action));
                assertEquals(DENY, decide(column, ClickType.WINDOW_BORDER_RIGHT, action));
            }
        }
    }

    @Test
    void aClickPastTheEndOfTheMenuIsDenied() {
        assertEquals(DENY, decide(MENU, TOP, 3, ClickType.LEFT, InventoryAction.PICKUP_ALL, null, diamond(1), null));
        assertEquals(DENY, decide(MENU, TOP, -1, ClickType.LEFT, InventoryAction.PICKUP_ALL, null, diamond(1), null));
    }

    @Test
    void clicksInThePlayersInventoryNeedAnInputSlotInTheMenu() {
        final SlotMap<Stack> noInput = FakeStacks.menu("###", 64);

        for (final ClickType type : ClickType.values()) {
            for (final InventoryAction action : InventoryAction.values()) {
                assertEquals(DENY, decide(noInput, BOTTOM, 0, type, action, diamond(1), diamond(1), diamond(1)),
                        type + " / " + action);
            }
        }
        assertEquals(ALLOW, decide(Column.BOTTOM, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    // ------------------------------------------------------------------------------------------ amounts and items

    private static final SlotMap<Stack> CAPPED = FakeStacks.menu("#a#", 8);

    private static Verdict capped(final ClickType type, final InventoryAction action, final Stack cursor,
                                  final Stack current, final Stack hotbar) {
        return decide(CAPPED, TOP, INPUT_SLOT, type, action, cursor, current, hotbar);
    }

    @Test
    void placeAllIsAllowedUpToMaxAmountAndNotOneItemMore() {
        assertEquals(ALLOW, capped(ClickType.LEFT, InventoryAction.PLACE_ALL, diamond(5), diamond(3), null));
        assertEquals(DENY, capped(ClickType.LEFT, InventoryAction.PLACE_ALL, diamond(6), diamond(3), null));
        assertEquals(ALLOW, capped(ClickType.LEFT, InventoryAction.PLACE_ALL, diamond(8), null, null));
        assertEquals(DENY, capped(ClickType.LEFT, InventoryAction.PLACE_ALL, diamond(9), null, null));
    }

    @Test
    void placeOneIsAllowedUpToMaxAmount() {
        assertEquals(ALLOW, capped(ClickType.RIGHT, InventoryAction.PLACE_ONE, diamond(30), diamond(7), null));
        assertEquals(DENY, capped(ClickType.RIGHT, InventoryAction.PLACE_ONE, diamond(30), diamond(8), null));
    }

    @Test
    void placeSomeFillsTheStackSoItIsAllowedOnlyIfAFullStackIsWithinMaxAmount() {
        assertEquals(DENY, capped(ClickType.LEFT, InventoryAction.PLACE_SOME, diamond(30), diamond(3), null));
        final Stack smallStack = new Stack("diamond", 30, 8);
        assertEquals(ALLOW, capped(ClickType.LEFT, InventoryAction.PLACE_SOME, smallStack, new Stack("diamond", 3, 8),
                null));
    }

    @Test
    void swapWithTheCursorNeedsAnAcceptedItemAndAnAmountWithinMax() {
        assertEquals(ALLOW, capped(ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR, diamond(8), dirt(2), null));
        assertEquals(DENY, capped(ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR, diamond(9), dirt(2), null));
        assertEquals(DENY, capped(ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR, dirt(1), diamond(2), null));
        assertEquals(DENY, capped(ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR, null, diamond(2), null));
    }

    @Test
    void placingOntoADifferentKindIsDeniedBecauseTheFactsDoNotAddUp() {
        assertEquals(DENY, capped(ClickType.LEFT, InventoryAction.PLACE_ALL, diamond(1), dirt(1), null));
    }

    @Test
    void anEmptyCursorPlacesNothing() {
        assertEquals(DENY, capped(ClickType.LEFT, InventoryAction.PLACE_ALL, null, null, null));
        assertEquals(DENY, capped(ClickType.LEFT, InventoryAction.PLACE_ONE, diamond(0), null, null));
    }

    @Test
    void numberKeyAndOffHandPutOnlyAnAcceptedItemWithinMaxIntoAnInputSlot() {
        for (final ClickType key : EnumSet.of(ClickType.NUMBER_KEY, ClickType.SWAP_OFFHAND)) {
            for (final InventoryAction action : HOTBAR) {
                assertEquals(ALLOW, capped(key, action, null, diamond(2), diamond(8)), key + " / " + action);
                assertEquals(DENY, capped(key, action, null, diamond(2), diamond(9)), key + " / " + action);
                assertEquals(DENY, capped(key, action, null, diamond(2), dirt(1)), key + " / " + action);
                assertEquals(ALLOW, capped(key, action, null, diamond(2), null), "empty hand takes the item out");
            }
        }
    }

    @Test
    void takingOutOfAnInputSlotIsAlwaysAllowedWhateverTheCursorHolds() {
        for (final InventoryAction action : EnumSet.of(InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_HALF,
                InventoryAction.PICKUP_ONE, InventoryAction.PICKUP_SOME, InventoryAction.DROP_ALL_SLOT,
                InventoryAction.DROP_ONE_SLOT)) {
            assertEquals(ALLOW, capped(ClickType.LEFT, action, dirt(9), diamond(2), null), "" + action);
        }
    }

    // ------------------------------------------------------------------------------------------ drags

    private static Verdict drag(final SlotMap<Stack> menu, final Map<Integer, Stack> top, final boolean bottom) {
        return ClickPolicy.decideDrag(menu, OPS, top, bottom);
    }

    @Test
    void aDragOverALockedSlotIsDeniedAsAWhole() {
        assertEquals(DENY, drag(MENU, Map.of(LOCKED_SLOT, diamond(2)), false));
        assertEquals(DENY, drag(MENU, Map.of(INPUT_SLOT, diamond(2), LOCKED_SLOT, diamond(2)), false));
        assertEquals(DENY, drag(MENU, Map.of(INPUT_SLOT, diamond(2), LOCKED_SLOT, diamond(2)), true));
        assertEquals(DENY, drag(MENU, Map.of(7, diamond(1)), false));
    }

    @Test
    void aDragOverInputSlotsOnlyIsAllowedIfEverySlotEndsUpAcceptedAndWithinMax() {
        final SlotMap<Stack> two = FakeStacks.menu("#ab#", 8);

        assertEquals(ALLOW, drag(two, Map.of(1, diamond(4), 2, diamond(4)), false));
        assertEquals(DENY, drag(two, Map.of(1, diamond(4), 2, diamond(9)), false));
        assertEquals(DENY, drag(two, Map.of(1, diamond(4), 2, dirt(4)), false));
        assertEquals(DENY, drag(two, Map.of(1, diamond(0)), false));
    }

    @Test
    void aDragThatReachesTheBottomNeedsAnInputSlotInTheMenu() {
        assertEquals(ALLOW, drag(MENU, Map.of(), true));
        assertEquals(ALLOW, drag(MENU, Map.of(INPUT_SLOT, diamond(2)), true));
        assertEquals(DENY, drag(FakeStacks.menu("###", 64), Map.of(), true));
        assertEquals(DENY, drag(MENU, Map.of(), false));
    }

    @Test
    void dragVerdictsAreNeverRedo() {
        assertNotEquals(REDO_SHIFT, drag(MENU, Map.of(INPUT_SLOT, diamond(2)), true));
        final Map<Integer, Stack> none = new HashMap<>();
        assertEquals(DENY, drag(MENU, none, false));
    }
}
