# P2-22 GUI locale tag, async lists, resource-pack passthrough
Status: done · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-gui · Depends on: P2-21 · Design: gui.md §5, §6, §7

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/gui.md and docs/plan/tasks/P2-22-gui-locale-async-resource-packs.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- `gui.messages(lang, fn)`: `<lang:key>` tag in every menu text (title, names, lore, `message:`), resolved from the
  viewer's locale (LocaleHandle, per player); missing key → warn once, show the key. Mixable with inline text.
- `.listAsync(id, source, render)`: source on `scheduler().async()`, render on the player's thread, `loading:` item
  meanwhile; view closed before the result → result dropped.
- Resource packs (§7): ItemsAdder `:offset_-8::name:` and a Nexo glyph tag (`<glyph:name>`) reach the inventory title
  unchanged (unknown tags kept literally — check how TextService parses; if it strips them, add a passthrough and
  say so). `item-model` / `tooltip-style` / `hide-tooltip` applied on 1.21.11 and 26.x.

## Tests
Unit: lang tag per locale, missing key, async result after close dropped.
Self-tests `gui.title-passthrough` (both placeholder styles), `gui.item-components` (model, tooltip style, hidden tooltip).

## Acceptance criteria
- [x] passthrough self-test green on 1.21.11 and 26.x
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
`gradlew.bat check smokeTest`: BUILD SUCCESSFUL; smoke 1.21.11 (Java 21) and 26.3 (Java 25) both PASS 67/67, including the new
`gui.lang`, `gui.title-passthrough`, `gui.item-components`, `gui.async-list`.

What was built
- `GuiService#messages(LocaleHandle<T>, Function<T, Map<String,String>>)`: `<lang:key>` in every menu text, language chosen
  by `lang.get(viewer, ...)` (so the `perPlayer` setting decides), message parsed with the menu's placeholders. Missing key ->
  the key is shown and one warning logged. A `<lang>` inside a message is not looked up (no loops). Without `messages(...)`
  MiniMessage's own `<lang>` is left alone. Package-private `GuiMessages` + `Renderer.Pass` do the work.
- `MenuBuilder#listAsync(id, source, render)`: source on `scheduler().async()`, answer back on the viewer's thread, `loading:`
  item meanwhile (nothing if the file has none). A refresh keeps the old entries until the new ones arrive; an answer after
  the view ended is dropped. Logic is in the pure, unit-tested `AsyncLoads`; `Registrations.ListBinding` got an `async` flag
  (the old 3-argument constructor stays).
- Resource packs: TextService/MiniMessage does NOT strip them. ItemsAdder `:offset_-8::name:` is plain text and a Nexo
  `<glyph:name>` is an unknown tag that MiniMessage keeps literally, so no passthrough code was needed (only tests).
  `item-model`, `tooltip-style`, `hide-tooltip` were already applied by `Renderer.build`; now covered by a self-test.
- `MenuType#previewTitle` (`@ApiStatus.Internal`, next to `preview`): an inventory cannot be asked for its title without a
  viewer, so the title self-test needs it. Small addition to the internal self-test surface only; no change to the public API.
- Tests: unit `AsyncLoadsTest` (9), `LangAndPassthroughTest` (7). A unit test for "lang tag per locale" cannot build a
  `LocaleHandle` (its host is package-private in config), so the per-locale check is the `gui.lang` self-test (th_TH bot
  via `PolicyBot#locale`); the unit test switches between two fake language files. The "result arrives after the view
  ended" case is unit-tested; in the server `gui.async-list` checks the loading item, that the source ran off the main
  thread and that closing before the answer is harmless (a proxy player cannot receive a delivery).


## Review (planner, 2026-10-20)
Approved. Smoke 67/67 on 1.21.11 + 26.3. `previewTitle` (internal) accepted. Passthrough needed no code — good.
Carried into P2-23: with `messages(...)` set, our `<lang:key>` shadows MiniMessage's standard `<lang>` (translatable),
so `<lang:item.minecraft.diamond>` shows the raw key + a warning. Fix: a key not in our files falls back to
`Component.translatable(key, args)`.
