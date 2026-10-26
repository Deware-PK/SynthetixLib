---
title: Use actionAsync for menu actions that do database or HTTP work
impact: HIGH
tags: gui, async, dupe
---

## Use actionAsync for menu actions that do database or HTTP work

A plain `action` ends when the handler returns. If the handler only starts a future, the view lets the next click in at once, so a double click claims a mail item twice. Do not patch this with your own "in flight" set (a static or per-player map breaks the lib rules and every product would write it differently). `actionAsync` keeps the view busy until the future completes, fails or times out. Your database update must still be atomic (`... WHERE claimed_at IS NULL`): the gate covers one view only.

**❌ Incorrect**

```java
private final Set<UUID> claiming = ConcurrentHashMap.newKeySet();   // hand-made gate, easy to leak

.action("claim", click -> {
    if (!claiming.add(click.player().getUniqueId())) {
        return;
    }
    claims.claim(click.player().getUniqueId()).whenComplete((count, error) -> claiming.remove(click.player().getUniqueId()));
})
```

**✅ Correct**

```java snippet=gui-action-async
/**
 * Registered with {@code actionAsync("claim", click -> claim(claims, click))}: the view ignores every click until the
 * database answers, so a double click cannot claim twice (the insert itself must still be safe to repeat).
 */
private static CompletableFuture<?> claim(final ClaimStore claims, final MenuClick<Void> click) {
    return claims.claim(DEMO_AIRDROP, click.player().getUniqueId()).thenRun(() -> click.view().refresh());
}
```
