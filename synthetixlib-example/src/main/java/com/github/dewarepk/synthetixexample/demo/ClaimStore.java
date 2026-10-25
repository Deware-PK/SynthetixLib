package com.github.dewarepk.synthetixexample.demo;

import com.github.dewarepk.synthetixlib.database.Database;
import com.github.dewarepk.synthetixlib.database.Migrations;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Airdrop claims in the database. The counts are also kept in memory (written through on every claim) so a menu can show
 * them without waiting for the database.
 */
public final class ClaimStore {

    /** The claims of one airdrop. */
    public record Stats(long count, List<UUID> lastClaimers) {
    }

    private final Database db;
    private final Map<String, Long> counts = new ConcurrentHashMap<>();

    private ClaimStore(final Database db) {
        this.db = db;
    }

    // @snippet:db-open-migrate
    /** Creates the table if needed and loads the counts; blocks, so call it from {@code onEnable}. */
    public static ClaimStore open(final Database db) {
        db.migrate(Migrations.fromResources("db/demo")).join();
        final ClaimStore store = new ClaimStore(db);
        db.query("SELECT airdrop, COUNT(*) AS n FROM {prefix}claims GROUP BY airdrop",
                        row -> Map.entry(row.string("airdrop"), row.longValue("n")))
                .join()
                .forEach(entry -> store.counts.put(entry.getKey(), entry.getValue()));
        return store;
    }
    // @end

    // @snippet:db-update
    /** Writes one claim; the future completes with the new count. */
    public CompletableFuture<Long> claim(final String airdrop, final UUID player) {
        return db.update("INSERT INTO {prefix}claims (airdrop, player, claimed_at) VALUES (?, ?, ?)",
                        airdrop, player, Instant.now())
                .thenApply(rows -> counts.merge(airdrop, 1L, Long::sum));
    }
    // @end

    // @snippet:db-query-chain
    /** Reads the count and the last {@code limit} claimers, newest first. */
    public CompletableFuture<Stats> stats(final String airdrop, final int limit) {
        return db.queryOne("SELECT COUNT(*) AS n FROM {prefix}claims WHERE airdrop = ?",
                        row -> row.longValue("n"), airdrop)
                .thenCompose(count -> db.query(
                                "SELECT player FROM {prefix}claims WHERE airdrop = ? ORDER BY id DESC LIMIT " + limit,
                                row -> row.uuid("player"), airdrop)
                        .thenApply(players -> new Stats(count.orElse(0L), players)));
    }
    // @end

    /** @return the number of claims of an airdrop, from memory; any thread */
    public long count(final String airdrop) {
        return counts.getOrDefault(airdrop, 0L);
    }
}
