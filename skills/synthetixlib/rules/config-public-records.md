---
title: Mapped config records must be public
impact: MEDIUM
tags: config, records
---

## Mapped config records must be public

The mapper reads record components by reflection without `setAccessible`, so a package-private or private record fails to load. Defaults come from a static factory on the record, not from a resource file.

**❌ Incorrect**

```java
private record Settings(int minBid) {}
```

**✅ Correct**

```java
public record Settings(@Comment("Minimum bid") int minBid) {
    public static Settings defaults() { return new Settings(100); }
}
```
