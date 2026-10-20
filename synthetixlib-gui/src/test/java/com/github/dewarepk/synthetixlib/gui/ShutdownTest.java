package com.github.dewarepk.synthetixlib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Closing every view on shutdown: the listener stops after the last view, whatever happens to the views. */
class ShutdownTest {

    @Test
    void everyViewIsClosedBeforeTheListenerStops() {
        final List<String> order = new ArrayList<>();

        Shutdown.run(List.of("a", "b", "c"), view -> order.add("close " + view), (view, e) -> order.add("failed " + view),
                () -> order.add("unregister"));

        assertEquals(List.of("close a", "close b", "close c", "unregister"), order);
    }

    @Test
    void aViewThatCannotBeClosedGetsTheFallbackAndTheOthersAreStillClosed() {
        final List<String> order = new ArrayList<>();

        Shutdown.run(List.of("a", "b", "c"), view -> {
            if (view.equals("b")) {
                throw new IllegalStateException("cannot be scheduled");
            }
            order.add("close " + view);
        }, (view, e) -> order.add("wipe " + view + " (" + e.getMessage() + ")"), () -> order.add("unregister"));

        assertEquals(List.of("close a", "wipe b (cannot be scheduled)", "close c", "unregister"), order);
    }

    @Test
    void theListenerStopsEvenIfTheFallbackFails() {
        final List<String> order = new ArrayList<>();

        assertThrows(IllegalArgumentException.class, () -> Shutdown.run(List.of("a", "b"), view -> {
            throw new IllegalStateException("no");
        }, (view, e) -> {
            throw new IllegalArgumentException("wipe failed too");
        }, () -> order.add("unregister")));

        assertEquals(List.of("unregister"), order);
    }

    @Test
    void noViewsStillStopsTheListener() {
        final List<String> order = new ArrayList<>();

        Shutdown.run(List.<String>of(), view -> order.add("close"), (view, e) -> order.add("failed"),
                () -> order.add("unregister"));

        assertEquals(List.of("unregister"), order);
    }
}
