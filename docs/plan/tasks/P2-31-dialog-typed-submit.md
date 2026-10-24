# P2-31 Typed submit, server-side validation, re-show with errors
Status: review · Approved: yes · Model: sonnet · Effort: high
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
- [x] invalid input never reaches the handler (tests per rule)
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
Ran `gradlew.bat check smokeTest`: green (unit tests incl. 19 new in `FormSubmitTest`, ArchUnit, smoke 82/82 on 1.21.11 and 26.3).

- **Input keys:** Paper itself refuses `-` in an input key (`dialog.limits` builds a dialog with `my-key`: rejected, `my_key`: accepted, same on both versions). The file key goes out as `my_key` (`InputKeys.wire`), answers are read by the wire key and stored under the file key. `a-b` + `a_b` in one file → error `inputs.a_b`, second input dropped. Self-test `dialog.submit` proves an answer under `my-key` is no answer.
- **Item body size:** Paper accepts 256 and rejects 257 (width and height), so `DialogParser.MAX_ITEM_SIZE = 256` was right; unchanged.
- **Callback thread:** logged once per service at FINE (`the first dialog callback arrived on thread '…'`), so it needs the plugin logger at FINE to be seen. Not measured here (no client).
- **Value types on 26.3:** `DialogResponseView` has the same `getText/getBoolean/getFloat` signatures on 26.3 (the fake client implements it and the library calls it there). What a real client sends for option/number is still unmeasured.
- **New API:** `DialogClick.response()` → `DialogResponse` (`number`, `integer`, `text`, `bool`, `option`, all `Optional*`, only values within the file's limits).
- **Server checks (`FormValidator`):** text length, a newline in a single-line input, `max-lines`, number in `min..max` and on `step` (the max is always allowed, like the slider), option in list, wrong kind / missing = refused. Floats are read as their decimal form (0.1f → 0.1). Then the config codecs: `@Range`, `@NotEmpty`, enums, int/long from whole numbers only.
- **Refused:** the dialog is shown again with fresh buttons, typed values as initial, one line per bad input above the body: `errors.<key>` (`<min> <max> <value>`, value plain text), else lang key `invalid-input`, else "Invalid value: <key>". Handler not called.
- **submitAsync:** handler on `scheduler().async()`; view stays consumed; when it ends the dialog closes unless the handler called `reopen/next/close`.
- Not done / unchanged: refusals inside a list dialog's child view are not shown (list children have no forms in practice).
