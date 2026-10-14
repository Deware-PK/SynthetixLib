# P2-08a Config setup polish (review findings)
Status: done · Approved: yes · Model: sonnet · Effort: low
Module: synthetixlib-archtest, synthetixlib-core · Depends on: P2-08

## Prompt (paste into Claude Code)
Read AGENTS.md and docs/plan/tasks/P2-08a-config-setup-polish.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Findings to fix (from planner review of P2-08)
1. **`setAccessible` rule does not catch real calls.** `callMethod(AccessibleObject.class, "setAccessible", boolean.class)`
   matches only call sites whose bytecode owner is `AccessibleObject`. `Field`, `Method` and `Constructor`
   override `setAccessible(boolean)`, so javac emits e.g. `Field.setAccessible` and `field.setAccessible(true)`
   passes the rule. Replace it with a `callMethodWhere(...)` predicate: target name is `setAccessible` or
   `trySetAccessible` (covers the static `AccessibleObject.setAccessible(AccessibleObject[], boolean)` too)
   and target owner is assignable to `java.lang.reflect.AccessibleObject`.
   Prove it: add a small fixture class under archtest **test** sources (e.g. `archtest.fixture.SetsAccessible`
   calling `Field#setAccessible(true)`, `Method#trySetAccessible()` and the static array overload), import only
   that class with `ClassFileImporter` and assert the rule fails (`assertThrows(AssertionError.class, ...)`).
   Extract the rule into a `static final ArchRule` so the real test and the fixture test share it.
   The fixture must not be part of the classes checked by the normal rules.
2. **`Resources.close()` catches `Throwable`.** That also swallows `Error`s (OOM, LinkageError). Catch
   `Exception` instead; keep the logging/isolation behaviour. Update `ResourcesTest` if needed.

## Acceptance criteria
- [x] setAccessible rule catches Field/Method/Constructor calls, trySetAccessible and the static overload; fixture test proves it
- [x] `Resources` catches `Exception`, tests green
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
1. `NO_SET_ACCESSIBLE` is now a shared `static final ArchRule` in `ArchitectureTest` using `callMethodWhere`: target name matches `setAccessible|trySetAccessible` and target owner is assignable to `AccessibleObject`. New test `setAccessibleRuleCatchesViolations` imports only the fixture `archtest.fixture.SetsAccessible` (test sources, so excluded from the normal rules by `DO_NOT_INCLUDE_TESTS`) and asserts the rule fails and names `Field.setAccessible`, `Method.trySetAccessible` and the static `AccessibleObject.setAccessible`.
2. `Resources.close()` now catches `Exception` instead of `Throwable`; `ResourcesTest` unchanged and green.
`gradlew.bat check smokeTest`: BUILD SUCCESSFUL (smoke passed on 1.21.11 and 26.x).
