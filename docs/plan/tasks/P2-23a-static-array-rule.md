# P2-23a ArchUnit: no static arrays (close the loophole from P2-23)
Status: done · Approved: yes · Model: sonnet · Effort: low
Module: synthetixlib-archtest, synthetixlib-gui · Depends on: P2-23

## Prompt (paste into Claude Code)
Read AGENTS.md and docs/plan/tasks/P2-23a-static-array-rule.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- P2-23 moved `Renderer.VANILLA_PREFIXES` from a static `List` to a `static final String[]` to pass
  `noStaticMutableState`. An array is still mutable static state, so the rule had a gap.
- Tighten the rule (this makes it stricter, which AGENTS.md allows): static fields must not have an array raw type
  either (same `because` text, mention arrays). Do not add exceptions.
- Fix `Renderer`: replace the array with a private static method `isVanillaKey(String key)` that takes the part
  before the first `.` and checks it in a `switch` (no field). Same behaviour, existing unit tests stay green.
- If the new rule finds other static arrays, fix them the same way and list them in Result.

## Acceptance criteria
- [x] rule rejects a static array (add a fixture like `SetsAccessible`, assert the rule fires)
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
- `noStaticMutableState` now also rejects static fields with an array type (`NO_STATIC_ARRAYS`). Synthetic
  fields are skipped (`$VALUES` of enums, `$SwitchMap$` of switches): compiler output, not written code.
- Fixture `StaticArray` + test `staticArrayRuleCatchesViolations` prove the rule fires.
- `Renderer`: `VANILLA_PREFIXES` removed; `isVanillaKey` switches on the part before the first `.`.
- No other static arrays in source (the only hits were the synthetic ones).
- Ran `gradlew.bat check smokeTest`: green (1.21.11 and 26.x self-tests PASS).

## Review (planner, 2026-10-20)
Approved. Rule is stricter (synthetic fields skipped, which is correct), fixture proves it fires, Renderer uses a switch. Goes into the next ship commit (database).
