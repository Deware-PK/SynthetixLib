package com.github.dewarepk.synthetixlib.command;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Who is on cooldown for which node, for one {@link CommandService}. One flat map keyed by player and node, so every
 * operation is a single atomic map operation: threads of different players never touch the same key, and a quit
 * racing with a start can only lose or keep that one entry, never corrupt another.
 *
 * <p>Entries expire by comparing against the clock, are dropped when they are looked at after expiry
 * ({@link #left}), when the player quits ({@link #clear}) and by the periodic {@link #sweep()} (which also catches
 * entries started for a player who quit while an async handler was still running).
 */
class Cooldowns {

    /** Longest stored cooldown (about 73 years), so the expiry can never overflow a {@code long}. */
    private static final long MAX_NANOS = Long.MAX_VALUE / 4;

    private record Key(UUID player, String node) {
    }

    private final LongSupplier nanos;
    private final ConcurrentHashMap<Key, Long> expiries = new ConcurrentHashMap<>();
    /** Async runs in progress; never touched by {@link #sweep()}, only by their own release. */
    private final ConcurrentHashMap<Key, Object> inFlight = new ConcurrentHashMap<>();

    /** @param nanos a monotonic clock in nanoseconds, {@code System::nanoTime} outside tests */
    Cooldowns(final LongSupplier nanos) {
        this.nanos = nanos;
    }

    /** @return how long {@code player} must still wait for {@code node}; {@link Duration#ZERO} if not at all */
    Duration left(final UUID player, final String node) {
        final Key key = new Key(player, node);
        final Long expiry = expiries.get(key);
        if (expiry == null) {
            return Duration.ZERO;
        }
        final long left = expiry - nanos.getAsLong();
        if (left <= 0) {
            expiries.remove(key, expiry);
            return Duration.ZERO;
        }
        return Duration.ofNanos(left);
    }

    /** Puts {@code player} on cooldown for {@code node}, replacing an earlier entry. */
    void start(final UUID player, final String node, final Duration duration) {
        long length;
        try {
            length = Math.min(MAX_NANOS, duration.toNanos());
        } catch (final ArithmeticException e) {
            length = MAX_NANOS;
        }
        expiries.put(new Key(player, node), nanos.getAsLong() + length);
    }

    /** Takes {@code player} off cooldown for {@code node}. */
    void reset(final UUID player, final String node) {
        expiries.remove(new Key(player, node));
    }

    /**
     * Reserves {@code node} for {@code player} while an async handler runs. Atomic: of two racing callers exactly one
     * gets a token.
     *
     * @return the token to {@link #release} with, {@code null} if the player already has a run of this node in flight
     */
    @Nullable Object reserve(final UUID player, final String node) {
        final Object token = new Object();
        return inFlight.putIfAbsent(new Key(player, node), token) == null ? token : null;
    }

    /**
     * Ends a reservation. Does nothing if it belongs to a newer run, which is why it needs the token.
     */
    void release(final UUID player, final String node, final Object token) {
        inFlight.remove(new Key(player, node), token);
    }

    /**
     * Forgets the cooldowns of {@code player}; called when they quit. Reservations stay: a handler that is still
     * running must keep blocking a second run after quit + rejoin, and ends its own reservation in its {@code finally}.
     */
    void clear(final UUID player) {
        expiries.keySet().removeIf(key -> key.player().equals(player));
    }

    /** Forgets everything; called when the service closes. */
    void clearAll() {
        expiries.clear();
        inFlight.clear();
    }

    /** Drops every expired entry. */
    void sweep() {
        final long now = nanos.getAsLong();
        expiries.entrySet().removeIf(entry -> entry.getValue() - now <= 0);
    }

    /** @return how many entries are stored, expired or not */
    int size() {
        return expiries.size();
    }

    /** @return how many async runs hold a reservation */
    int inFlightSize() {
        return inFlight.size();
    }
}
