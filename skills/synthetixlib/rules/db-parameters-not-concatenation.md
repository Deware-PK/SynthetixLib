---
title: Bind values with ?, never concatenate SQL
impact: CRITICAL
tags: database, security
---

## Bind values with ?, never concatenate SQL

String-built SQL is an injection hole and breaks on quotes in names. Use positional `?` parameters; the library converts `UUID`, `Instant`, enums and `ItemStack` for you. Table names get their prefix through `{prefix}`, and the prefix itself is validated.

**❌ Incorrect**

```java
db.update("UPDATE ia_lots SET seller = '" + name + "' WHERE id = " + id);
```

**✅ Correct**

```java snippet=db-update
/** Writes one claim; the future completes with the new count. */
public CompletableFuture<Long> claim(final String airdrop, final UUID player) {
    return db.update("INSERT INTO {prefix}claims (airdrop, player, claimed_at) VALUES (?, ?, ?)",
                    airdrop, player, Instant.now())
            .thenApply(rows -> counts.merge(airdrop, 1L, Long::sum));
}
```
