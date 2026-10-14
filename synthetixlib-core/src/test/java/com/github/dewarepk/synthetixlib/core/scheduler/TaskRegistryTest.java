package com.github.dewarepk.synthetixlib.core.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TaskRegistryTest {

    @Test
    void registerAndUnregister() {
        final TaskRegistry<String> registry = new TaskRegistry<>();
        assertTrue(registry.register("a"));
        assertTrue(registry.register("b"));
        assertEquals(2, registry.size());
        registry.unregister("a");
        registry.unregister("missing");
        assertEquals(1, registry.size());
    }

    @Test
    void closeReturnsLiveTasksAndRejectsNewOnes() {
        final TaskRegistry<String> registry = new TaskRegistry<>();
        registry.register("a");
        registry.register("b");
        registry.unregister("b");

        assertEquals(List.of("a"), registry.close());
        assertTrue(registry.isClosed());
        assertFalse(registry.register("c"));
        assertEquals(1, registry.size(), "rejected task must not be tracked");
    }

    @Test
    void closeIsIdempotent() {
        final TaskRegistry<String> registry = new TaskRegistry<>();
        registry.close();
        assertTrue(registry.close().isEmpty());
        assertTrue(registry.isClosed());
    }
}
