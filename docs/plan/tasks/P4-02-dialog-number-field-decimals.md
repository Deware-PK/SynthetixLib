# P4-02 dialog: `decimals` limit on a decimal number-field
Status: review · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-dialog · Depends on: — · Design: dialog.md §1, §2 (limits from code), §3 (number-field), §4
Requested by: InteractiveAuction 2.0 (money with 2 decimals), lib gap L-4

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/dialog.md (§1–§4, §13) and docs/plan/tasks/P4-02-dialog-number-field-decimals.md, then
implement the task. Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Goal
A `number-field` with `format: decimal` accepts any number of digits after the dot today (`FormValidator.DECIMAL`
pattern `(?:\.\d+)?`). Money has a fixed number of decimals (Vault economies usually 2). The server must refuse
`12.345` when the plugin allows 2 decimals, show the normal error line, and the plugin must be able to set the limit from
its own config (the server owner chooses the currency's decimals).

## Spec
- File key on `number-field`: `decimals: <0..10>` (only meaningful with `format: decimal`; on `integer` → load error
  "decimals is only used with format: decimal", ignored). Absent = no limit (today's behaviour, no break).
- Code: `DialogBuilder.inputDecimals(String key, Function<DialogView<C>, Integer> decimals)` — like `inputRange`, asked
  for every showing and again when the answer is checked; must be 0..10 (else the file value / no limit is used with a
  warning once). Unknown key / not a decimal number-field → warning at `build()` like `inputRange` does.
- Server check (`FormValidator.field`): after parsing, `value.stripTrailingZeros().scale() > decimals` → refused
  (`12.50` with 2 decimals is fine, `12.505` is not; `12` is fine). `decimals: 0` on a decimal field = whole numbers but
  still a `BigDecimal` in the record.
- Error line placeholder `<decimals>` available in `errors.<key>` (next to `<min> <max> <value>`); default lang line
  `invalid-decimals` (en + th) used when the file has no `errors.<key>`.
- `initial` with more decimals than allowed → load warning, rounded `HALF_UP` to the limit.
- `click.response().number(key)` keeps returning only values that passed the check.

## Tests
Unit: 2 decimals → `12`, `12.5`, `12.50`, `1,000.25` accepted; `12.505`, `0.001` refused; `decimals: 0` on decimal;
`decimals` on integer = load error; `inputDecimals` overrides the file and is re-read on submit (change it between show and
submit → the new limit applies); out-of-range from code → warning + fallback; `<decimals>` in the error line.
Self-test `dialog.decimals` (both versions): fake response `12.345` with 2 decimals → re-show with the error; `12.34` →
record gets `BigDecimal("12.34")`.

## Docs (same task)
- reference `dialog.md`: the key, `inputDecimals`, the placeholder; API table "since 0.9.0".
- rule `dialog-money-is-a-number-field.md`: add "set `decimals` from your currency settings".
- `docs/design/dialog.md`: §14 "0.9.0 additions". Example `dialogs/sell.yml` demo: `decimals: 2`, raise its
  `config-version` (rule `files-bump-config-version`). CHANGELOG "0.9.0".

## Acceptance criteria
- [x] too many decimals never reach the handler (unit + self-test)
- [x] no change for files without `decimals` (existing tests green)
- [x] docs + CHANGELOG + snippet check green
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
`decimals` on `NumberFieldInput` (parser: load errors for integer / out of 0..10, initial rounded HALF_UP); `InputLimits.decimals`
(third component, 2-arg constructor kept); `FormValidator.field` refuses `stripTrailingZeros().scale() > decimals`;
`DialogBuilder#inputDecimals` (+ `Registrations`, checker warning, `<decimals>` allowed in `errors.*`). Error line: `Failure` has
`decimals` + `tooManyDecimals`; no `errors.<key>` -> lang `invalid-decimals` -> `invalid-input` -> English. Unit tests:
`NumberFieldDecimalsTest`; self-test numberField extended (12.345 refused, 12.34 -> BigDecimal). Demo `sell.yml` price is now
decimal, `decimals: 2`, `config-version: 3` (`SellForm.price` BigDecimal, upgrade self-test updated); `invalid-decimals` in en + th.
Out-of-range `inputDecimals` warns per showing (not once). Docs: reference, rule, design §14, CHANGELOG.
