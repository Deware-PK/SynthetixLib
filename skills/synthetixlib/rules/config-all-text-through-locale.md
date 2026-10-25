---
title: Every player-visible text comes from the language files
impact: CRITICAL
tags: config, locale, text
---

## Every player-visible text comes from the language files

Inline strings cannot be translated or edited by the server owner. Keep a typed `Messages` record, one YAML file per language, and send with `lang.send(...)`. This includes command messages and menu/dialog texts (`<lang:key>`).

**❌ Incorrect**

```java
player.sendMessage("<red>You do not have enough money");
ctx.reply("<green>Reloaded");
```

**✅ Correct**

```java snippet=config-locale-send
lang.send(player, Messages::welcome, Placeholder.unparsed("name", player.getName()));
lang.sendLines(player, Messages::help);                           // List<String>: one chat line each
final Component line = lang.get(player, Messages::reloaded);      // when you need the Component
final Component forConsole = lang.get(lang.defaultLocale(), Messages::reloaded);
```
