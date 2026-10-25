---
title: Shutdown waits for pending writes before the pool closes
impact: CRITICAL
tags: database, shutdown, never-again
---

## Shutdown waits for pending writes before the pool closes

SnapPlug closed the pool while writes were still queued and lost data on `/stop` (bug #1). A `Database` made with `create` is attached to `lib`: `lib.close()` in `onDisable` stops new calls, waits for queued statements up to `shutdown-timeout`, and closes the pool last. Do not close the pool yourself or skip `close()`.

**❌ Incorrect**

```java
@Override public void onDisable() {
    dataSource.close();       // queued saves are dropped
}
```

**✅ Correct**

```java snippet=hello-close
if (lib != null) {
    lib.close();
}
```

```java snippet=db-create
final Database database = Database.create(lib, settings.get().database());
final ClaimStore claims = ClaimStore.open(database);
```
