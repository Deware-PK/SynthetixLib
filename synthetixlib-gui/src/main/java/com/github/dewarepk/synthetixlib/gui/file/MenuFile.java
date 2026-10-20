package com.github.dewarepk.synthetixlib.gui.file;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A menu file after parsing: immutable and shared by every view of the menu.
 *
 * <pre>{@code
 * title: '<white>Auction <gray>(<page>/<pages>)'
 * type: chest
 * rows: 6
 * update-every: 20t
 * layout:
 *   - "#########"
 *   - "#LLLLLLL#"
 *   - "<##BIT##>"
 * items:
 *   '#': { material: black_stained_glass_pane, hide-tooltip: true }
 * }</pre>
 *
 * @param title       MiniMessage title of the inventory
 * @param type        the kind of inventory
 * @param rows        layout lines: 1 to 6 for a chest, 1 for a hopper, 3 for a dispenser
 * @param updateTicks ticks between renders of dynamic items, 0 for never
 * @param layout      exactly {@code rows} lines of {@code type.width()} characters; a space is an empty slot
 * @param items       the item of each layout character
 */
public record MenuFile(String title, MenuKind type, int rows, int updateTicks, List<String> layout,
                       Map<Character, MenuItem> items) {

    public MenuFile {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(type, "type");
        if (!type.allows(rows)) {
            throw new IllegalArgumentException(type + " cannot have " + rows + " rows");
        }
        if (updateTicks < 0) {
            throw new IllegalArgumentException("updateTicks must not be negative");
        }
        layout = List.copyOf(layout);
        if (layout.size() != rows || layout.stream().anyMatch(line -> line.length() != type.width())) {
            throw new IllegalArgumentException("layout must be " + rows + " lines of " + type.width() + " characters");
        }
        items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
    }

    /** @return a menu with no items: a chest of three empty rows, what a menu falls back to when its file is unusable */
    public static MenuFile empty() {
        final String line = " ".repeat(MenuKind.CHEST.width());
        return new MenuFile("", MenuKind.CHEST, 3, 0, List.of(line, line, line), Map.of());
    }

    /** @return number of slots of the inventory */
    public int size() {
        return type.size(rows);
    }

    /**
     * @param slot a slot index from 0
     * @return its layout character, a space for an empty slot
     */
    public char charAt(final int slot) {
        if (slot < 0 || slot >= size()) {
            throw new IndexOutOfBoundsException("slot " + slot + " of " + size());
        }
        return layout.get(slot / type.width()).charAt(slot % type.width());
    }

    /**
     * @param c a layout character
     * @return the slots that show it, in layout order (left to right, top to bottom)
     */
    public int[] slots(final char c) {
        final List<Integer> found = new ArrayList<>();
        for (int slot = 0; slot < size(); slot++) {
            if (charAt(slot) == c) {
                found.add(slot);
            }
        }
        return found.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * @param c a layout character
     * @return its item, or {@code null} if the file defines none
     */
    public @Nullable MenuItem item(final char c) {
        return items.get(c);
    }
}
