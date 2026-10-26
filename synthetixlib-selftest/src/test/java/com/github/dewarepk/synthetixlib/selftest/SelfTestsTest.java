package com.github.dewarepk.synthetixlib.selftest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SelfTestsTest {

    /** A host with a manual global thread: tasks run when the test drains them. */
    static final class FakeHost implements SelfTestHost {
        final List<Runnable> loadActions = new ArrayList<>();
        final Deque<Runnable> queue = new ArrayDeque<>();
        final List<Runnable> delayed = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();
        final List<String> logged = new ArrayList<>();
        @Nullable String written;
        boolean shutdown;

        @Override
        public void onServerLoad(final Runnable action) {
            loadActions.add(action);
        }

        @Override
        public void runGlobal(final Runnable action) {
            queue.add(action);
        }

        @Override
        public void runGlobalLater(final long delayTicks, final Runnable action) {
            delays.add(delayTicks);
            delayed.add(action);
        }

        @Override
        public void info(final String message) {
            logged.add(message);
        }

        @Override
        public void severe(final String message, final @Nullable Throwable error) {
            logged.add(message);
        }

        @Override
        public String minecraftVersion() {
            return "1.21.test";
        }

        @Override
        public void writeResult(final String json) {
            written = json;
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        void drain() {
            while (!queue.isEmpty()) {
                queue.poll().run();
            }
        }
    }

    @AfterEach
    void clearProperties() {
        System.clearProperty(SelfTests.ENABLE_PROPERTY);
        System.clearProperty(SelfTests.CAP_PROPERTY);
    }

    @Test
    void withoutThePropertyNothingIsRegistered() {
        final FakeHost host = new FakeHost();
        new SelfTests(host).add(SelfTest.sync("a", () -> { })).runIfRequested();
        assertTrue(host.loadActions.isEmpty());
        assertTrue(host.logged.isEmpty());
        assertTrue(host.queue.isEmpty());
        assertNull(host.written);
        assertFalse(host.shutdown);
    }

    @Test
    void withThePropertyTheRunStartsOnServerLoadAndStopsTheServer() {
        System.setProperty(SelfTests.ENABLE_PROPERTY, "true");
        final FakeHost host = new FakeHost();
        final List<String> order = new ArrayList<>();
        new SelfTests(host)
                .before(() -> order.add("before"))
                .add(SelfTest.sync("one", () -> order.add("one")))
                .addLater(() -> {
                    order.add("later-built");
                    return List.of(SelfTest.sync("two", () -> order.add("two")));
                })
                .runIfRequested();
        assertEquals(1, host.loadActions.size());
        assertTrue(order.isEmpty(), "nothing runs before the server loaded");

        host.loadActions.get(0).run();
        host.drain();

        assertEquals(List.of("before", "later-built", "one", "two"), order);
        assertEquals(List.of(120L * 20L), host.delays);
        assertTrue(host.shutdown);
        assertTrue(host.written.startsWith("{\"status\":\"PASS\""), host.written);
        assertTrue(host.written.contains("\"total\":2,\"failed\":0,\"skipped\":0"), host.written);
    }

    @Test
    void failuresAndSkipsAreCountedSeparately() {
        System.setProperty(SelfTests.ENABLE_PROPERTY, "true");
        final FakeHost host = new FakeHost();
        new SelfTests(host)
                .add(SelfTest.sync("ok", () -> { }))
                .add(SelfTest.sync("bad", () -> Checks.equal(1, 2, "answer")))
                .add(SelfTest.sync("skip", () -> SelfTest.skip("needs a \"player\"")))
                .add(SelfTest.async("async-bad", () -> CompletableFuture.failedFuture(new IllegalStateException("boom"))))
                .runIfRequested();
        host.loadActions.get(0).run();
        host.drain();

        final String json = host.written;
        assertTrue(json.startsWith("{\"status\":\"FAIL\",\"minecraftVersion\":\"1.21.test\",\"total\":4,\"failed\":2,\"skipped\":1,"),
                json);
        assertTrue(json.contains("\"error\":\"AssertionError: answer: expected <1> but was <2>\""), json);
        assertTrue(json.contains("{\"name\":\"skip\",\"passed\":true,\"skipped\":true,"), json);
        assertTrue(json.contains("\"reason\":\"needs a \\\"player\\\"\""), json);
        assertTrue(json.contains("\"error\":\"IllegalStateException: boom\""), json);
        assertTrue(host.shutdown);
    }

    @Test
    void failingSetupEndsTheRunWithoutRunningTests() {
        System.setProperty(SelfTests.ENABLE_PROPERTY, "true");
        final FakeHost host = new FakeHost();
        final List<String> ran = new ArrayList<>();
        new SelfTests(host)
                .before(() -> {
                    throw new IllegalStateException("no world");
                })
                .add(SelfTest.sync("never", () -> ran.add("never")))
                .runIfRequested();
        host.loadActions.get(0).run();
        host.drain();

        assertTrue(ran.isEmpty());
        assertTrue(host.written.startsWith("{\"status\":\"FAIL\""), host.written);
        assertTrue(host.written.contains("\"name\":\"selftest.before\""), host.written);
        assertTrue(host.shutdown);
    }

    @Test
    void globalCapFailsTheStuckTestAndTheRest() {
        System.setProperty(SelfTests.ENABLE_PROPERTY, "true");
        System.setProperty(SelfTests.CAP_PROPERTY, "5");
        final FakeHost host = new FakeHost();
        new SelfTests(host)
                .add(SelfTest.sync("first", () -> { }))
                .add(SelfTest.async("stuck", Duration.ofHours(1), CompletableFuture::new))
                .add(SelfTest.sync("after", () -> { }))
                .runIfRequested();
        host.loadActions.get(0).run();
        host.drain();
        assertEquals(List.of(100L), host.delays);
        assertFalse(host.shutdown, "still waiting for the stuck test");

        host.delayed.get(0).run();
        host.drain();

        final String json = host.written;
        assertTrue(json.startsWith("{\"status\":\"FAIL\""), json);
        assertTrue(json.contains("\"total\":3,\"failed\":2"), json);
        assertTrue(json.contains("global run cap (5 s) reached"), json);
        assertTrue(json.contains("not run: global run cap (5 s) reached"), json);
        assertTrue(host.shutdown);
    }

    @Test
    void jsonEscapesControlCharacters() {
        final String json = SelfTestResult.toJson("v", List.of(SelfTestResult.failed("n", 1, "a\nb\t\"c\"\\\u0001")));
        assertTrue(json.contains("\"error\":\"a\\nb\\t\\\"c\\\"\\\\\\u0001\""), json);
    }

    @Test
    void checksHaveReadableMessages() {
        assertEquals("amount: expected <1> but was <2>",
                assertThrows(AssertionError.class, () -> Checks.equal(1, 2, "amount")).getMessage());
        assertEquals("open: expected true but was false",
                assertThrows(AssertionError.class, () -> Checks.isTrue(false, "open")).getMessage());
        Checks.throwsType(IllegalArgumentException.class, () -> {
            throw new IllegalArgumentException("x");
        });
        Checks.throwsType(RuntimeException.class, () -> {
            throw new IllegalStateException("subclass is fine");
        });
        assertTrue(assertThrows(AssertionError.class, () -> Checks.throwsType(IllegalStateException.class, () -> { }))
                .getMessage().contains("nothing was thrown"));
        assertTrue(assertThrows(AssertionError.class, () -> Checks.throwsType(IllegalStateException.class, () -> {
            throw new IllegalArgumentException("other");
        })).getMessage().contains("IllegalArgumentException was thrown: other"));
    }
}
