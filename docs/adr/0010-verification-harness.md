# 0010. Verification harness

- Status: Accepted
- Date: 2026-10-14

## Context
Development is AI-driven; an agent must be able to prove its work without a human reading logs.

## Decision
Three layers, all runnable from Gradle:
1. `./gradlew check` — compile, JUnit, ArchUnit rules (`synthetixlib-archtest`), forbidden source
   patterns, shaded-jar relocation check.
2. `./gradlew smokeTest` — run-paper boots Paper 1.21.11 (Java 21) and 26.x (Java 25) headless; the
   example plugin runs in-server self-tests after `ServerLoadEvent`, writes `selftest-result.json`,
   shuts down; the task fails unless status is PASS.
3. (from the GUI module on) go-mcbots E2E: bots join a smoke server and attempt real interactions /
   dupe exploits.

## Consequences
Every server-touching feature ships with a self-test. Logic is kept in pure, unit-testable classes
where possible (e.g. GUI click rules as pure functions).
