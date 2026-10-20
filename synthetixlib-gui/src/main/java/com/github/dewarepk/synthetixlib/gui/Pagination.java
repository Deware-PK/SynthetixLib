package com.github.dewarepk.synthetixlib.gui;

/** Page arithmetic of the lists of a menu: every list char shows {@code slots} entries per page. */
final class Pagination {

    private Pagination() {
    }

    /**
     * @param entries how many entries the list has
     * @param slots   how many slots show it per page
     * @return the number of pages, at least 1 (an empty list still has its one empty page)
     */
    static int pages(final int entries, final int slots) {
        if (entries <= 0 || slots <= 0) {
            return 1;
        }
        return (entries + slots - 1) / slots;
    }

    /** @return {@code page} inside {@code 0..pages-1} */
    static int clamp(final int page, final int pages) {
        return Math.max(0, Math.min(page, pages - 1));
    }
}
