---
title: Never block the server thread
impact: HIGH
tags: threads, performance
---

## Never block the server thread

`future.join()`, `Thread.sleep` and blocking IO on the server thread stall the tick and can deadlock when the future completes on the same thread. Compose with `whenComplete` and hop back with the scheduler.

**❌ Incorrect**

```java
var stats = claims.stats(id, 5).join();     // on the server thread
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
