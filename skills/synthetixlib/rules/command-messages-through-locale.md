---
title: Command messages come from the language file
impact: HIGH
tags: command, locale
---

## Command messages come from the language file

Nest `CommandMessages` in your `Messages` record and pass it to `.messages(lang, Messages::commands)`; send your own messages with `ctx.reply(lang, ...)` and `throw ctx.fail(lang, ...)`.

**❌ Incorrect**

```java
if (money < price) {
    ctx.reply("<red>Not enough money");
    return;
}
```

**✅ Correct**

```java snippet=command-fail
private void run(final CommandCall ctx) {
    final String value = ctx.get(id);
    try {
        airdrops.create(value);
    } catch (final IllegalArgumentException invalid) {
        throw ctx.fail(lang, Messages::airdropInvalidId, Placeholder.unparsed("id", value));
    } catch (final IllegalStateException exists) {
        throw ctx.fail(lang, Messages::airdropExists, Placeholder.unparsed("id", value));
    }
    ctx.reply(lang, Messages::airdropCreated, Placeholder.unparsed("id", value));
}
```
