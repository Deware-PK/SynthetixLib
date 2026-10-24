# P2-29 Dialog module setup, dialog file model, DialogService.dialog()
Status: done · Approved: yes · Model: sonnet · Effort: high
Module: synthetixlib-dialog · Depends on: P2-28 · Design: dialog.md §1, §2 (registration only), §12

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/dialog.md and docs/plan/tasks/P2-29-dialog-module-and-files.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- New module `synthetixlib-dialog` (AGENTS.md "Adding a library module" checklist): `api(project(":synthetixlib-config"))`
  (NOT gui), package `com.github.dewarepk.synthetixlib.dialog`, module AGENTS.md + CLAUDE.md, archtest + example wiring,
  `DialogSelfTests` registered in `SelfTestTrigger`.
- Immutable model of a dialog file (§1), parsed with the config module (records + codecs, reuse the approach of gui's
  `MenuParser`; no new YAML code): `title`, `external-title`, `type` (notice | confirmation | multi-action | list | links),
  `can-close-with-escape`, `pause`, `body` (message + width | item: config item / `slot: hand|offhand` / `placeholder:`
  + description, show-decorations, show-tooltip, width, height), `inputs` (ordered map: text | bool | option | number with
  all keys of §1; `option` ids = map keys, at most one `initial`), `buttons` (per type: confirmation = `yes` + `no`;
  multi-action = ordered map + optional `exit` + `columns`; notice = one `ok`; list = `dialogs: [ids]` + `exit` +
  `columns` + `button-width`; links = `exit` + `columns` + `button-width`), button `label tooltip width click`, `errors`.
- Range checks at load (dialog.md §9): widths 1..1024, text max-length >= 1, number `min < max`, `step > 0`,
  `initial` inside range, columns >= 1. Violations → config error with file + key, element dropped / value clamped (say which in Result).
- Click actions as a sealed type: `close back open:<id> menu:<id> command:<cmd> console:<cmd> message:<text> url:<https…>
  copy:<text> sound:<key> action:<name>`. `menu:` parses always; it only works when linked (P2-32). `url:` must be
  http/https. Unknown → config error, button inert.
- `DialogService.create(lib, configs)` attaches to lib; `dialogs.dialog(id)` → builder; file `dialogs/<id>.yml`, bundled
  default copied from the jar once, never overwritten (same as gui). Builder (stored only, behaviour in P2-30/31):
  `placeholder`, `action`, `submit(name, Class<R extends Record>, handler)`, `submitAsync`, `input(key, initialFn)`,
  `onClose`, `withContext`. `build()` cross-check: every `action:` and placeholder used by the file registered; every
  `submit` record component (kebab-case) matches an input key and its kind (number→int/long/double/float,
  bool→boolean, text→String, option→String or enum) → else warn with file + key, that button inert.
  `dialogs.reload()` → FilesReloadResult-like report.

## Tests
Unit: every key of §1; each type's button rules; range checks; action syntax (each built-in, unknown, bad url);
submit/record mismatch reported; bundled copy not overwritten. Self-test `dialog.files`: bundled test dialog copied,
parsed, unknown action reported.

## Acceptance criteria
- [x] model + parsing match dialog.md §1 (deviations in Result)
- [x] no static state, no reflection outside config.mapping (ArchUnit green, rules untouched)
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
Ran: `gradlew.bat check` (green: 64 dialog unit tests, ArchUnit untouched, forbidden patterns, shaded jar) and
`gradlew.bat smokeTest` (PASS 76/76 on 1.21.11 and 26.3, incl. new `dialog.files`). Not committed.

Built: module `synthetixlib-dialog` (wired in settings, archtest, example; AGENTS.md + CLAUDE.md), `dialog.file` model,
`DialogParser`, `DialogService.create/dialog/reload`, `DialogBuilder` (placeholder, action, submit, submitAsync, input,
onClose, withContext), `DialogType` (`file()`, `isInert(buttonPath)`), `DialogChecker` + `FormCheck`, bundled-copy store.
Self-test `dialog.files` + `example/src/main/resources/dialogs/selftest-dialog.yml`.

Decisions where the spec was open (change if you disagree):
- **`buttons:` layout.** One flat section whose keys depend on `type`: confirmation `yes`/`no`; notice `ok`;
  multi-action = named buttons (file order) + `exit` + `columns` (so `exit`/`columns` are reserved names); list =
  `dialogs` + `exit` + `columns` + `button-width`; links = `exit` + `columns` + `button-width`. Unquoted `yes:`/`no:` keys
  (YAML booleans) are mapped back to `yes`/`no`.
- **Range handling.** Clamped (error reported): widths to 1..1024, `max-length` and `columns` to >= 1, multiline
  max-lines/height >= 1, number `initial` into min..max, text `initial` cut to max-length (32 when absent).
  Cleared: `step <= 0`. Dropped: number input with `min >= max`, option input with no options, extra `initial: true` option
  loses its flag. Bad click action -> button keeps no clicks (inert), as in gui.
- **Item body size 1..256** (width/height) is my recollection of the vanilla limit, not checked against Paper: confirm in P2-30.
- **Input keys** must match `[a-z0-9_-]{1,64}`, else the input is dropped. Risk for P2-30/31: vanilla may only allow
  `[a-zA-Z0-9_]` as an input key on the wire, so `-` may need mapping to `_` there.
- **Missing required buttons** (confirmation yes/no) -> error + default button with label `<lang:gui.yes>` / `gui.no`;
  notice without `ok` -> default `<lang:gui.ok>`, no error.
- **`FormCheck` uses no reflection:** it decodes sample answers (text "x", bool true, number min/max, each option id) into
  the submit record with the config codecs, plus a "text where a number/bool goes" probe because the String codec accepts
  numbers and booleans. Inputs the record does not read are fine. Option ids are checked against enum components.
- **Placeholders:** `player` built in; `min`, `max`, `value` also in `errors:` lines only.
- `DialogView` (player, context) and `DialogClick` (view, player) are minimal public interfaces so the builder compiles;
  P2-30/31 extend them. `DialogType.open` is not there yet (P2-30). `menu:` parses; linking is P2-32.
- No public API change in other modules. Escape-key and `after-action` are not part of the file model.

## Review (planner, 2026-10-24)
Approved. Smoke 76/76 on 1.21.11 + 26.3, ArchUnit untouched, no static state, FormCheck without reflection is a good fit.
Accepted as built: flat `buttons:` per type (`exit`/`columns` reserved in multi-action), clamp/drop rules, default
buttons via vanilla keys `gui.yes`/`gui.no`/`gui.ok`, minimal `DialogView`/`DialogClick`.
Carry-over to P2-30: input keys on the wire (`-` → `_` mapping, collision check) and verify item body 1..256 against Paper.
