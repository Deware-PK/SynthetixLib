package com.github.dewarepk.synthetixlib.gui;

import com.github.dewarepk.synthetixlib.gui.file.MenuFile;
import com.github.dewarepk.synthetixlib.gui.file.MenuItem;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * What the click policy knows about the slots of the top inventory of a view: each slot is locked (the default) or an
 * input slot with the rules of its menu item. Immutable; built again when the menu file changes.
 *
 * @param <S> the stack type
 */
final class SlotMap<S> {

    /**
     * The rules of an input slot.
     *
     * @param id        the layout character of the slot
     * @param accepts   which items may be put here
     * @param maxAmount most items the slot holds, at least 1
     */
    record Input<S>(char id, Predicate<S> accepts, int maxAmount) {

        Input {
            if (maxAmount < 1) {
                throw new IllegalArgumentException("maxAmount must be at least 1");
            }
        }
    }

    private final List<@Nullable Input<S>> slots;
    private final boolean hasInput;
    private final boolean hasLocked;

    /** @param slots one entry per slot of the top inventory, {@code null} for a locked slot */
    SlotMap(final List<@Nullable Input<S>> slots) {
        this.slots = new ArrayList<>(slots);
        this.hasInput = slots.stream().anyMatch(input -> input != null);
        this.hasLocked = slots.stream().anyMatch(input -> input == null);
    }

    /**
     * @param file  a menu file
     * @param inert layout characters whose items do nothing; their slots are locked
     * @return the layout characters whose items are input slots
     */
    static Set<Character> inputChars(final MenuFile file, final Set<Character> inert) {
        final Set<Character> chars = new LinkedHashSet<>();
        for (final var entry : file.items().entrySet()) {
            final MenuItem item = entry.getValue();
            if (item.isInput() && !inert.contains(entry.getKey())) {
                chars.add(entry.getKey());
            }
        }
        return chars;
    }

    int size() {
        return slots.size();
    }

    /** @return the rules of the slot, {@code null} if it is locked or does not exist */
    @Nullable Input<S> input(final int slot) {
        return slot < 0 || slot >= slots.size() ? null : slots.get(slot);
    }

    boolean isInput(final int slot) {
        return input(slot) != null;
    }

    /** @return {@code true} if at least one slot is an input slot */
    boolean hasInput() {
        return hasInput;
    }

    /** @return {@code true} if at least one slot is locked (a layout space is locked too) */
    boolean hasLocked() {
        return hasLocked;
    }

    /** @return the input slots in slot order */
    int[] inputSlots() {
        return slotsWhere(input -> true);
    }

    /** @return the input slots of one layout character in slot order */
    int[] slotsOf(final char id) {
        return slotsWhere(input -> input.id() == id);
    }

    /** @return the layout characters of the input slots, in slot order of their first slot */
    Set<Character> chars() {
        final Set<Character> chars = new LinkedHashSet<>();
        for (final Input<S> input : slots) {
            if (input != null) {
                chars.add(input.id());
            }
        }
        return chars;
    }

    private int[] slotsWhere(final Predicate<Input<S>> test) {
        final List<Integer> found = new ArrayList<>();
        for (int slot = 0; slot < slots.size(); slot++) {
            final Input<S> input = slots.get(slot);
            if (input != null && test.test(input)) {
                found.add(slot);
            }
        }
        return found.stream().mapToInt(Integer::intValue).toArray();
    }
}
