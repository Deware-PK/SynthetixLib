---
title: Text is MiniMessage / Adventure only
impact: CRITICAL
tags: text, adventure
---

## Text is MiniMessage / Adventure only

`ChatColor`, `§` codes and legacy serializers are deprecated, cannot do hex/gradients cleanly and mix badly with components. Use MiniMessage strings through `lib.text()` or the language files.

**❌ Incorrect**

```java
player.sendMessage(ChatColor.GOLD + "Lot " + id);
player.sendMessage("\u00a76Lot " + id);
```

**✅ Correct**

```java snippet=core-text
final TextService text = lib.text();

text.send(player, "<green>Bid of <amount> on <item>",
        Placeholder.unparsed("amount", "100"),
        Placeholder.component("item", itemName));

final TextService prefixed = text.with(Placeholder.parsed("prefix", "<dark_gray>[<gold>Shop</gold>]"));
prefixed.send(player, "<prefix> <gray>Welcome");

final String safe = text.escape(userInput);
text.send(player, "<yellow>You typed: " + safe);
text.send(player, "<yellow>You typed: <input>", Placeholder.unparsed("input", userInput));
```
