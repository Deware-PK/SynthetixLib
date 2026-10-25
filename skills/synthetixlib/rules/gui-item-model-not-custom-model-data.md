---
title: Item art: item-model, never int custom model data
impact: HIGH
tags: gui, items, resource-pack
---

## Item art: item-model, never int custom model data

Int custom model data collides between plugins and packs. Use `item-model: 'pack:key'` (and `tooltip-style`) in the menu file or `ItemBuilder#itemModel`. ItemsAdder `:glyph:` and Nexo tags in titles go through as plain text; do not switch the MiniMessage parser to strict mode.

**❌ Incorrect**

```java
meta.setCustomModelData(1001);
```

**✅ Correct**

In a menu file: `item-model: 'mypack:invisible'` on the item. In code:

```java snippet=core-items
final ItemBuilder ticket = lib.items().of(Material.PAPER)
        .name("<gold>Auction ticket")
        .lore("<gray>Lot #<id>", Placeholder.unparsed("id", "12"))
        .itemModel(new NamespacedKey(lib.plugin(), "auction_ticket"))
        .glint(true)
        .set(lotId, "12");
final ItemStack first = ticket.build();
final ItemStack second = ticket.amount(2).build();      // build() returns a fresh stack every time

final ItemStack expired = lib.items().from(existing).clearLore().lore("<red>Expired").build();
```
