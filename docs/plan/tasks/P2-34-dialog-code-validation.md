# P2-34 Dialog: number-field input, code-side validation, input limits from code
Status: done · Approved: yes (owner 2026-10-25) · Model: sonnet · Effort: high
Module: synthetixlib-dialog · Depends on: P3-06 · Design: dialog.md §1, §3, §4 · Found by: evals auction-lite + shop run 1

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/dialog.md and docs/plan/tasks/P2-34-dialog-code-validation.md, then implement the task.
Run `gradlew.bat syncSnippets check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Why
Money and other wide-range numbers cannot be a slider (owner decision: money is typed). Limits that come from config.yml
(min/max price, max per purchase) could not be expressed, so both eval agents re-built "re-show with error" by hand.

## Spec
1. **`type: number-field`** input (a vanilla text input underneath): keys `label`, `format: integer | decimal`
   (default integer), `min`, `max` (optional), `initial`, `max-length` (default 18), `width`. Server parses
   (trim, accept `1,000` and `1_000`, `decimal` accepts `.`; no exponent, no NaN/Infinity), checks min/max, then
   the value goes to the record as `long`/`int` (integer) or `double`/`BigDecimal` (decimal). `response()` gets
   `integer(key)` / `number(key)` for it. Wrong text → same error re-show as other file limits; `<value>` = what
   was typed (plain text). FormCheck: number-field fits long/int/double/BigDecimal components.
2. **Limits from code, per showing:** builder `.inputRange(key, view -> Range)` for `number` (slider) and
   `number-field`, `.maxLength(key, view -> int)` for text. Read when the dialog is rendered and again when the answer
   is validated (the same values the player saw: store them in the view). Overrides the file values; the file value is the
   fallback. `<min>`/`<max>` placeholders use the effective values. Small `Range` record (min, max, step optional).
3. **Code-side validation:** `submit(...)` / `submitAsync(...)` get an optional validator:
   `.validate("sell", (form, click) -> Optional<FieldError>)` (by submit name). `FieldError.of(key, lang or MiniMessage,
   TagResolver...)` or `FieldError.form(...)` (not tied to an input). Runs on the player's thread after file limits and
   record decoding, before the handler. Non-empty → re-show exactly like a file-limit failure (error line above the body,
   typed values kept, fresh callbacks), handler not called. Several errors: `List<FieldError>` overload.
4. Docs: dialog.md (design) §1/§3/§4 + deviations; skill `references/dialog.md` replaces the "wide numbers / limits from
   config" workaround section with these APIs (snippets); new rule `dialog-money-is-a-number-field.md` (HIGH): money or
   any wide range → number-field, slider only for small ranges (≈ ≤ 100 steps). CHANGELOG 0.8.0 (no breaking change).
5. Example: demo sell dialog (`dialogs/sell.yml`) uses number-field for the price with `inputRange` from settings and a
   `validate` (e.g. refuse a price above 10 × the item's base value, as a demo).

## Tests
Unit: number-field parse table (`1,000`, ` 42 `, `-1`, `1e3`, `12.5` for integer, `12.50` decimal, 19 digits,
empty, NaN), min/max, record kinds; inputRange render + validation use the same values; validator errors re-show and
skip the handler; list of errors. Self-test `dialog.number-field` (render + fake answer) on both versions.

## Acceptance criteria
- [x] number-field, inputRange/maxLength, validate implemented and documented (design + skill + rule + CHANGELOG)
- [x] `gradlew.bat syncSnippets check smokeTest` green

## Verify
gradlew.bat syncSnippets check smokeTest

## Result
Ran `gradlew.bat syncSnippets check smokeTest`: green (unit tests, ArchUnit, forbidden patterns, checkSnippets, shaded-jar
relocation; smoke on Paper 1.21.11 and 26.3, 86 self-tests, `dialog.number-field` passes on both).

Done
- `type: number-field` (`DialogInput.NumberFieldInput`, `NumberFormat`), parsed/clamped in `DialogParser`, rendered as a text
  input (`DialogRenderer`), read by `FormValidator` (trim, `1,000` / `1_000` in groups of three, no exponent / NaN, long range,
  min / max / max-length exact with `BigDecimal`) into `Long` / `BigDecimal`; `BigDecimalCodec` (registered by `DialogService`)
  for `BigDecimal` components; `FormCheck` samples it (long / int / double / BigDecimal fit, String / boolean make the buttons
  inert); `click.response().number/integer` read it. Typed text is kept as typed when the dialog is shown again.
- `DialogBuilder#inputRange` / `#maxLength` + new `Range`: resolved per showing into `InputLimits`, stored in the view, used by
  render, validation, `ResponseImpl` and `<min>` / `<max>` of the error line; file value is the fallback. Checker warns for a key
  that cannot use them.
- `DialogBuilder#validate` / `#validateAll` + new `FieldError` (`of` / `form`): run in `ViewManager.submit` after file limits and
  record decoding, before the handler (also before `submitAsync` goes async); errors re-show like a file-limit failure.
- Tests: `NumberFieldTest` (parse table incl. `1,000`, ` 42 `, `-1`, `1e3`, `12.5` integer, `12.50` decimal, 19 digits, empty,
  NaN; min / max; record kinds; code limits vs file limits; error line values; form check), `DialogFilesTest` (builder rules,
  checker warnings). Self-test `dialog.number-field` (render + fake answers: code range, code max-length, validator, range
  re-asked per showing).
- Docs: design `dialog.md` (§1, §2, §3, §4, §13 deviations), skill `references/dialog.md` (workaround section replaced; new
  snippet `dialog-limits-validate`), rule `dialog-money-is-a-number-field` (HIGH; SKILL.md list + llms.txt), `CHANGELOG.md`
  0.8.0 (no breaking change), module `AGENTS.md`.
- Example: `sell.yml` price is a number-field; `DemoDialogs` uses `inputRange` from new `Settings.sellMinPrice/sellMaxPrice`
  (`sell-min-price` / `sell-max-price`) and a `validate` (price <= 10 x 100 x stack size); `th_TH` + en texts. `SellForm.price` is now `long`.

Differences from the spec (also in design §13)
- `validate(name, Record.class, ...)` takes the record class (Java cannot infer the record type of an implicit lambda from the
  name); the `List<FieldError>` form is `validateAll` (two lambdas that differ only in return type are ambiguous).
- `FieldError` text is one MiniMessage string; a language message is `<lang:key>` in it.
- `maxLength` is also accepted for a `number-field`; the limits of a registry dialog stay the file's.
- ArchUnit forbids static library-typed fields, so "no limits" is `InputLimits.file()`, not a constant.

## Review (planner, 2026-10-25)
Approved. Smoke 86/86 on 1.21.11 + 26.3 (new `dialog.number-field`). Accepted deviations: `validate(name, Form.class, fn)`
(type inference), `validateAll` for lists, `FieldError` = one MiniMessage string (`<lang:key>` for languages),
`InputLimits.file()` instead of a static constant. Strict `1,000` grouping (refuse `12,5`) is a good call.
Not yet seen on a real client → owner check added to P2-36 Part B.
