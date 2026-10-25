---
title: Items: data components and item_model, PDC for data
impact: HIGH
tags: core, items, pdc
---

## Items: data components and item_model, PDC for data

Int custom model data is legacy and collides between plugins. Use `item_model` with a namespaced key and store your own data in PDC through a typed `PdcKey`, which is namespaced to your plugin.

**❌ Incorrect**

```java
meta.setCustomModelData(1001);
meta.getPersistentDataContainer().set(new NamespacedKey("x", "id"), PersistentDataType.STRING, "12");
```

**✅ Correct**

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

Read and write item or entity data with a `PdcKey`:

```java snippet=core-pdc
final PdcKey<String> lotId = lib.keys().string("lot_id");
final PdcKey<Integer> bids = lib.keys().integer("bids");

lotId.set(stack, "13");
final Optional<String> lot = lotId.get(stack);
final boolean tagged = lotId.has(stack);
bids.set(player, 3);                                    // any PersistentDataHolder
lotId.remove(stack);
```
