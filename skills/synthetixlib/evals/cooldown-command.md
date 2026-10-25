# Eval: cooldown-command

Product: a kit command with a per-player cooldown. Smallest eval: core + config + command only. No database, no menu.

## Prompt

Paste this block (plus the one extra line from RUNNING.md) into the fresh session. Nothing else.

```text
Make me a Minecraft Paper plugin called DailyKit for Paper 1.21.11, Java 21. Package dev.example.dailykit.

What it does:
- /kit gives the player a starter kit: 16 bread, a stone sword and 8 torches. After using it, the player must wait
  before using it again; the default wait is 60 seconds. While waiting, /kit tells them how much time is left in a
  readable form (for example "1m 12s"), not a raw number.
- /kit can also be called as /starterkit (an alias). Whichever name the player typed, the messages mention that name.
- Admins with permission dailykit.admin can run /kit reset <player> to clear that player's wait, and /kit reload to
  reload settings and texts without restarting.
- If the player's inventory has no room, they are told and the wait is not started.
- The waits are only kept while the server runs (no database), and they must not pile up forever: when a player
  leaves, their wait data is forgotten. Say so in a comment in config.yml.
- The owner can change in config.yml: the wait time in seconds and the kit contents (item, amount). Comments in the
  file must stay after a reload or restart.
- Every text the player sees is editable by the owner in a language file (English en_US only), not in code.

It must build into a single jar that I can drop into plugins/ on a real server. Build it and make sure `gradlew build` passes.
```

## Checklist (reviewer scores each 1 / 0; N/A counts 1 with a reason)

| # | Check | How to verify |
|---|---|---|
| 1 | Builds | `gradlew build` exits 0 in the result folder (re-run it) |
| 2 | Shaded + relocated | One jar; no un-relocated `com/github/dewarepk/synthetixlib`; classes under `<package>/lib/synthetixlib`; no database libs needed (none shaded, none required) |
| 3 | No static state | No mutable `static` fields, no singleton; the cooldown data is an instance field, cleaned on quit, thread-safe (concurrent map or the lib's cooldown support) |
| 4 | Scheduler facade only | No `Bukkit.getScheduler`/`runTask`/`BukkitRunnable`; anything scheduled goes through `lib.scheduler()`. N/A if nothing is scheduled |
| 5 | MiniMessage only | No `ChatColor`, `§`, legacy serializer; the alias and player names go into messages via placeholders / escape |
| 6 | Typed config, no string paths | `config.yml` is a record/class with `@Comment`s kept after reload; kit items are typed config entries; no `getConfig().getX("path")` |
| 7 | Player texts via locale | Every reply (wait left, inventory full, reset done, reload done, no permission) from the language file; the command framework's own messages through the locale too |
| 8 | Correct module for the job | **command** module (Brigadier, alias reported as typed, the lib's cooldown support if it has one); not `plugin.yml` commands / `BasicCommand`; no gui, dialog or database added |
| 9 | `close()` in onDisable | `lib.close()` in `onDisable`, null-safe |
| 10 | Dupe-safe | The kit is given only when there is room and the wait starts only when the kit was given; two fast `/kit` calls cannot give two kits; the check-and-set of the wait is atomic (no shared mutable state race, Folia-safe). N/A is **not** allowed here |

Task-specific, not scored: `/kit reset` tab-completes online players; `/kit reload` keeps comments in `config.yml`; the time left reads like "1m 12s".

Score = items passed / 10. Green = 9 or 10.
