---
title: Refuse with ctx.fail, do not track cooldowns yourself
impact: MEDIUM
tags: command, cooldown
---

## Refuse with ctx.fail, do not track cooldowns yourself

`throw ctx.fail(...)` (and any exception) leaves the cooldown unused: the player is not punished for a refused run. Do not
keep your own cooldown map to get that, or a reloadable length, or an admin reset: `cooldown(sender -> Duration)` is asked at
every successful run and `commandService.cooldowns().reset(uuid, "kit")` clears one player.

**❌ Incorrect**

```java
private final Map<UUID, Long> readyAt = new HashMap<>();   // own map: not thread-safe, never cleaned, not reloadable
```

**✅ Correct**

```java snippet=command-cooldown-reload
SubSpec kit(final ConfigHandle<KitSettings> settings) {
    return SubSpec.literal("kit")
            .cooldown(sender -> settings.get().kitWait(), "kit.bypass")   // asked at every successful run
            .executes(ctx -> ctx.reply("<green>Here is your kit"));
}
```
