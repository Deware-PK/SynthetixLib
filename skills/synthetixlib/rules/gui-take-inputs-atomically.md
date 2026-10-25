---
title: Read input slots with takeInputs, not getItem
impact: HIGH
tags: gui, dupe
---

## Read input slots with takeInputs, not getItem

`view.takeInputs('S')` empties the slots and returns the stacks in one step, so a double-click on a confirm button cannot pay twice. Reading with `getItem` and clearing later leaves a window where the same items count twice.

**❌ Incorrect**

```java
final ItemStack item = inventory.getItem(10);
pay(player, item);
inventory.setItem(10, null);      // a second click already read the same item
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
