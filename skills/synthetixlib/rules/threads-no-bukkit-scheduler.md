---
title: Schedule only through lib.scheduler()
impact: CRITICAL
tags: threads, folia, scheduler
---

## Schedule only through lib.scheduler()

`Bukkit.getScheduler()` and `runTask*` throw on Folia and ignore regions. The facade works on Paper and Folia: global, region (location), entity, async. It also tracks tasks so `close()` cancels them.

**❌ Incorrect**

```java
Bukkit.getScheduler().runTaskLater(plugin, () -> block.setType(Material.GOLD_BLOCK), 20);
```

**✅ Correct**

```java snippet=core-scheduler
final Scheduler scheduler = lib.scheduler();

scheduler.global().runLater(40, () -> lib.plugin().getLogger().info("two seconds later"));
final SynthetixTask timer = scheduler.global().runTimer(0, 20, task -> {
    if (lib.isClosed()) {
        task.cancel();
    }
});

scheduler.region(location).run(() -> block.setType(Material.GOLD_BLOCK));
scheduler.entity(player).run(() -> player.sendMessage("hi"), () -> lib.plugin().getLogger().info("player left"));

scheduler.async().run(() -> lib.plugin().getLogger().info("off the server thread"));
scheduler.async().runTimer(Duration.ZERO, Duration.ofMinutes(1), task -> lib.plugin().getLogger().info("autosave"));
```
