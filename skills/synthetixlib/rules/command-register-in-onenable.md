---
title: Register commands in onEnable, before the first COMMANDS event
impact: HIGH
tags: command, lifecycle
---

## Register commands in onEnable, before the first COMMANDS event

Paper has no runtime command registration. `register` after the first `COMMANDS` event throws `IllegalStateException`. Register all specs in `onEnable`; use `reload` subcommands for config, not for commands.

**❌ Incorrect**

```java
getServer().getScheduler().runTaskLater(this, () -> commands.register(spec), 100);
```

**✅ Correct**

```java snippet=command-root
return CommandSpec.literal("example")
        .aliases("ex")
        .description("SynthetixLib example plugin")
        .sub(new AirdropCommand(airdrops, claims, lang))
        .sub(new DialogCommand(dialogs))
        .sub(new DbCommand(database, settings, lang))
        .sub(new GiveCommand(settings, lang))
        .sub(new GiveResetCommand(cooldowns, lang))
        .sub(new MenuCommand(menus))
        .sub(new ReloadCommand(settings, airdrops, lang))
        .helpSub()
        .build();
```
