---
title: Test ItemStacks, entities, worlds and inventories in a server; plain SQL with a unit test
impact: MEDIUM
tags: testing, selftest, items
---

## Test ItemStacks, entities, worlds and inventories in a server; plain SQL with a unit test

Anything that needs the Paper registry (items and their data components, entities, worlds, inventories, menus, displays) cannot be built in a plain JUnit test: `ItemStack` fails without a server, and a mock of the API tests the mock, not Paper. Write a `SelfTest` and let `./gradlew smokeTest` run it on 1.21.11 **and** 26.x. Do not fake the server with mocks or reflection. The opposite also holds: a store that only runs SQL does not need a server, a unit test with `Database.standalone` is faster and easier to debug.

**❌ Incorrect**

```java
@Test
void roundTripsTheItem() {
    final ItemStack stack = new ItemStack(Material.DIAMOND, 3);   // IllegalStateException: no server
    assertEquals(3, ItemCodec.read(ItemCodec.write(stack)).getAmount());
}
```

**✅ Correct**

```java
// self-test (runs inside Paper, in smokeTest)
SelfTest.sync("items.round-trip", () -> {
    final ItemStack stack = lib.items().of(Material.DIAMOND).amount(3).build();
    Checks.equal(3, ItemCodec.read(ItemCodec.write(stack)).getAmount(), "amount");
});

// plain SQL: unit test, no server
@Test
void savesAnOrder() throws Exception {
    try (Database db = Database.standalone("orders", tempDir, logger, DatabaseSettings.sqlite("orders.db"))) {
        ...
    }
}
```

See `references/selftest.md` (and `references/database.md` for `Database.standalone`).
