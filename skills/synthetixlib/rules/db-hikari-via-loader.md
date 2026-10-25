---
title: HikariCP comes from a PluginLoader, never shaded
impact: CRITICAL
tags: database, build, relocation
---

## HikariCP comes from a PluginLoader, never shaded

Shading and relocating HikariCP breaks driver discovery and doubles the jar size. The library needs it at runtime from the plugin class loader: `compileOnly` in the build, `SynthetixLibraries.database(classpath)` in the `PluginLoader` named by `loader:` in `paper-plugin.yml`. Paper ships the SQLite and MySQL drivers.

**❌ Incorrect**

```kotlin
implementation("com.zaxxer:HikariCP:7.1.0")      // ends up in the shaded jar
```

**✅ Correct**

`compileOnly("com.zaxxer:HikariCP:<version of the lib>")` in the build (see `setup.md`); in the loader enable the `SynthetixLibraries.database(classpath)` line:

```java snippet=template-loader
@Override
public void classloader(final PluginClasspathBuilder classpath) {
    // Database module: add HikariCP (needs the import com.github.dewarepk.synthetixlib.database.SynthetixLibraries)
    // SynthetixLibraries.database(classpath);
}
```
