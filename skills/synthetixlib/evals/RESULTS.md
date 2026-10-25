# Eval results

One table per SynthetixLib version. How to run: `RUNNING.md`. Reviewer prompt: `REVIEW.md`. Green = builds and score >= 9/10.
`Fixed by`: the doc file (or task) that closed the gap; "API" gaps become proposed tasks, not doc fixes.

## 0.8.0

| Eval | Run | Builds | Score | Gaps | Fixed by |
|---|---|---|---|---|---|
| auction-lite | 2 (Sonnet, medium) | yes (builder log; not booted) | 10/10 | none new; uses number-field + inputRange, no hand-made re-show or parsing | P2-34 confirmed |
| shop | 2 (Sonnet, medium) | yes · **owner played it in game: all works** | 10/10 | small: no async validate (money check needs the DB) → re-open with a context flag | proposal (validateAsync) |
| cooldown-command | 2 (Sonnet, medium) | yes · owner tested in game: all works | 10/10 | small: agent unsure that `cooldown(Duration.ZERO)` on a child clears the inherited wait | references/command.md (P2-36 C) |

### auction-lite run 2 (reviewer: planner in Cowork, 2026-10-25)
Builds: yes · Score: 10/10. Same checklist evidence as run 1 (relocated, 0 `com/github` entries, no static state, no
Bukkit scheduler, `.join()` only in onEnable and inside `listAsync`, typed config, locale incl. th_TH, one-transaction buy
with delivery queue). **Change vs run 1:** `dialogs/sell.yml` uses `type: number-field` + `format: integer`, code uses
`.inputRange("price", view -> Range.of(min, max))` from config; the hand-written parser and the `next(dialog, ctx)` error
flow are gone (SellDialog about half the logic). Nit: a dead `return` without a message when limits change between
showing and answer (the library already checks with the shown range).

### shop run 2 (reviewer: planner in Cowork, 2026-10-25)
Builds: yes; owner ran it on Paper: everything worked · Score: 10/10. 0 `com/github` entries, no static state, `.join()` only
in onEnable. **Change vs run 1:** `type: number-field` + `.inputRange("amount", view -> Range.of(1, maxPerPurchase))` — the
config value and the dialog limit can no longer disagree. "Not enough money" needs the database, so the agent re-opens the
dialog with a `tooPoor` context and the typed amount (`input("amount", …)`): correct, but a `validateAsync` would make it a
one-liner → proposal for a later task. Database settings are fixed in code (no `database:` in config.yml): allowed by the
prompt, noted.

### cooldown-command run 2 (reviewer: planner in Cowork, 2026-10-25)
Builds: yes; owner tested in game: all works · Score: 10/10. **Change vs run 1:** no own cooldown map any more —
`.cooldown(sender -> settings.get().kitWait())` (reload applies), `/kit reset` uses `commandService.cooldowns().reset(uuid, "kit")`,
full inventory is `throw ctx.fail(...)` (comment: "a failure does not start the wait"); cleanup on quit is the library's.
222 lines (run 1: 295). Doc gap: references/command.md says cooldowns are inherited but not how an admin child opts
out; the agent guessed `.cooldown(sender -> Duration.ZERO)` (correct) → add that example.

## 0.7.0

| Eval | Run | Builds | Score | Gaps | Fixed by |
|---|---|---|---|---|---|
| auction-lite | 1 (Sonnet, medium) | yes (builder log; reviewer could not re-run Gradle) | 10/10 | dialog: wide-range number input, config-driven limits (doc + API) | dialog.md "Wide numbers and limits that come from config" · API → P2-34 proposed |
| shop | 1 (Sonnet, medium) | yes (builder log; not booted) | 10/10 | dialog: config-driven max vs static slider max (same as auction-lite) | dialog.md (same section) · API → P2-34 |
| cooldown-command | 1 (Sonnet, medium) | yes (builder log; not booted) | 10/10 | command: cooldown fixed at registration, no per-player reset, no reload (API); config: comments not on list entries | command.md (own cooldown map, snippet `command-own-cooldown`) · config.md Limits · API → P2-35 |

Reviews (paste each full review below, newest last):

