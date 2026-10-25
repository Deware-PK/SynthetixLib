---
title: Every slot is locked unless it is an input slot
impact: CRITICAL
tags: gui, dupe, security
---

## Every slot is locked unless it is an input slot

Every click, drag and move into or out of a menu is cancelled by the library; only slots the file marks `input: true` take part, and only for accepted items. Allowing a click in your own handler reopens the dupe paths (number-key swap, offhand swap, drop, hopper move) the library closes.

**❌ Incorrect**

```java
@EventHandler
public void onClick(InventoryClickEvent event) {
    event.setCancelled(false);     // "let them take the reward"
}
```

**✅ Correct**

Mark the one slot where items may go with `input: true` (plus `accept`, `max-amount`) in the menu file, and read it with `view.takeInputs(...)`:

```java snippet=gui-take-inputs
/** The items leave the slot at once (so a double click cannot pay twice), then go back to the player. */
private static void confirm(final Player player, final List<ItemStack> taken, final LocaleHandle<Messages> lang) {
    if (taken.isEmpty()) {
        lang.send(player, Messages::inputEmpty);
        return;
    }
    int count = 0;
    for (final ItemStack stack : taken) {
        count += stack.getAmount();
        player.getInventory().addItem(stack).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
    }
    lang.send(player, Messages::inputConfirmed, Placeholder.unparsed("count", String.valueOf(count)));
}
```
