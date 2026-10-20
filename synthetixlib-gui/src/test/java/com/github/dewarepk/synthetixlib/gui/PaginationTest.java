package com.github.dewarepk.synthetixlib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PaginationTest {

    @Test
    void anEmptyListStillHasOnePage() {
        assertEquals(1, Pagination.pages(0, 21));
    }

    @Test
    void fewerEntriesThanSlotsIsOnePage() {
        assertEquals(1, Pagination.pages(1, 21));
        assertEquals(1, Pagination.pages(20, 21));
    }

    @Test
    void exactlyOnePageOfEntriesDoesNotAddAnEmptyPage() {
        assertEquals(1, Pagination.pages(21, 21));
        assertEquals(2, Pagination.pages(42, 21));
    }

    @Test
    void oneEntryOverStartsAnotherPage() {
        assertEquals(2, Pagination.pages(22, 21));
        assertEquals(5, Pagination.pages(100, 21));
    }

    @Test
    void noSlotsMeansOnePage() {
        assertEquals(1, Pagination.pages(10, 0));
    }

    @Test
    void aPageIsClampedToThePagesThatExist() {
        assertEquals(0, Pagination.clamp(-3, 4));
        assertEquals(2, Pagination.clamp(2, 4));
        assertEquals(3, Pagination.clamp(99, 4));
        assertEquals(0, Pagination.clamp(5, 1));
    }
}
