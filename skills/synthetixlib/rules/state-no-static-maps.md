---
title: Per-player state is an instance field, cleaned on quit, thread-safe
impact: CRITICAL
tags: state, folia, memory
---

## Per-player state is an instance field, cleaned on quit, thread-safe

SnapPlug kept per-player data in static maps that were never cleared and raced across threads. Keep it in a concurrent map owned by an instance, remove the entry on `PlayerQuitEvent`, or store it in PDC so it needs no map at all.

**❌ Incorrect**

```java
private static final Map<UUID, Session> SESSIONS = new HashMap<>();
```

**✅ Correct**

```java
private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

@EventHandler
public void onQuit(PlayerQuitEvent event) {
    sessions.remove(event.getPlayer().getUniqueId());
}
```

Or store the value on the player with a `PdcKey` (see `core-items-data-components`).
