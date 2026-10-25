---
title: Do IO async, then return to the owning thread
impact: CRITICAL
tags: threads, async, command
---

## Do IO async, then return to the owning thread

Blocking the server thread with a database or HTTP call freezes the server (or a Folia region). Run it with `executesAsync` or `lib.scheduler().async()`, then use `ctx.runOnSender` or the entity/region scheduler before you touch Bukkit API with the result.

**❌ Incorrect**

```java
.executes(ctx -> {
    var rows = database.queryBlocking(...);   // blocks the tick
    ctx.reply("done");
})
```

**✅ Correct**

```java snippet=command-async
@Override
public SubSpec spec() {
    return SubSpec.literal("ban").argument(target).optional(time, Duration.ofDays(1))
            .usage("<player> [time]").description("Ban a player")
            .executesAsync(this::run);
}

private void run(final CommandCall ctx) {
    final Player player = ctx.get(target);
    final Duration length = ctx.getOrDefault(time, Duration.ofDays(1));
    storeBan(player, length);                                     // blocking is fine here
    ctx.runOnSender(() -> ctx.reply(lang, Messages::reloaded, Placeholder.component("player", player.name())));
}
```
