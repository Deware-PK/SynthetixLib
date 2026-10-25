---
title: Text and number input uses a dialog, not an anvil GUI
impact: HIGH
tags: dialog, input
---

## Text and number input uses a dialog, not an anvil GUI

A dialog gives typed fields (text, number range, option, bool) validated by the client and the server. Anvil GUIs and chat prompts are untyped, fragile and cannot be translated or restyled by the buyer.

**❌ Incorrect**

```java
new AnvilGUI.Builder().onClick((slot, state) -> parse(state.getText())).open(player);
player.sendMessage("Type the price in chat");
```

**✅ Correct**

```java snippet=dialog-submit
final DialogType<ItemStack> sell = dialogs.dialog("sell").<ItemStack>withContext()
        .placeholder("item-name", view -> itemName(view.context()))
        .submit("sell", SellForm.class, (form, click) -> {
            final Player player = click.player();
            lang.send(player, Messages::dialogSold,
                    Placeholder.component("item", itemName(click.view().context())),
                    Placeholder.unparsed("price", String.valueOf((long) form.price())),
                    Placeholder.unparsed("duration", form.duration()),
                    Placeholder.unparsed("note", form.note().isBlank() ? "-" : form.note()),
                    Placeholder.unparsed("who", form.anonymous() ? "anonymous" : player.getName()));
        })
        .build();
```
