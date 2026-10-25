---
title: Target Paper only, Java 21, paper-api 1.21.11
impact: CRITICAL
tags: core, build, paper
---

## Target Paper only, Java 21, paper-api 1.21.11

SynthetixLib supports Paper 1.21.11 and runs on 26.x. Compiling against 26.x or Spigot-only API breaks the plugin on the minimum version, and Spigot servers lack the schedulers, Brigadier lifecycle and dialogs the library needs. Java 21 bytecode loads on both Java 21 and 25.

**❌ Incorrect**

```kotlin
compileOnly("org.spigotmc:spigot-api:1.21.4-R0.1-SNAPSHOT")
java { toolchain.languageVersion = JavaLanguageVersion.of(25) }
```

**✅ Correct**

```kotlin
compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
java { toolchain.languageVersion = JavaLanguageVersion.of(21) }
```

The template's `build.gradle.kts` already does this (see `references/setup.md`).
