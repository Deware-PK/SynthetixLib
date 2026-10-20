package com.github.dewarepk.synthetixlib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The registry is the only place with per-viewer state besides the view itself: two viewers must never meet in it. */
class ViewRegistryTest {

    private final ViewRegistry<String, String> registry = new ViewRegistry<>();
    private final UUID alex = UUID.randomUUID();
    private final UUID steve = UUID.randomUUID();

    @Test
    void twoViewersOfTheSameMenuHaveTheirOwnView() {
        registry.put(alex, "auction/alex");
        registry.put(steve, "auction/steve");

        assertEquals("auction/alex", registry.get(alex));
        assertEquals("auction/steve", registry.get(steve));
        assertEquals(2, registry.size());

        assertEquals("auction/alex", registry.remove(alex));
        assertNull(registry.get(alex));
        assertEquals("auction/steve", registry.get(steve));
    }

    @Test
    void theBackStackIsPerViewer() {
        registry.push(alex, "shop");
        registry.push(alex, "bid");
        registry.push(steve, "settings");

        assertEquals("bid", registry.pop(alex));
        assertEquals("shop", registry.pop(alex));
        assertNull(registry.pop(alex));
        assertEquals("settings", registry.pop(steve));
    }

    @Test
    void clearingTheBackStackOfOneViewerLeavesTheOther() {
        registry.push(alex, "shop");
        registry.push(steve, "shop");

        registry.clearBack(alex);

        assertEquals(0, registry.backSize(alex));
        assertEquals(1, registry.backSize(steve));
    }

    @Test
    void aViewIsOnlyRemovedWhenItIsStillTheCurrentOne() {
        registry.put(alex, "old");
        registry.put(alex, "new"); // the viewer moved on to another menu

        assertFalse(registry.remove(alex, "old"));
        assertEquals("new", registry.get(alex));
        assertTrue(registry.remove(alex, "new"));
        assertNull(registry.get(alex));
    }

    @Test
    void theBackStackKeepsTheNewestEntriesOnly() {
        for (int i = 0; i < ViewRegistry.MAX_BACK + 10; i++) {
            registry.push(alex, "menu" + i);
        }

        assertEquals(ViewRegistry.MAX_BACK, registry.backSize(alex));
        assertEquals("menu" + (ViewRegistry.MAX_BACK + 9), registry.pop(alex));
    }

    @Test
    void clearAllReturnsTheOpenViewsAndForgetsEverything() {
        registry.put(alex, "a");
        registry.put(steve, "b");
        registry.push(alex, "back");

        final List<String> views = registry.clearAll();

        assertEquals(2, views.size());
        assertTrue(views.containsAll(List.of("a", "b")));
        assertEquals(0, registry.size());
        assertEquals(0, registry.backSize(alex));
        assertSame(null, registry.get(alex));
    }
}
