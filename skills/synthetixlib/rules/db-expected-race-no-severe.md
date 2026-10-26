---
title: An expected race rolls back on purpose, it does not throw
impact: MEDIUM
tags: database, transactions
---

## An expected race rolls back on purpose, it does not throw

A transaction body that finds the data in another state than it expected (the lot ended, another bid came first) is a normal outcome. Throwing a plain `RuntimeException` logs a `SEVERE` stack trace for every lost race, and server owners report it as a crash. Call `tx.rollbackOnly()` and return a result type, or throw `TransactionAbortedException` (logged at `FINE`).

**❌ Incorrect**

```java
db.transaction(tx -> {
    if (tx.update("UPDATE {prefix}lots SET state = 'SOLD' WHERE id = ? AND state = 'RUNNING'", id) == 0) {
        throw new IllegalStateException("lot is no longer running");   // SEVERE + stack in the console
    }
    return null;
});
```

**✅ Correct**

```java snippet=db-rollback-only
final CompletableFuture<Sale> sale = db.transaction(tx -> {
    final int changed = tx.update("UPDATE {prefix}lots SET state = 'SOLD' WHERE id = ? AND state = 'RUNNING'", lotId);
    if (changed == 0) {
        tx.rollbackOnly();                       // expected race: roll back, no exception, nothing logged
        return Sale.STALE;
    }
    tx.insert("INSERT INTO {prefix}mail (player, note) VALUES (?, ?)", UUID.randomUUID(), "sold");
    return Sale.SOLD;
});

// or, when the caller prefers an exception (logged at FINE, never SEVERE):
db.transaction(tx -> {
    if (tx.update("UPDATE {prefix}lots SET state = 'ENDED' WHERE id = ? AND state = 'RUNNING'", lotId) == 0) {
        throw new TransactionAbortedException("lot " + lotId + " is no longer running");
    }
    return null;
});
```
