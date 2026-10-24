# P2-31 Typed submit, server-side validation, re-show with errors
Status: todo · Approved: yes · Model: sonnet · Effort: high
Module: synthetixlib-dialog · Depends on: P2-30 · Design: dialog.md §3, §4

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/dialog.md and docs/plan/tasks/P2-31-dialog-typed-submit.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- **Carry-over from P2-29/P2-30 review (do these first, they were skipped in P2-30):**
  - Input keys on the wire: vanilla input keys are `[a-zA-Z0-9_]+`; file keys may contain `-`. `DialogRenderer.input`
    and `ViewManager.readAnswers` currently pass the file key raw. Send `-` as `_`, read answers by the wire key, map back
    to the file key; two file keys colliding after mapping (`a-b` + `a_b`) → config error at load, second input dropped.
    Self-test: render + fake response with key `my-key` on both versions (also tells whether Paper refuses `-` itself).
  - Item body size: self-test builds Paper `DialogBody.item` with width/height 256 and 257; fix `DialogParser.MAX_ITEM_SIZE`
    if Paper disagrees; write the real limit in Result.
  - Debug log once per service: the name of the thread the first dialog callback arrives on (owner reads it in P2-33).
- `click.response()`: `number(key)` (double), `integer(key)`, `text(key)`, `bool(key)`, `option(key)`; missing key →
  `Optional.empty()` / validation error, never an NPE.
- `submit(name, Record.class, (form, click) -> …)`: build a node from the `DialogResponseView` values and decode it with
  the config `Codecs` record mapping (kebab-case keys, `@Range`, `@NotEmpty`, enums for option ids, number float →
  int/long/double; non-integral value for an int component = error). No reflection in the dialog module (ADR 0013).
- Server re-checks the file's own limits before the record: text `max-length`, multiline `max-lines`, number in
  `min..max` and on `step`, option id in the list (modified clients, §4).
- Any error → the dialog is shown again (fresh callbacks) with the `errors.<key>` line (or a default lang message) above
  the body and every submitted value passed back as `initial`; handler not called. Placeholders `<min> <max> <value>`.
- `submitAsync`: handler on `scheduler().async()`, result back on the player's thread; view stays consumed while running.
- Text input values shown again are plain text components (no MiniMessage parsing).

## Tests
Unit: every kind mapping, each limit violated, enum option, int from 12.5 refused, missing key, MiniMessage in text
stays literal, async handler thread hop. Self-test `dialog.submit`: fake `DialogResponseView` → record on both versions;
note in Result whether 26.3 returns the same value types as 1.21.11.

## Acceptance criteria
- [ ] invalid input never reaches the handler (tests per rule)
- [ ] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
(agent fills in)
