---
title: Commands keep no shared mutable state
impact: CRITICAL
tags: command, folia, thread-safety
---

## Commands keep no shared mutable state

A command spec is built once and executed concurrently on region threads (Folia). A field that a handler writes ("current target", a counter) is a race. Per-call data lives in `CommandCall`; shared state belongs in a thread-safe service.

**❌ Incorrect**

```java
private Player lastTarget;                 // written by every run
private void run(CommandCall ctx) { lastTarget = ctx.get(target); }
```

**✅ Correct**

```java snippet=command-callback
private void run(final CommandCall ctx) {
    final String value = ctx.get(id);
    if (!airdrops.ids().contains(value)) {
        throw ctx.fail(lang, Messages::airdropNotFound, Placeholder.unparsed("id", value));
    }
    final UUID player = ctx.player().getUniqueId();
    claims.claim(value, player).whenComplete((count, failure) -> ctx.runOnSender(() -> {
        if (failure != null) {
            ctx.lib().plugin().getLogger().warning("claim failed: " + failure);
            ctx.reply(lang, Messages::dbFailed);
        } else {
            ctx.reply(lang, Messages::airdropClaimed,
                    Placeholder.unparsed("id", value), Placeholder.unparsed("count", String.valueOf(count)));
        }
    }));
}
```
