# P2-19 GUI module setup, menu file model, GuiService.menu()
Status: done · Approved: yes · Model: sonnet · Effort: high
Module: synthetixlib-gui · Depends on: P2-18 · Design: gui.md §1, §2 (registration only), §10

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/gui.md and docs/plan/tasks/P2-19-gui-module-and-menu-files.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- New module `synthetixlib-gui` (AGENTS.md "Adding a library module" checklist): `api(project(":synthetixlib-config"))`,
  package `com.github.dewarepk.synthetixlib.gui`, module AGENTS.md + CLAUDE.md, archtest + example wiring,
  `GuiSelfTests` registered in `SelfTestTrigger`.
- Parsed, immutable model of a menu file (§1), built with the config module (records + codecs, no new YAML code):
  `title`, `type` (chest rows 1-6 | hopper | dispenser), `rows`, `update-every` (config Durations, ticks `20t` allowed),
  `layout` (must match the type's size; space = empty), `items` per char: config ItemStack readable form +
  `hide-tooltip`, `tooltip-style`; `list` + `empty` + `loading`; `state` + `states`; `hide-when`
  (`first-page`|`last-page`); `input` + `accept` + `max-amount`; `sound`; `click` as a list (any click) or a map
  per click type (`left right shift-left shift-right middle drop`).
- Click actions parsed into a sealed type: `close back refresh page-next page-previous open:<id> command:<cmd>
  console:<cmd> message:<text> sound:<key> action:<name>`. Unknown → config error with file + key, item inert.
- `GuiService.create(lib, configs)` attaches to lib. `gui.menu(id)` → builder; menu file = `menus/<id>.yml`, bundled
  default copied from the jar once (never overwritten, comments kept, like config files). Builder methods from §2:
  `placeholder`, `list`, `onListClick`, `state`, `action`, `withContext` — stored only (views are P2-20).
  `build()` cross-checks: every `action:`, `list:`, `state:` and custom placeholder used by the file must be
  registered → otherwise warn with file + char, that item inert. Unknown char in layout / char without item → error,
  slot empty. `gui.reload()` re-reads all files (returns FilesReloadResult-like report).

## Tests
Unit: every key of §1 parses; size mismatch per type; unknown char; char without item; action syntax (each built-in,
unknown, empty); click map vs list; durations; unregistered action/list/state/placeholder reported; bundled copy not
overwritten. Self-test `gui.menu-files`: bundled test menu copied, parsed, unknown action reported.

## Acceptance criteria
- [x] model + parsing match gui.md §1 (deviations in Result)
- [x] no static state, no reflection outside config.mapping (ArchUnit green, rules untouched)
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
Ran `gradlew.bat check smokeTest`: BUILD SUCCESSFUL. 81 new unit tests in `synthetixlib-gui` (ArchUnit rules untouched and
green over the new module); self-test `gui.menu-files` PASS on Paper 1.21.11 and 26.3 (61/61 tests each).

What was added
- Module `synthetixlib-gui` (settings, archtest, example wiring, module AGENTS.md/CLAUDE.md). `GuiService.create(lib, configs)`,
  `gui.menu(id)` -> `MenuBuilder<C>` (`placeholder list onListClick state action withContext build`) -> `MenuType<C>`
  (`id() file() isInert(char)`), `gui.reload()` -> `FilesReloadResult`. `MenuView<C>` / `MenuClick<C>` exist as interfaces
  only (needed by the builder signatures; implemented in P2-20).
- Model in `gui.file` (public + `@ApiStatus.Internal` so the config codecs can map the records): `MenuFile`, `MenuItem`,
  `ClickAction` (sealed, `parse`/`format`), `ClickBindings`, `ClickKind`, `MenuKind`, `HideWhen`, `Ticks`. Parsing
  (`MenuParser`) uses the config `Codecs` (record mapping) plus one registered codec for `click:`; YAML via `YamlTrees`.
- Bundled default copied byte for byte from `menus/<id>.yml` in the jar when the file is missing (comments kept), never
  overwritten, jar not even opened if the file exists; a deleted file is copied again on reload.
- `build()` cross-check (`MenuChecker`): unregistered `action:` / `list:` / `state:` / placeholder -> warning
  `items.<char>: ... , the item is inert`, logged as `[menus/<id>.yml] ...` and returned by `reload()`.

Deviations / decisions to review
- `update-every` `20t`: config `Durations` has no tick unit, so `gui.file.Ticks` handles `<n>t` and delegates durations
  (rounded up to ticks, 1t..1 day) to `Durations`. Config module untouched.
- Unknown keys (file and item level) are errors, as §1 says, but the item is kept (unknown key ignored) instead of
  replaced by a "default item". An item that fails to decode (bad material, amount 500...) is dropped: its slots stay
  empty and the layout does not report its character a second time.
- A bad click action leaves the item visible but with NO click behaviour at all (not just that step), so a half-valid list
  never runs part of its steps.
- YAML 1.1 reads unquoted `on:`/`off:`/`yes:`/`no:` keys as booleans, so the design's own `states: { on: ..., off: ... }`
  arrives as `true`/`false`. `MenuItem.stateItem(name)` maps `on/yes/true` and `off/no/false` back; P2-20 must use it
  instead of `states().get(...)`.
- Only bare `<word>` tags count as placeholders (MiniMessage tags with arguments, `<#hex>`, closing tags and the standard
  no-argument tags do not). A custom tag resolved by `lib.text()` itself still has to be registered with `placeholder()`
  or its item is flagged inert. Title placeholders only warn.
- File that is not valid YAML: first `build()` gives an empty 3-row chest (error logged), a later `reload()` keeps the
  last good menu. No file and no bundled copy: `build()` throws `IllegalStateException` (packaging error).
- `onListClick<E>` / `list<E>` are generic but the link between them is by id only (erased); `build()` throws if
  `onListClick` has no `list`. If inference gives `Object`, call `.<Lot>onListClick(...)`.
- Not unit-testable offline (need a server, covered by the self-test): anything that builds an `ItemStack`; the real
  Bukkit `Material`/`NamespacedKey` codecs. Unit tests use `Material.matchMaterial`, which works without a server.
- `.idea/gradle.xml` shows as modified: written by the IDE's Gradle sync, not by this task.


## Review (planner, 2026-10-20)
Approved. Smoke 61/61 on 1.21.11 + 26.3, ArchUnit untouched. Accepted as built: `gui.file.Ticks` (gui-only),
`MenuItem.stateItem` for YAML 1.1 booleans, invalid action → whole item has no click, unknown key keeps the item,
undecodable item → empty slot, public empty `MenuView` / `MenuClick` interfaces.
Changed in P2-20 (carry-over): an unknown placeholder only warns (it is text, not behaviour; inert stays for
action/list/state); known tags come from MiniMessage + `lib.text()` instead of a hard-coded list.
