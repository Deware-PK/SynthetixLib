---
title: Player input is a placeholder or escaped, never concatenated
impact: CRITICAL
tags: text, security
---

## Player input is a placeholder or escaped, never concatenated

A player-provided string inserted into a MiniMessage string can contain tags such as `<click:run_command:...>`. Pass it as `Placeholder.unparsed(...)` or run it through `lib.text().escape(...)` first.

**❌ Incorrect**

```java
lib.text().send(player, "<gray>" + otherPlayerName + " says hi");
lang.send(player, Messages::note, Placeholder.parsed("note", noteTypedByPlayer));
```

**✅ Correct**

```java
lib.text().send(player, "<gray><name> says hi", Placeholder.unparsed("name", otherPlayerName));
lang.send(player, Messages::note, Placeholder.unparsed("note", noteTypedByPlayer));
```
