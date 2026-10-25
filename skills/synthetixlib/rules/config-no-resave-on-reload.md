---
title: Never re-save the config on reload
impact: HIGH
tags: config, reload
---

## Never re-save the config on reload

SnapPlug rewrote the file on every reload and destroyed the owner's comments and edits. A plain reload must only read; the library writes only when the file is created, migrated or when keys were added.

**❌ Incorrect**

```java
public void reload() {
    plugin.reloadConfig();
    plugin.saveConfig();      // rewrites and drops the owner's comments
}
```

**✅ Correct**

```java snippet=config-reload-result
final ReloadResult result = handle.reload();                      // never rewrites the file
result.errors().forEach(error -> lib.plugin().getLogger().warning(error));
handle.reloadAsync().thenAccept(async -> lib.plugin().getLogger().info("changed: " + async.changed()));
```
