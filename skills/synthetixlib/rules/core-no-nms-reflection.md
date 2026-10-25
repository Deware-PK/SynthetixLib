---
title: No NMS, CraftBukkit, reflection or Class.forName
impact: CRITICAL
tags: core, compatibility
---

## No NMS, CraftBukkit, reflection or Class.forName

NMS and CraftBukkit internals change every Minecraft release and between 1.21.11 and 26.x; reflection and class names in strings also break under relocation. Use Paper API, data components and Adventure instead.

**❌ Incorrect**

```java
Class<?> craft = Class.forName("org.bukkit.craftbukkit.v1_21_R1.CraftServer");
Object handle = player.getClass().getMethod("getHandle").invoke(player);
```

**✅ Correct**

```java
player.showTitle(Title.title(title, subtitle));   // Paper / Adventure API
lib.items().of(Material.PAPER).itemModel(model).build();
```
