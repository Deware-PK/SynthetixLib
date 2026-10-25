---
title: No static or global state, no singletons
impact: CRITICAL
tags: core, state, relocation
---

## No static or global state, no singletons

The library is shaded and relocated into each plugin; every plugin has its own copy and lifecycle. A static `LIB` or static registry leaks across reloads, breaks when two plugins share a classloader assumption, and is rejected by the library's ArchUnit rules. Everything hangs off the instance returned by `init`.

**❌ Incorrect**

```java
public static SynthetixLib LIB;
public static final Map<String, Arena> ARENAS = new HashMap<>();
```

**✅ Correct**

```java
private SynthetixLib lib;                       // field of the plugin
private final Map<String, Arena> arenas = new ConcurrentHashMap<>();
// pass lib or the services you need to your classes through constructors
```
