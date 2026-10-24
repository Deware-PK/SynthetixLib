package com.github.dewarepk.synthetixlib.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** One current view and one back stack per viewer; nothing is shared between viewers (bug #4). */
class ViewRegistryTest {

    private final ViewRegistry<String, Integer> registry = new ViewRegistry<>();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void viewersDoNotSeeEachOthersViewsOrStacks() {
        registry.put(alice, "a");
        registry.put(bob, "b");
        registry.push(alice, 1);
        assertEquals("a", registry.get(alice));
        assertEquals("b", registry.get(bob));
        assertEquals(1, registry.backSize(alice));
        assertEquals(0, registry.backSize(bob));
        assertNull(registry.pop(bob));
    }

    @Test
    void removeOnlyRemovesTheViewThatWasCurrent() {
        registry.put(alice, "old");
        registry.put(alice, "new");
        assertFalse(registry.remove(alice, "old"), "a stale view must not remove its replacement");
        assertEquals("new", registry.get(alice));
        assertTrue(registry.remove(alice, "new"));
        assertNull(registry.get(alice));
    }

    @Test
    void backReturnsTheNewestFirstAndKeepsAtMost32() {
        for (int i = 0; i < ViewRegistry.MAX_BACK + 5; i++) {
            registry.push(alice, i);
        }
        assertEquals(ViewRegistry.MAX_BACK, registry.backSize(alice));
        assertEquals(ViewRegistry.MAX_BACK + 4, registry.pop(alice));
        registry.clearBack(alice);
        assertNull(registry.pop(alice));
    }

    @Test
    void clearAllForgetsEverythingAndReturnsTheViews() {
        registry.put(alice, "a");
        registry.put(bob, "b");
        registry.push(alice, 1);
        final List<String> views = registry.clearAll().stream().sorted().toList();
        assertEquals(List.of("a", "b"), views);
        assertEquals(0, registry.size());
        assertEquals(0, registry.backSize(alice));
    }
}
