# Eval: auction-lite

Product: a small auction house. Exercises dialog (price input), database (lots), gui (paginated buy menu), locale (two languages).

## Prompt

Paste this block (plus the one extra line from RUNNING.md) into the fresh session. Nothing else.

```text
Make me a Minecraft Paper plugin called AuctionLite for Paper 1.21.11, Java 21. Package com.acme.auctionlite.

What it does:
- A player holds an item and types /sell. A window pops up asking for the price (a number, at least 1, at most 1,000,000).
  When they confirm, the item they hold is taken from them and put up for sale. If the price is wrong, the window comes
  back with an error message instead of closing.
- /auction opens a chest-style menu with all items for sale, several pages. Each item shows the seller and the price.
  Clicking an item buys it: the buyer pays and gets the item, the seller is told. Nobody can buy their own lot, nobody
  can buy a lot twice, and nothing can be duplicated or lost, even with two players clicking at the same moment.
- Payment is a number kept per player in the plugin's own database (no Vault). /balance shows it.
  An admin command /auction give <player> <amount> adds money (permission auctionlite.admin).
- Lots and balances must survive a server restart (SQLite file in the plugin folder).
- Every text the player sees (messages, menu titles, button names, window text) can be edited by the server owner and
  comes in English (en_US) and Thai (th_TH). Players get their own language. /auction reload reloads texts and settings
  without a restart.
- Settings the owner can change in config.yml: minimum price, maximum price, maximum lots per player (default 10).

It must build into a single jar that I can drop into plugins/ on a real server. Build it and make sure `gradlew build` passes.
```

## Checklist (reviewer scores each 1 / 0; N/A counts 1 with a reason)

| # | Check | How to verify |
|---|---|---|
| 1 | Builds | `gradlew build` exits 0 in the result folder (re-run it, do not trust the log) |
| 2 | Shaded + relocated | One jar in `build/libs`; no `com/github/dewarepk/synthetixlib` entries; classes under `<package>/lib/synthetixlib`; HikariCP/drivers not shaded (loaded through `paper-plugin.yml` `loader`) |
| 3 | No static state | grep `static` fields that are not constants; no singleton `getInstance()`; no static per-player map |
| 4 | Scheduler facade only | grep `Bukkit.getScheduler`, `runTask`, `BukkitRunnable`, `new Thread`, own executors: none. DB calls are not made on the server thread |
| 5 | MiniMessage only | grep `ChatColor`, `§`, `LegacyComponentSerializer`, `&` colour codes: none. Player input (seller name, item names) escaped before MiniMessage |
| 6 | Typed config, no string paths | `config.yml` is a record/class; grep `getConfig()`, `getString("`, `getInt("`: none |
| 7 | Player texts via locale | No hard-coded player-visible text in Java; `en_US` and `th_TH` files exist; menu/dialog YAML use `<lang:key>`; command replies come from locale |
| 8 | Correct module for the job | Price input is a **dialog** (not anvil/chat/sign); the list is a **gui** menu with pages; storage is the **database** module; the command is the **command** module (Brigadier, not `plugin.yml` commands) |
| 9 | `close()` in onDisable | `lib.close()` (and database closed or owned by lib) in `onDisable`, null-safe; DB shutdown waits for pending writes |
| 10 | Dupe-safe | Buying is one atomic DB step (a lot cannot be sold twice, self-buy refused); the held item is removed only after the dialog submit passed validation on the server; no item given before the DB write succeeded; menu uses deny-by-default clicks (no taking menu items) |

Task-specific, not scored: `/sell` with an empty hand is refused with a locale message; reload works; the Thai file is real Thai.

Score = items passed / 10. Green = 9 or 10.
