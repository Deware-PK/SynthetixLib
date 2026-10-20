package com.github.dewarepk.synthetixlib.gui.file;

/**
 * When an item is left out of the view; written {@code hide-when: first-page} in the menu file.
 */
public enum HideWhen {

    /** Hidden while the viewer is on the first page (a "previous page" button). */
    FIRST_PAGE,
    /** Hidden while the viewer is on the last page (a "next page" button). */
    LAST_PAGE
}
