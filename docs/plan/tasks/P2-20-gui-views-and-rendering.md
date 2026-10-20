# P2-20 GUI views: open/close, rendering, lists, pagination, states, refresh
Status: done · Approved: yes · Model: sonnet · Effort: high
Module: synthetixlib-gui · Depends on: P2-19 · Design: gui.md §2, §3, §5

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/gui.md and docs/plan/tasks/P2-20-gui-views-and-rendering.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.
Before coding, check in the paper-api jar how an open inventory's title can be changed on 1.21.11 (Component, not
legacy String); if it cannot, re-open with the same contents and say so in Result.

## Spec
- `MenuType.open(player)` / `open(player, context)` from any thread → hops onto the player's entity scheduler. Creates
  a `MenuView` (own inventory with the library's `InventoryHolder`, page, context); `getHolder(false)` only (bug #8).
- `MenuView`: `player() context() page() page(int) refresh() refresh(char) close() back()`. `back` = previous menu of
  this viewer (per-viewer stack inside the service instance, cleared on quit — bug #4).
- Rendering: static items once; placeholders `<player> <page> <pages>` + registered ones as unparsed components
  (never re-parsed); `state` picks the item; `hide-when`; `list` fills its slots in layout order, pagination math
  over all slots of that char; `empty` for free list slots.
- `update-every` → one repeating task per open view on the player's entity scheduler, cancelled on close.
- Views dropped on close / quit / kick / world change / death; `lib.close()` closes every open view.
- `gui.reload()`: open views pick up the new file on their next refresh.
- Clicks in this task: cancel all (full safety is P2-21) and dispatch `page-next/previous`, `close`, `open:`,
  `back`, `refresh`, `action:`, `onListClick` so pages can be tested.

- Carry-over from P2-19 review (with unit tests):
  - `MenuChecker`: an unregistered placeholder is a **warning only**, the item keeps its clicks and renders the tag as
    plain text; `action:` / `list:` / `state:` still make it inert.
  - Replace the hard-coded `isKnownTag` list: a name is a known tag if `TagResolver.standard().has(name)` or the
    `lib.text()` base resolvers have it. Add `TextService#hasTag(String)` in core (small public method, Javadoc) for that.

## Tests
Unit: pagination (0, 1, exact page, many), placeholder not re-parsed (`<red>` in a value stays text), state switch,
hide-when, back stack per viewer, view registry has no shared per-player field (two viewers, same menu).
Self-test `gui.render`: build a view for a fake/offline context if Paper allows creating the inventory without a
player; else render into a plain Inventory and assert slots. Note limits in Result.

## Acceptance criteria
- [x] one view per viewer, nothing per-player outside the view / service instance
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
Done 2026-10-20. `gradlew.bat check smokeTest` green (gui 113 unit tests; `gui.menu-files` and `gui.render` pass on
Paper 1.21.11 and 26.3).

**Title change (checked in paper-api-1.21.11 jar with javap):** `InventoryView` has `title()` (read-only Component) and
`setTitle(String)` (legacy string); there is no `Component` setter. So a changed title is done by **re-opening a new
inventory with the same contents** (`Bukkit.createInventory(holder, size, Component)` + `player.openInventory`). The
view re-opens only when the rendered title `Component` differs or the file's type/size changed after a reload. The old
inventory's `InventoryCloseEvent` is ignored (`view.inventory()` is already the new one), so the back stack and the
view survive. Known cost: the mouse pointer re-centres on a re-open (a page change whose title has `<page>`).

Built
- `MenuType.open(player)` / `open(player, context)` (any thread -> `lib.scheduler().entity(player)`), `MenuView` impl
  (`MenuViewImpl`): `page / page(int) / refresh() / refresh(char) / close() / back()` all hop onto the player's thread.
- `ViewManager` (per `GuiService`): `ViewRegistry` (viewer -> view, back stack, max 32, no static), listeners for
  click / drag (cancel all, dispatch `page-next/previous, close, open:, back, refresh, command:, console:, message:,
  sound:, action:, onListClick`), close, quit, death, world change; `GuiService.close()` closes every open view.
  Events are registered with plain executors (no reflection). Kick is covered by quit (Paper fires `PlayerQuitEvent`
  after a kick).
- `Renderer`: static items once (only list / state / hide-when / placeholder items are redrawn on `update-every`;
  `refresh()` redraws all), placeholders `<player> <page>(from 1) <pages>` + registered ones inserted as components
  (never re-parsed, each value asked once per render), `state`, `hide-when`, `list` in layout order with pagination
  over all slots of the char, `empty` for free list slots, `update-every` = one entity-scheduler timer per view,
  cancelled on dispose; a source/state/placeholder/render that throws is logged once and renders as empty.
- Carry-over: `MenuChecker` placeholders are warning-only (item keeps clicks, tag shown as text); `action:` / `list:` /
  `state:` still inert. `isKnownTag` list removed: `TextService#hasTag(String)` added in core (standard tags + the
  service's base resolvers) with a unit test.
- Clicks are answered by what the viewer saw (`Renderer.Shown` per slot), not by recomputing state/list.

Tests: `PaginationTest` (0, 1, exact page, many, clamp), `ViewRegistryTest` (two viewers same menu, back stack per
viewer, cap, stale-view remove), `RendererTest` (placeholder `<red>` stays text, player named like markup, state
switch, hide-when, pages in title, two viewers keep own page/data, reload-only-some-lists, throwing source),
`MenuCheckerTest` (warning-only placeholders, hasTag), `TextServiceTest.hasTag`. Self-test `gui.render`.

Limits / notes for review
- `gui.render` cannot open a real view: the smoke server has no player. It renders through
  `MenuType.preview(playerName, context, page)` into a plain `Inventory` (holder = the library's) and asserts slots,
  pages, hide-when, state, empty item, placeholder text and reload. That is **one new public method**, marked
  `@ApiStatus.Internal` (like `TextService.create()`); the task implied a seam but did not name one. Remove or rename
  if you prefer another way. `open / back / close / quit / title re-open / update-every` are therefore covered by unit
  tests of their parts only; the go-mcbots E2E (ROADMAP) is where they get exercised with a real client.
- `open:<menu>` opens the target without a context (`null`); a menu that needs a context should be opened from code.
- `back` restores the previous menu with its context and page; the stack is cleared when the viewer closes the menu
  (ESC / `close`), quits, dies or changes world.
- Click safety (input slots, double-click, shift-click, debounce) is P2-21; for now every click and drag on our
  inventory is cancelled, including clicks in the player's own inventory while a menu is open.


## Review (planner, 2026-10-20)
Approved. Re-open on title/shape change is the right call (1.21.11 has no Component title setter); stale close events
are correctly ignored (`view.inventory() != event inventory`, `registry.remove(id, view)`). `MenuType#preview`
(`@ApiStatus.Internal`) accepted for smoke tests. Carried into P2-21: listener unregister order on close, re-open must
carry cursor + input items.
