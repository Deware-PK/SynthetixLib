---
title: Shift-click must target only editable slots
impact: CRITICAL
tags: gui, dupe, never-again
---

## Shift-click must target only editable slots

Bukkit puts a shift-clicked stack into the first empty slot of the other inventory, which can be a decorative or locked slot (SnapPlug bug #3). The library cancels the shift-click and re-does it into `input: true` slots only.

**❌ Incorrect**

```java
// let vanilla shift-click move the item into "the menu"
event.setCancelled(false);
```

**✅ Correct**

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
