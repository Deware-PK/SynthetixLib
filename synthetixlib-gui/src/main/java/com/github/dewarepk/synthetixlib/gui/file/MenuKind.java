package com.github.dewarepk.synthetixlib.gui.file;

import java.util.Locale;

/**
 * The inventory a menu opens as: a chest of 1 to 6 rows, a hopper (5 slots) or a dispenser (3 x 3).
 *
 * <pre>{@code
 * type: hopper
 * layout:
 *   - "#B#I#"
 * }</pre>
 */
public enum MenuKind {

    /** 9 columns, 1 to 6 rows. */
    CHEST(9, 1, 6),
    /** 5 slots in one row. */
    HOPPER(5, 1, 1),
    /** 3 columns, 3 rows. */
    DISPENSER(3, 3, 3);

    private final int width;
    private final int minRows;
    private final int maxRows;

    MenuKind(final int width, final int minRows, final int maxRows) {
        this.width = width;
        this.minRows = minRows;
        this.maxRows = maxRows;
    }

    /** @return characters per layout line */
    public int width() {
        return width;
    }

    /** @return fewest layout lines */
    public int minRows() {
        return minRows;
    }

    /** @return most layout lines */
    public int maxRows() {
        return maxRows;
    }

    /** @return {@code true} if {@code rows} lines are allowed for this kind */
    public boolean allows(final int rows) {
        return rows >= minRows && rows <= maxRows;
    }

    /**
     * @param rows a number of lines the kind {@linkplain #allows allows}
     * @return slots of an inventory with that many lines
     */
    public int size(final int rows) {
        if (!allows(rows)) {
            throw new IllegalArgumentException(name().toLowerCase(Locale.ROOT) + " cannot have " + rows
                    + " rows");
        }
        return width * rows;
    }
}
