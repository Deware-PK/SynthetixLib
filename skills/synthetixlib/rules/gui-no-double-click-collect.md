---
title: Block COLLECT_TO_CURSOR from locked slots
impact: CRITICAL
tags: gui, dupe, never-again
---

## Block COLLECT_TO_CURSOR from locked slots

A double-click on an item in the player's inventory gathers matching stacks from the whole view, including locked menu slots (SnapPlug bug #2). The library cancels `COLLECT_TO_CURSOR` whenever the menu has any locked slot. If you must build an inventory outside the gui module, you have to do the same.

**❌ Incorrect**

```java
if (event.getClickedInventory() == menuInventory) {
    event.setCancelled(true);
}     // misses COLLECT_TO_CURSOR started in the player's inventory
```

**✅ Correct**

```java snippet=gui-menu-builder
final MenuType<Void> demo = gui.menu("demo")
        .placeholder("balance", view -> Component.text(view.player().getLevel()))
        .placeholder("time", view -> Component.text(LocalTime.now().format(CLOCK)))
        .list("airdrops", view -> entries(airdrops), (entry, view) -> icon(lib, lang, claims, entry, view.player()))
        .onListClick("airdrops", (Entry entry, MenuClick<Void> click) -> airdropMenu.open(click.player(), entry))
        .state("notify", view -> notify.get(view.player()).orElse(false) ? "on" : "off")
        .action("toggle-notify", click -> {
            notify.set(click.player(), !notify.get(click.player()).orElse(false));
            click.view().refresh();
        })
        .action("confirm", click -> confirm(click.player(), click.view().takeInputs('S'), lang))
        .build();
```

With the gui module there is nothing to write: `ClickPolicy` denies `COLLECT_TO_CURSOR` while any slot is locked.
