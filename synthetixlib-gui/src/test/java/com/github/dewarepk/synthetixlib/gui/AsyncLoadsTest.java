package com.github.dewarepk.synthetixlib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** The order of events of an async list, with executors that run only when the test says so. */
class AsyncLoadsTest {

    private final Queue<Runnable> asyncQueue = new ArrayDeque<>();
    private final Queue<Runnable> viewerQueue = new ArrayDeque<>();
    private final AtomicBoolean disposed = new AtomicBoolean();
    private final AtomicInteger arrived = new AtomicInteger();
    private final AtomicInteger asked = new AtomicInteger();
    private final List<String> warnings = new ArrayList<>();

    private AsyncLoads loads(final Function<String, List<?>> source) {
        return new AsyncLoads(asyncQueue::add, viewerQueue::add, disposed::get, warnings::add, id -> {
            asked.incrementAndGet();
            return source.apply(id);
        }, arrived::incrementAndGet);
    }

    private void runAsync() {
        asyncQueue.remove().run();
    }

    private void runViewer() {
        viewerQueue.remove().run();
    }

    @Test
    void theFirstPollHasNoAnswerAndTheSourceRunsOnTheAsyncExecutorOnly() {
        final AsyncLoads loads = loads(id -> List.of("a", "b"));

        assertNull(loads.poll("lots", true));
        assertEquals(0, asked.get(), "the source must not run on the polling thread");
        runAsync();
        assertEquals(1, asked.get());
        assertEquals(0, arrived.get(), "the answer is delivered on the viewer's thread, not the async one");
        assertNull(loads.poll("lots", false));

        runViewer();
        assertEquals(1, arrived.get());
        assertEquals(List.of("a", "b"), loads.poll("lots", false));
        assertTrue(asyncQueue.isEmpty());
    }

    @Test
    void anAnswerThatArrivesAfterTheViewEndedIsDropped() {
        final AsyncLoads loads = loads(id -> List.of("a"));
        loads.poll("lots", true);
        runAsync();

        disposed.set(true);
        runViewer();

        assertEquals(0, arrived.get());
        assertNull(loads.poll("lots", false));
        assertTrue(asyncQueue.isEmpty(), "an ended view asks for nothing more");
    }

    @Test
    void aViewThatEndedBeforeTheFirstPollAsksNothing() {
        final AsyncLoads loads = loads(id -> List.of("a"));
        disposed.set(true);

        assertNull(loads.poll("lots", true));
        assertTrue(asyncQueue.isEmpty());
    }

    @Test
    void whileAnswerIsOnItsWayAnotherPollDoesNotAskAgain() {
        final AsyncLoads loads = loads(id -> List.of("a"));

        loads.poll("lots", true);
        loads.poll("lots", true);
        loads.poll("lots", false);

        assertEquals(1, asyncQueue.size());
    }

    @Test
    void aReloadKeepsShowingTheOldAnswerUntilTheNewOneArrives() {
        final AtomicInteger round = new AtomicInteger();
        final AsyncLoads loads = loads(id -> List.of("round " + round.incrementAndGet()));
        loads.poll("lots", true);
        runAsync();
        runViewer();

        assertEquals(List.of("round 1"), loads.poll("lots", true));
        assertEquals(1, asyncQueue.size(), "the reload was started");
        runAsync();
        assertEquals(List.of("round 1"), loads.poll("lots", false));
        runViewer();

        assertEquals(List.of("round 2"), loads.poll("lots", false));
        assertEquals(2, arrived.get());
    }

    @Test
    void aPollWithoutReloadDoesNotAskAgainOnceThereIsAnAnswer() {
        final AsyncLoads loads = loads(id -> List.of("a"));
        loads.poll("lots", true);
        runAsync();
        runViewer();

        loads.poll("lots", false);

        assertTrue(asyncQueue.isEmpty());
    }

    @Test
    void aSourceThatThrowsGivesAnEmptyListAndOneWarning() {
        final AsyncLoads loads = loads(id -> {
            throw new IllegalStateException("db down");
        });
        loads.poll("lots", true);
        runAsync();
        runViewer();

        assertEquals(List.of(), loads.poll("lots", false));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("lots") && warnings.get(0).contains("db down"));
    }

    @Test
    void aClosedSchedulerLeavesTheListLoadingWithoutThrowing() {
        final AsyncLoads loads = new AsyncLoads(task -> {
            throw new IllegalStateException("scheduler closed");
        }, viewerQueue::add, disposed::get, warnings::add, id -> List.of("a"), arrived::incrementAndGet);

        assertNull(loads.poll("lots", true));
        assertNull(loads.poll("lots", true), "the failed start is forgotten, a later poll may try again");
    }

    @Test
    void listsAreIndependent() {
        final AsyncLoads loads = loads(id -> List.of(id));
        loads.poll("a", true);
        loads.poll("b", true);
        runAsync();
        runAsync();
        runViewer();
        runViewer();

        assertEquals(List.of("a"), loads.poll("a", false));
        assertEquals(List.of("b"), loads.poll("b", false));
    }
}
