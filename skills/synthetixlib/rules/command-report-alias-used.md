---
title: Report the alias the player actually typed
impact: MEDIUM
tags: command, messages
---

## Report the alias the player actually typed

Usage and error messages must show `/ah bid`, not the registered name, when the player typed an alias. Use the `<label>` placeholder (built into every reply) or `ctx.label()`.

**❌ Incorrect**

```java
ctx.reply("<red>Usage: /auction bid <amount>");
```

**✅ Correct**

```java
ctx.reply("<red>Usage: /<label> bid <amount>");      // or put it in the language file
```
