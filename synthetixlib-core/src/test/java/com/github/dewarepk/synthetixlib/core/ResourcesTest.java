package com.github.dewarepk.synthetixlib.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourcesTest {

    private final List<String> events = new ArrayList<>();
    private final List<Throwable> errors = new ArrayList<>();
    private final Resources resources = new Resources((r, t) -> errors.add(t));

    @Test
    void closesInReverseOrder() {
        resources.attach(() -> events.add("a"));
        resources.attach(() -> events.add("b"));
        resources.attach(() -> events.add("c"));
        resources.close();
        assertEquals(List.of("c", "b", "a"), events);
    }

    @Test
    void failingResourceDoesNotStopOthers() {
        resources.attach(() -> events.add("a"));
        resources.attach(() -> {
            throw new IllegalStateException("boom");
        });
        resources.attach(() -> events.add("c"));
        resources.close();
        assertEquals(List.of("c", "a"), events);
        assertEquals(1, errors.size());
        assertEquals("boom", errors.get(0).getMessage());
    }

    @Test
    void closeIsIdempotent() {
        resources.attach(() -> events.add("a"));
        resources.close();
        resources.close();
        assertEquals(List.of("a"), events);
    }

    @Test
    void attachAfterCloseThrows() {
        resources.close();
        assertThrows(IllegalStateException.class, () -> resources.attach(() -> { }));
    }

    @Test
    void attachNullThrows() {
        assertThrows(NullPointerException.class, () -> resources.attach(null));
    }
}
