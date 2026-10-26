package com.github.dewarepk.synthetixlib.selftest;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * One in-server check, either synchronous ({@link #sync}) or asynchronous ({@link #async}). Ids follow
 * {@code <area>.<feature>}, e.g. {@code auction.bid-takes-money}. Run them with {@link SelfTests}.
 *
 * <pre>{@code
 * SelfTest.sync("items.round-trip", () -> Checks.equal(3, serialize(stack).getAmount(), "amount"));
 * SelfTest.async("db.saves-order", () -> store.save(order));          // passes when the future completes
 * SelfTest.withPlayer("gui.opens-for-player", player -> menu.open(player)); // SKIP when nobody is online
 * }</pre>
 *
 * <p>Bodies run on the global region thread. Never block it: complete futures through the scheduler instead of
 * {@code join()}/{@code get()}.
 */
public final class SelfTest {

    /** Timeout used by {@link #async(String, Supplier)}. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    /** The body of a synchronous check. Throw (e.g. {@link AssertionError}) to fail. */
    @FunctionalInterface
    public interface Check {
        void run() throws Exception;
    }

    /** The body of a check that needs a player. */
    @FunctionalInterface
    public interface PlayerCheck {
        void run(Player player) throws Exception;
    }

    /** Thrown by {@link #skip}: the test could not run here (counted as SKIP, never as a failure). */
    public static final class Skipped extends RuntimeException {

        private static final long serialVersionUID = 1L;

        Skipped(final String reason) {
            super(reason, null, false, false);
        }
    }

    private final String name;
    private final Duration timeout;
    private final Supplier<CompletableFuture<?>> body;

    private SelfTest(final String name, final Duration timeout, final Supplier<CompletableFuture<?>> body) {
        this.name = Objects.requireNonNull(name, "name");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.body = Objects.requireNonNull(body, "body");
    }

    /**
     * Synchronous check on the global region thread.
     *
     * @param name  the test id
     * @param check the body; throw to fail
     * @return the test
     */
    public static SelfTest sync(final String name, final Check check) {
        Objects.requireNonNull(check, "check");
        return new SelfTest(name, DEFAULT_TIMEOUT, () -> {
            try {
                check.run();
            } catch (final Exception e) {
                return CompletableFuture.failedFuture(e);
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Check that needs a player. Runs with the first online player; when nobody is online the test is SKIPPED (counted
     * separately in the result, never a failure). The kit does not simulate clients: join the dev server to run it.
     *
     * @param name  the test id
     * @param check the body; throw to fail
     * @return the test
     */
    public static SelfTest withPlayer(final String name, final PlayerCheck check) {
        Objects.requireNonNull(check, "check");
        return sync(name, () -> {
            final Player player = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
            if (player == null) {
                skip("needs an online player");
            }
            check.run(player);
        });
    }

    /**
     * Async check with {@link #DEFAULT_TIMEOUT}.
     *
     * @param name the test id
     * @param body called on the global region thread; returns the future that must complete normally
     * @return the test
     */
    public static SelfTest async(final String name, final Supplier<CompletableFuture<?>> body) {
        return async(name, DEFAULT_TIMEOUT, body);
    }

    /**
     * Async check: passes when the future completes normally within {@code timeout}; fails on exceptional completion,
     * timeout, or if {@code body} throws while creating the future. {@code body} is called on the global region thread;
     * never block it.
     *
     * @param name    the test id
     * @param timeout how long the future may take
     * @param body    returns the future
     * @return the test
     */
    public static SelfTest async(final String name, final Duration timeout,
                                 final Supplier<CompletableFuture<?>> body) {
        return new SelfTest(name, timeout, body);
    }

    /**
     * Marks the running test as skipped, e.g. because a plugin it needs is not installed on this server.
     *
     * @param reason why it cannot run here
     * @throws Skipped always
     */
    public static void skip(final String reason) {
        throw new Skipped(reason);
    }

    /**
     * Fails the running test.
     *
     * @param condition must be true
     * @param message   the failure text
     */
    public static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** @return the test id */
    public String name() {
        return name;
    }

    /** @return how long an async body may take */
    public Duration timeout() {
        return timeout;
    }

    Supplier<CompletableFuture<?>> body() {
        return body;
    }
}
