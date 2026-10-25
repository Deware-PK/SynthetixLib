---
title: Init in onEnable, close in onDisable, no base class
impact: CRITICAL
tags: core, lifecycle
---

## Init in onEnable, close in onDisable, no base class

`SynthetixLib.init(this)` creates the instance that owns the scheduler and every module service. `close()` cancels scheduled tasks and drains pending file and database writes. Without it a reloaded plugin keeps old tasks running and can lose queued writes.

**❌ Incorrect**

```java
public final class MyPlugin extends JavaPlugin {
    @Override public void onEnable() { SynthetixLib.init(this); }   // instance dropped
    // no onDisable
}
```

**✅ Correct**

```java snippet=hello-init
lib = SynthetixLib.init(this);
```

```java snippet=hello-close
if (lib != null) {
    lib.close();
}
```
