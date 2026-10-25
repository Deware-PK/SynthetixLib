---
title: Commands use the command module (native Brigadier)
impact: HIGH
tags: command, brigadier
---

## Commands use the command module (native Brigadier)

`plugin.yml` commands, `BasicCommand` and `CommandMap` hacks give no client-side validation, no typed arguments and no consistent permissions. The command module builds native Brigadier nodes with typed `Arg` handles.

**❌ Incorrect**

```java
getCommand("auction").setExecutor((sender, cmd, label, args) -> {
    long amount = Long.parseLong(args[1]);   // NumberFormatException, no tab-complete
    return true;
});
```

**✅ Correct**

```java snippet=command-give
final class GiveCommand implements SubCommand {

    private final LocaleHandle<Messages> lang;
    private final Arg<Player> target = Arg.of("player", Args.player());
    private final Arg<Material> material = Arg.of("material", Args.material());
    private final Arg<Integer> amount = Arg.of("amount", Args.integer(1, 64));

    GiveCommand(final LocaleHandle<Messages> lang) {
        this.lang = lang;
    }

    @Override
    public SubSpec spec() {
        return SubSpec.literal("give")
                .permission("example.give")
                .allowConsole()
                .argument(target).argument(material).optional(amount, 1)
                .cooldown(Duration.ofSeconds(10), "example.admin")
                .usage("<player> <material> [amount]").description("Give items to a player")
                .executes(this::run);
    }

    private void run(final CommandCall ctx) {
        final Player player = ctx.get(target);
        final Material type = ctx.get(material);
        final int count = ctx.get(amount);
        player.getInventory().addItem(new ItemStack(type, count));
        ctx.reply(lang, Messages::gave,
                Placeholder.unparsed("amount", String.valueOf(count)),
                Placeholder.unparsed("material", type.key().value()),
                Placeholder.unparsed("player", player.getName()));
    }
}
```
