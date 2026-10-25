---
title: Money and wide numbers are a number-field, not a slider
impact: HIGH
tags: dialog, input, number
---

## Money and wide numbers are a number-field, not a slider

A `type: number` input is a slider. It suits a small range (about 100 steps or fewer: 1-64 items, a duration). A price, a bid or any range with thousands of values cannot be set with a slider, so the player must type it: use `type: number-field`. The server reads the text (`1,000` and `1_000` are fine, no exponent, no `NaN`), checks `min` / `max` and gives the record a `long`, `int`, `double` or `BigDecimal`. Limits that come from `config.yml` go in `inputRange(key, view -> Range.of(min, max))`; a check that needs code goes in `validate(...)`. Do not build "show the dialog again with an error" by hand.

**❌ Incorrect**

```yaml
inputs:
  price: { type: number, label: 'Price', min: 1, max: 1000000000, step: 1 }   # a slider over a billion values
```

```java
priceDialog.open(player, new PriceAsk(typed, "Price must be 1-" + maxPrice));   // a hand-made re-show with the error
```

**✅ Correct**

```yaml snippet=dialog-sell-yml
config-version: 1
title: '<lang:sell-title>'
external-title: '<lang:sell-external>'
type: confirmation
body:
  - message: '<lang:sell-info>'
    width: 250
  - item: { slot: hand }
    description: '<lang:sell-item>'
inputs:
  price:
    type: number-field          # typed, for money; min / max here are the fallback of DemoDialogs.inputRange
    label: '<lang:sell-price>'
    format: integer
    min: 1
    max: 1000000
    initial: 100
  duration:
    type: option
    label: '<lang:sell-duration>'
    options:
      h1: { display: '<lang:sell-1h>' }
      h12: { display: '<lang:sell-12h>', initial: true }
      h24: { display: '<lang:sell-24h>' }
  note:
    type: text
    label: '<lang:sell-note>'
    max-length: 64
  anonymous:
    type: bool
    label: '<lang:sell-anonymous>'
buttons:
  yes: { label: '<lang:sell-yes>', tooltip: '<lang:sell-yes-tip>', click: ['action:sell', 'sound:ui.button.click'] }
  no: { label: '<lang:sell-no>', click: [close] }
errors:
  price: '<lang:sell-error-price>'
```

```java snippet=dialog-submit
final DialogType<ItemStack> sell = dialogs.dialog("sell").<ItemStack>withContext()
        .placeholder("item-name", view -> itemName(view.context()))
        // limits from settings.yml, read for every showing and again when the answer is checked
        .inputRange("price", view -> Range.of(settings.get().sellMinPrice(), settings.get().sellMaxPrice()))
        .submit("sell", SellForm.class, (form, click) -> {
            final Player player = click.player();
            lang.send(player, Messages::dialogSold,
                    Placeholder.component("item", itemName(click.view().context())),
                    Placeholder.unparsed("price", String.valueOf(form.price())),
                    Placeholder.unparsed("duration", form.duration()),
                    Placeholder.unparsed("note", form.note().isBlank() ? "-" : form.note()),
                    Placeholder.unparsed("who", form.anonymous() ? "anonymous" : player.getName()));
        })
        // a check that needs code: not more than 10 x the base value of the stack (a demo rule)
        .validate("sell", SellForm.class, (form, click) -> {
            final long limit = maxPrice(click.view().context());
            return form.price() > limit
                    ? Optional.of(FieldError.of("price", "<lang:sell-error-too-high>",
                            Placeholder.unparsed("limit", String.valueOf(limit))))
                    : Optional.empty();
        })
        .build();
```
