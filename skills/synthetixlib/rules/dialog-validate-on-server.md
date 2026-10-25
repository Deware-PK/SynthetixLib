---
title: Never trust the dialog client
impact: CRITICAL
tags: dialog, security
---

## Never trust the dialog client

A modified client can send any text, number or option. The library re-checks `max-length`, ranges and option ids and re-shows the dialog with the `errors:` line. Declare the same limits on the record (`@Range`, `@NotEmpty`) and re-check game state in the handler (the context is a snapshot, the item may have left the hand).

**❌ Incorrect**

```java
.submit("sell", SellForm.class, (form, click) -> auctions.list(click.player(), form.price()))   // price from the form, nothing checked
```

**✅ Correct**

Put `@Range` / `@NotEmpty` on the record components and re-check game state in the handler (`if (!stillHolding(click.player(), click.view().context())) { ... }`):

```java snippet=dialog-form-record
/** The answers of {@code sell.yml}; public so the config codecs can fill it. The price is a typed whole number. */
public record SellForm(long price, String duration, String note, boolean anonymous) {
}
```
