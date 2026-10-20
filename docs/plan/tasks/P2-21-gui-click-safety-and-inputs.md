# P2-21 GUI click safety, input slots, built-in actions
Status: done · Approved: yes · Model: sonnet · Effort: high
Module: synthetixlib-gui · Depends on: P2-20 · Design: gui.md §2 (actions), §4

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/gui.md and docs/plan/tasks/P2-21-gui-click-safety-and-inputs.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.
This is dupe-critical: write the click policy as a pure function first and test the full table before wiring events.

## Spec
- Pure `ClickPolicy.decide(view model, slot, ClickType, InventoryAction, raw slot in top/bottom)` → allow / deny /
  redo-shift. Deny by default; allow only inside `input` slots that `accept` the item and respect `max-amount`.
- Every case of gui.md §4: COLLECT_TO_CURSOR denied when the view has any locked slot (bug #2); shift-click from the
  player inventory denied and re-done by us into input slots only (bug #3); number-key / off-hand swap / drop on
  locked slots; MOVE_TO_OTHER_INVENTORY from locked; drag over any locked slot → whole drag denied;
  `InventoryMoveItemEvent` into a view denied; creative-mode clicks treated the same.
- Input items handed back on close / quit / kick / `lib.close()` (inventory, overflow dropped at the player, never lost,
  never doubled). `onInputChange(char, items)`, `takeInputs()` atomic (empties + returns once).
- Debounce: one click handler at a time per view; a second click while an `action:` runs is ignored.
- Built-in actions run in order: `command:` (as player), `console:`, `message:` (MiniMessage or `<lang:key>` later
  P2-22 — plain MiniMessage now), `sound:`, plus the ones from P2-20. Item `sound` plays on any click.

- Carry-over from P2-20 review (dupe-relevant, with tests):
  - `ViewManager.close()`: close every open inventory **first**, unregister the listener **last** (today the listener
    goes first, so if a close task cannot run, menu items are takeable). If a player's close cannot be scheduled
    (library closing), clear that view's top inventory contents (`inventory.clear()`) so nothing real is left in it.
  - Re-open (`MenuViewImpl` title/shape change, `manager.reopen`): input-slot items move to the new inventory
    (same chars, never doubled, overflow handed back like on close) and the item on the cursor is kept (set it back on
    the new view or give it back). Unit test: re-open with items in input slots + cursor → counts equal before/after.
  - Doc note in gui.md §7 / module AGENTS.md: `<page>` in a title re-opens the menu on every page change (pointer re-centres).

## Tests
Unit: full table ClickType × InventoryAction × {locked, input, input-wrong-item, bottom inventory} (assert every cell),
shift redo distributes only into input slots and respects max-amount, drag rules, takeInputs twice → second empty,
return-on-close never duplicates (item counts before/after), debounce from 2 threads.
Self-test `gui.policy`: policy wired to a real view, synthetic events through the listener.

## Acceptance criteria
- [x] every bug #2/#3 path covered by a unit test
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
Done 2026-10-20. `gradlew.bat check smokeTest` green: gui 182 unit tests, ArchUnit + forbidden patterns, and 63/63
self-tests on Paper 1.21.11 and 26.3 (`gui.policy` is new).

**Order of work (as asked):** the click policy was written first as a pure function (`ClickPolicy`, generic over the stack
type, no server) and its table was green (1330 cells, see below) before any event was wired.

Built (all package-private in `com.github.dewarepk.synthetixlib.gui` unless noted)
- `ClickPolicy.decide(SlotMap, StackOps, Click)` -> `ALLOW | DENY | REDO_SHIFT`, `decideDrag(...)`. Deny by default. Locked
  slot: everything denied. Input slot: take out / drop / shift out allowed; place, swap with cursor, number key, off-hand key
  only for an `accept`ed item that keeps the slot within `max-amount` (PLACE_SOME counts as a full stack). Double-click
  (`DOUBLE_CLICK` or `COLLECT_TO_CURSOR`) denied wherever it lands while the menu has any locked slot (bug #2). Shift-click in
  the player's inventory is never left to the server: `REDO_SHIFT` (bug #3), denied if the menu has no input slot. Other
  clicks in the player's inventory only while an input slot exists. `MIDDLE`, window border, `CLONE_STACK`, `NOTHING`,
  `UNKNOWN`, hotbar action without number/off-hand key, shift action without shift: denied. Dropping the cursor outside the
  window is allowed (the cursor only ever holds the player's own item). Creative clicks go by their action.
  `HOTBAR_MOVE_AND_READD` is deprecated for removal, so it is matched by name (a server without it must still load the class).
- `SlotMap`, `StackOps<S>` + `BukkitStacks`, `Inputs` (shift distribution: onto same-kind stacks first, then empty input slots,
  never beyond `max-amount`; drain; hand back), `Handoff` (re-open), `AcceptFilter` (`*`, material names, `#tags`, tag looked up
  at check time), `ClickGate`, `Shutdown`.
- `ViewManager`: click / drag at `HIGHEST`, `InventoryMoveItemEvent` + `InventoryPickupItemEvent` cancelled into or out of a
  menu; a click already cancelled by another plugin runs no action. Input items handed back on close, quit (kick fires quit),
  world change, `closeNow`, `lib.close()`, and when a menu is replaced by another; on death they join `getDrops()` (or the kept
  inventory). Every path drains first, so a second path finds nothing.
- `Renderer`: input slots are never drawn, cleared or redrawn (before, `refresh()` did `inventory.clear()`, which would have
  wiped the player's items).
- `MenuViewImpl`: `inputs(c)`, `takeInputs(c)`, `takeInputs()` (one lock around read + clear: second call returns nothing),
  `onInputChange` notification (coalesced, after the click, skipped if the items equal what the handler saw last),
  debounce (`ClickGate`), item `sound` plays on any click that reaches a shown item.
- Carry-over from P2-20: `ViewManager.close()` closes every view first and unregisters the listener last (`Shutdown`); a view
  that cannot be closed on its thread gets its menu items cleared at once. Re-open (title/shape change, and now also a reload
  that moves input slots) moves input items to the same slots of the new inventory (same char, same order), hands overflow
  back, and takes the cursor item off before the old inventory closes and puts it back after; if the new inventory does not
  open, nothing moves. `<page>`-in-title note added to gui.md §7 and the module AGENTS.md.
- Menu files: `input: true` items need no `material`; the parser reports `accept`/`max-amount` without `input`, `input` with
  `list`/`state`, `input` nested in a state, keys that have no effect on an input slot (`material, name, click, ...`) and bad
  `accept` entries (with their path). `MenuChecker` warns about an `onInputChange` handler for a char that is not an input slot.

Tests
- `ClickPolicyTest`: 5 columns (locked, input accepted, input refused, player's inventory, outside) x all 14 `ClickType` x all
  19 `InventoryAction`, every cell compared with an oracle written as rules; invariants (locked never allows, `REDO_SHIFT` only
  for shift in the player's inventory); named tests for bug #2 (incl. all-input menu), bug #3, number key / off-hand / drop on
  locked, creative, amounts (place all/one/some, swap, hotbar), drags.
- `InputsTest` (shift fills same-kind first, never into locked/refused, totals add up for every existing x moving amount;
  drain twice -> second empty; hand back with a bag of every size: held + dropped = given; closing three times returns once),
  `HandoffTest` (inputs + cursor, counts equal before/after; gone/refused/lowered slots overflow; refused open changes
  nothing; cursor off while the old inventory closes), `ClickGateTest` (2 threads: exactly one through; 8 threads x 20k: never
  two inside), `AcceptFilterTest`, `ShutdownTest`, `InputSlotsFileTest`.
- Self-test `gui.policy`: a real view (`MenuType#attach`) with a Proxy player, real `InventoryClickEvent` / `InventoryDragEvent` /
  `InventoryMoveItemEvent` / `InventoryCloseEvent` through the server's event system to the listener: locked slot, creative
  clone, number key, off-hand, double-click (bug #2), accepted / refused / over-max placement, shift-click redo with exact
  counts and no touch of locked slots (bug #3), drags, hopper move, `takeInputs` from a click action twice, hand-back on close
  twice (never doubled), service closing with a menu open.

Public API added (new, named in the task/design but not spelled out there; please check the names)
- `MenuBuilder#onInputChange(char slotChar, BiConsumer<MenuView<C>, List<ItemStack>>)`
- `MenuView#inputs(char)`, `MenuView#takeInputs(char)`, `MenuView#takeInputs()` (`List<ItemStack>`; call on the viewer's thread)
- `MenuType#attach(Player, C)` (`@ApiStatus.Internal`, like `preview`; self-tests only)

Decisions / deviations for review
- The spec says to `inventory.clear()` a view whose close cannot be scheduled. That would delete the player's input items, which
  contradicts "never lost", so only the slots that are not input slots are cleared; the input items stay in the inventory.
- On a thread that does not own the player (Folia) `lib.close()` clears the menu items at once and also schedules the close; if
  the library's scheduler then drops that task, the input items are not handed back (they stay in the inventory the player
  still has open). Not testable here without Folia; the go-mcbots / Folia run should look at it.
- `max-amount` absent means "no limit beyond the item's own stack size" (the file model allows 1..99 when present).
- A dropped-cursor click outside the window is allowed, and clicks in the player's own inventory are denied when the menu has
  no input slot (same as P2-20).
- `gui.policy` logs one expected WARNING ("could not close the menu of PolicyBot cleanly"): the Proxy player is not a real
  entity, so `scheduler().owns(player)` throws and `Shutdown` falls back to clearing the menu's items.

Not covered (needs a real client / server thread, left for the go-mcbots E2E)
- vanilla actually carrying out an allowed click (the self-test only sees "not cancelled"), the cursor item surviving a real
  `openInventory` re-open, Folia thread hops, `PlayerQuitEvent` / death order on a real server.


## Review (planner, 2026-10-20)
Approved. Smoke 63/63 on 1.21.11 + 26.3. Checked: click/drag/move listeners at HIGHEST, deny path cancels even if another
plugin un-cancelled earlier; every hand-back path drains under `inputLock` first (no double give: close after death /
quit finds empty slots); shift redo only into input slots; `refresh()` no longer clears input slots.
Accepted: API names (`onInputChange`, `inputs`, `takeInputs`, internal `attach`); the deviation "wipe only non-input
slots when a close cannot be scheduled" (correct — the spec would have lost items). Known gap: Folia scheduler dropping
the close task at shutdown → input items stay in the menu (not lost, not handed back); covered later by go-mcbots E2E.
