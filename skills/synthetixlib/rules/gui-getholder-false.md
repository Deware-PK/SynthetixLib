---
title: Inventories: getHolder(false) only
impact: CRITICAL
tags: gui, inventory, paper
---

## Inventories: getHolder(false) only

`Inventory#getHolder()` snapshots the block entity of a container and can load chunks or throw off the owning thread. Menus have their own holder; recognise them with `getHolder(false)`, and use the library's views instead of identifying menus by title.

**❌ Incorrect**

```java
@EventHandler
public void onClick(InventoryClickEvent event) {
    if (event.getInventory().getHolder() instanceof MyMenuHolder) { ... }
    if (event.getView().getTitle().equals("Shop")) { ... }
}
```

**✅ Correct**

```java
final InventoryHolder holder = event.getInventory().getHolder(false);   // never the snapshotting overload
```

Best: do not write click listeners for menus at all. Register `action:` / `list:` names on the `MenuBuilder`:

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
