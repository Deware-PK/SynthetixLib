package com.github.dewarepk.synthetixexample.selftest;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * One in-server check, either synchronous ({@link #sync}) or asynchronous ({@link #async}).
 * Ids follow {@code <module>.<feature>}, e.g. {@code core.init}.
 */
public final class SelfTest {

    /** Timeout used by {@link #async(String, Supplier)}. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    @FunctionalInterface
    public interface Check {
        void run() throws Exception;
    }

    private final String name;
    private final Duration timeout;
    private final Supplier<CompletableFuture<?>> body;

    private SelfTest(final String name, final Duration timeout, final Supplier<CompletableFuture<?>> body) {
        this.name = name;
        this.timeout = timeout;
        this.body = body;
    }

    /** Synchronous check on the global region thread. Throw (e.g. {@link AssertionError}) to fail. */
    public static SelfTest sync(final String name, final Check check) {
        return new SelfTest(name, DEFAULT_TIMEOUT, () -> {
            try {
                check.run();
            } catch (final Exception e) {
                return CompletableFuture.failedFuture(e);
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    /** Async check with {@link #DEFAULT_TIMEOUT}. */
    public static SelfTest async(final String name, final Supplier<CompletableFuture<?>> body) {
        return async(name, DEFAULT_TIMEOUT, body);
    }

    /**
     * Async check: passes when the future completes normally within {@code timeout}; fails on
     * exceptional completion, timeout, or if {@code body} throws while creating the future.
     * {@code body} is called on the global region thread; never block it.
     */
    public static SelfTest async(final String name, final Duration timeout,
                                 final Supplier<CompletableFuture<?>> body) {
        return new SelfTest(name, timeout, body);
    }

    public String name() {
        return name;
    }

    public Duration timeout() {
        return timeout;
    }

    Supplier<CompletableFuture<?>> body() {
        return body;
    }

    public static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
