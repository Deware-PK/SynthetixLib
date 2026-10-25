# Eval: shop

Product: an admin-defined item shop. Exercises per-item config files, gui lists, dialog amount input, database, locale.

## Prompt

Paste this block (plus the one extra line from RUNNING.md) into the fresh session. Nothing else.

```text
Make me a Minecraft Paper plugin called QuickShop for Paper 1.21.11, Java 21. Package org.example.quickshop.

What it does:
- /shop opens a menu with the products for sale, several pages. Each product shows its name, a short description and the price.
- Clicking a product opens a window asking how many to buy (a whole number from 1 to 64, default 1). When the player
  confirms, the money is taken and the items are given. If they do not have enough money or the number is wrong, the
  window comes back showing what is wrong. If their inventory is full the rest drops at their feet and they are told.
- Each product is its own small file in plugins/QuickShop/products/ (for example diamond.yml) with the item, the price
  and the description, so the server owner can add products without touching code. The plugin ships three example
  products. Changing a file and running /shop reload updates the menu without a restart.
- Money is a number kept per player in the plugin's database (SQLite), starting at 100 for new players. /money shows it.
  /shop give <player> <amount> adds money (permission quickshop.admin).
- Every purchase is written to a history table; /shop history shows the player's last 10 purchases.
- All texts the player sees can be edited by the owner. English (en_US) only, but in a language file, not in code.
- The owner can set the starting money and the maximum amount per purchase in config.yml.

It must build into a single jar that I can drop into plugins/ on a real server. Build it and make sure `gradlew build` passes.
```

## Checklist (reviewer scores each 1 / 0; N/A counts 1 with a reason)

| # | Check | How to verify |
|---|---|---|
| 1 | Builds | `gradlew build` exits 0 in the result folder (re-run it) |
| 2 | Shaded + relocated | One jar; no un-relocated `com/github/dewarepk/synthetixlib`; classes under `<package>/lib/synthetixlib`; HikariCP/drivers loaded by the `paper-plugin.yml` loader, not shaded |
| 3 | No static state | No mutable `static` fields, no singleton, no static per-player map (e.g. cached balances) |
| 4 | Scheduler facade only | No `Bukkit.getScheduler`/`runTask`/`BukkitRunnable`; DB work async via the lib and results applied back on the player's own thread (`entity(player)`), not the async thread |
| 5 | MiniMessage only | No `ChatColor`, `§`, legacy serializer; product names/descriptions from files are parsed by MiniMessage, player names escaped |
| 6 | Typed config, no string paths | `config.yml` and `products/*.yml` use records/classes (per-object collection); no `getConfig().getX("path")` |
| 7 | Player texts via locale | Messages, titles, button names, validation errors all from the language file; none inline in Java |
| 8 | Correct module for the job | Amount input is a **dialog**; the product list is a **gui** list; products are a config **collection** (one file each); money/history in **database**; commands via the Brigadier command module |
| 9 | `close()` in onDisable | `lib.close()` in `onDisable`, null-safe; database closed after pending writes finish |
| 10 | Dupe-safe | Money check + deduction + history row are one atomic DB step before the items are given; amount validated on the server (dialog submit), not trusted from the client; menu clicks cannot take the displayed items |

Task-specific, not scored: ships three example products; `/shop reload` picks up a new file; history shows at most 10 rows.

Score = items passed / 10. Green = 9 or 10.
