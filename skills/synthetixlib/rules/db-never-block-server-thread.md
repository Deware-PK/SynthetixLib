---
title: Database results are futures; never join on a server thread
impact: CRITICAL
tags: database, threads
---

## Database results are futures; never join on a server thread

Statements run on the database worker. `join()` / `get()` on a server thread freezes the tick (or a Folia region); only `onEnable` / `onDisable` may wait. Chain with `thenAccept` and hop back with `lib.scheduler()` before touching Bukkit.

**❌ Incorrect**

```java
final List<Lot> lots = db.query("SELECT ...", Lot::fromRow).join();   // in a command or event
player.sendMessage("lots: " + lots.size());
```

**✅ Correct**

```java snippet=db-query-chain
/** Reads the count and the last {@code limit} claimers, newest first. */
public CompletableFuture<Stats> stats(final String airdrop, final int limit) {
    return db.queryOne("SELECT COUNT(*) AS n FROM {prefix}claims WHERE airdrop = ?",
                    row -> row.longValue("n"), airdrop)
            .thenCompose(count -> db.query(
                            "SELECT player FROM {prefix}claims WHERE airdrop = ? ORDER BY id DESC LIMIT " + limit,
                            row -> row.uuid("player"), airdrop)
                    .thenApply(players -> new Stats(count.orElse(0L), players)));
}
```