### auction-lite run 1 (reviewer: planner in Cowork, read the code directly, 2026-10-25)
Builds: yes (builder; booted on Paper 1.21.11, files generated) · Score: 10/10
| # | Check | 1/0 | Evidence |
|---|---|---|---|
| 1 | Builds | 1 | builder ran `gradlew build -PsynthetixLocal=true`; jar booted on 1.21.11 (owner: re-run once to confirm) |
| 2 | Shaded + relocated | 1 | 366 entries under `com/acme/auctionlite/lib/synthetixlib`, none under `com/github`, no HikariCP/sqlite in jar, `loader:` set |
| 3 | No static state | 1 | only `static final long MAX_GIVE`; per-player sets are instance `ConcurrentHashMap` |
| 4 | Scheduler facade | 1 | no Bukkit scheduler; `.join()` only in `AuctionStore.open` from onEnable (allowed by database.md) ; replies via `lib.scheduler().entity(p)` |
| 5 | MiniMessage only | 1 | no legacy; player/item names as `Placeholder.unparsed` / components |
| 6 | Typed config | 1 | `Settings` record, no `getConfig()` |
| 7 | Locale | 1 | `Messages` record, `lang/th_TH.yml` bundled, en_US generated from defaults; menu/dialog use `<lang:>` |
| 8 | Right modules | 1 | dialog for price, gui menu with pages, database module, command module |
| 9 | close() | 1 | `lib.close()` null-safe; `Database.create(lib, …)` owned by lib |
| 10 | Dupe-safe | 1 | `buy` = one transaction (select, conditional debit, delete must hit 1 row, credit, delivery row); item leaves hand only after server checks, given back / queued on failure or quit; held item re-compared |
Task-specific: empty hand refused, reload works, Thai written by the agent (owner to proof-read).
Notes: crash between hand-clear and DB write could lose one item (builder says so) — acceptable, documented.
### Doc gaps
- references/dialog.md: number input is a **slider**; say so and show the pattern for a wide range (text input + `integer()`
  / `long` component, `max-length`). The agent found it alone but had to hand-roll parsing.
- references/dialog.md: limits that come from config.yml (min/max price) cannot be `@Range` or file limits, so the agent
  re-showed the dialog by hand with `next(dialog, ctx-with-error)`. Document that pattern now; **API** gap → P2-34.

### shop run 1 (reviewer: planner in Cowork, read the code directly, 2026-10-25)
Builds: yes (builder; never booted — no server jar on the builder's machine) · Score: 10/10
| # | Check | 1/0 | Evidence |
|---|---|---|---|
| 1 | Builds | 1 | builder `gradlew build -PsynthetixLocal=true` (owner: boot once on runServer to confirm) |
| 2 | Shaded + relocated | 1 | 366 entries under `org/example/quickshop/lib/synthetixlib`, 0 under `com/github` / zaxxer |
| 3 | No static state | 1 | no static fields at all |
| 4 | Scheduler facade | 1 | `.join()` only in onEnable (migrate), `submitAsync` handler and `executesAsync` command (async threads); items given via `lib.scheduler().entity(p)` with a retired-callback refund |
| 5 | MiniMessage only | 1 | no legacy; numbers as `Placeholder.unparsed` |
| 6 | Typed config | 1 | `Settings` record + `configs.collection("products", Product.class, …)` (one file per product) |
| 7 | Locale | 1 | `Messages` record → `lang/en_US.yml`; menu/dialog via `<lang:>` |
| 8 | Right modules | 1 | gui menu with pages, dialog (slider 1–64), config collection, database, command module |
| 9 | close() | 1 | `lib.close()` null-safe; `Database.create(lib, …)` |
| 10 | Dupe-safe | 1 | debit + history in one transaction, items only after success; refund + history delete if the player left before delivery; product re-read at submit (reload-safe) |
Task-specific: full inventory drops + message, three example products created on first start, reload covers all files.
### Doc gaps
- references/dialog.md: same as auction-lite — `max-per-purchase` (config) vs the slider `max: 64` (dialog file) are two
  limits the owner must keep in sync; the agent re-showed errors by hand via `next(dialog, ctx)`. **API** → P2-34
  (code-side validation, and ideally input limits from code: `.inputRange("amount", view -> 1..max)`).

### cooldown-command run 1 (reviewer: planner in Cowork, read the code directly, 2026-10-25)
Builds: yes (builder, incl. `verifyRelocation`; never booted) · Score: 10/10
| # | Check | 1/0 | Evidence |
|---|---|---|---|
| 1 | Builds | 1 | builder `gradlew build` incl. relocation check |
| 2 | Shaded + relocated | 1 | 0 entries under `com/github`; template loader dropped (no runtime libs needed) |
| 3 | No static state | 1 | none; waits in an instance `ConcurrentHashMap` |
| 4 | Scheduler facade | 1 | no scheduler needed, no Bukkit scheduler, no blocking |
| 5 | MiniMessage only | 1 | no legacy |
| 6 | Typed config | 1 | `Settings` record with `@Comment`, `List<KitItem>` |
| 7 | Locale | 1 | `Messages` record incl. h/m/s words; `<label>` placeholder everywhere |
| 8 | Right modules | 1 | core + config + command only (Brigadier, alias `starterkit`) |
| 9 | close() | 1 | null-safe `lib.close()` |
| 10 | Waits cleaned on quit, no room → no wait | 1 | `onQuit` clears; fit check before giving |
Task-specific: comment about forgetting waits is on the `kit`/wait key (comments on list items are not kept by config).
### Doc gaps
- references/command.md: say clearly that `.cooldown(Duration)` is fixed at registration (not reloadable), has no
  per-player reset and always starts on success — and show the "own cooldown map cleaned on quit" pattern for those cases.
  **API** → P2-35.
- references/config.md: comments can sit on keys, not on list entries (agent found out by itself).

