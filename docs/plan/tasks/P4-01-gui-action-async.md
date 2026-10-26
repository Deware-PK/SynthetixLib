# P4-01 gui: `actionAsync` — the view stays busy until the work is done
Status: todo · Approved: yes · Model: sonnet · Effort: high
Module: synthetixlib-gui · Depends on: — · Design: gui.md §2, §4, §5 (+ this task, §14 of gui.md once added)
Requested by: InteractiveAuction 2.0 (mailbox claim, cancel listing, blacklist edit), lib gap L-1

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/gui.md (§2, §4, §5, §11) and docs/plan/tasks/P4-01-gui-action-async.md, then implement the
task. Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Goal
A menu action that starts database work (claim a mail item, cancel a listing) must not run twice when the player double-
clicks or keeps clicking while the work is in flight. Today `ClickGate` only covers the synchronous handler: an `action`
that returns right after starting a future lets the next click in at once (`MenuViewImpl.click`, `gate.leave()` in
`finally`). Products then need their own per-player "in flight" maps, which the lib rules forbid (no static per-player
maps) and which every product would write differently.

## Spec
### API (`MenuBuilder<C>`)
```java
/** Like {@link #action}, but the view stays busy until the returned future completes. */
public MenuBuilder<C> actionAsync(String name, Function<MenuClick<C>, CompletableFuture<?>> handler)
```
- Same name rules and the same `action:<name>` in the file as `action` (a name is either sync or async, registering both
  → `IllegalStateException` like any duplicate name).
- The handler runs on the player's thread (like `action`); it may return `null` → treated as a completed future (plus a
  warning once per name: "actionAsync handler returned null").

### Behaviour (`MenuViewImpl`)
- The `ClickGate` is entered as today; for an async action it is **not** left in `finally` but when the future completes
  (normally or exceptionally), on the player's entity scheduler. While busy, every click in this view is ignored (same as
  today for a slow sync handler), including other slots, page buttons and `close` steps of other items. Closing the
  inventory (Esc) is never blocked.
- Several click steps in one item (`[action:claim, sound:…]`): steps run in order; an async step makes the later steps wait
  until its future completes (then they run, still on the player's thread). If the future fails, the later steps are
  skipped.
- **Busy look (optional, file side)**: a menu item may set `busy:` (an item, like `loading:` of lists). While an async action
  started from that slot runs, the slot shows `busy:`; afterwards the normal render comes back (`refresh(char)` of that
  slot). No `busy:` → nothing changes visually.
- **Timeout**: no settings record; a constant default of 30 s, overridable per action with
  `actionAsync(name, timeout, handler)`. On timeout the gate is released, a warning is logged once with menu id + action
  name, the future is **not** cancelled (the work may still finish; products must stay atomic on their side).
- **Failure**: an exceptional future is logged like a failing sync handler (`manager.guarded` style: menu id, action,
  exception) and the lang key `gui.action-failed` is sent to the player if the product's `Messages::gui` has it (empty /
  missing → nothing sent). Add the key to the template's `Messages` with an English and Thai default.
- **View gone**: the view was closed / replaced / the player quit before completion → the completion only releases the
  gate state of that (dead) view and touches nothing else; no exception, no refresh, no message.
- `lib.close()` / `GuiService` shutdown with an action in flight: views close as today; the future is left alone.
- Threads: the completion hop uses `lib.scheduler().entity(player)` with a `retired` runnable that does nothing. Never
  `join()` the future.

### Not in this task
No per-player lock across two different views (one view per viewer is the design; the product's state must be atomic).
Document this in the reference: "the gate stops double clicks in one view; your database update must still be
`... WHERE claimed_at IS NULL`".

## Tests
- Unit (fake view/clock, like `ClickActionTest` / `ClickGateTest`): second click while the future is pending is ignored;
  after completion the next click runs; exceptional completion releases the gate and skips later steps; timeout releases
  the gate + one warning; `null` future; `busy:` item shown and restored; view disposed before completion → no refresh.
- Self-test `gui.action-async` (both versions): a menu with an async action completing after 10 ticks, two scripted
  clicks 2 ticks apart → handler ran once; a third click after completion → twice.
- Example plugin: `/example menu` gets a "claim" item using `actionAsync` + `busy:` (snippet `gui-action-async` for the
  skill).

## Docs (same task)
- `skills/synthetixlib/references/gui.md`: `actionAsync`, `busy:`, timeout, the "atomic on your side" note; API table
  line "since 0.9.0".
- New rule `skills/synthetixlib/rules/gui-async-action-for-io.md` (HIGH): an action that does DB/HTTP work uses
  `actionAsync`, never `action` + a hand-made in-flight set; ❌/✅ pair from the example snippet.
- `docs/design/gui.md`: new §14 "0.9.0 additions" with the API and behaviour above. CHANGELOG "0.9.0".

## Acceptance criteria
- [ ] double click on an async claim runs the handler once (unit + self-test)
- [ ] timeout and failure paths release the gate (unit)
- [ ] no static state, no `Bukkit.getScheduler()` (archtest green)
- [ ] reference, rule, snippet, design §14, CHANGELOG updated; `checkSnippets` green
- [ ] `gradlew.bat check smokeTest` green on 1.21.11 and 26.x

## Verify
gradlew.bat check smokeTest

## Result
(agent fills in)
