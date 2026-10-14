# P2-06 Core wrap-up: example usage + local publish
Status: done · Approved: yes · Model: sonnet · Effort: low
Module: synthetixlib-core, synthetixlib-example · Depends on: P2-02..P2-05

## Prompt (paste into Claude Code)
Read AGENTS.md and docs/plan/tasks/P2-06-core-wrapup.md, then implement the task.
Run the Verify commands, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- In `SynthetixExamplePlugin`, add a small, readable demo (outside self-test mode) using every core
  service exactly like the snippets in docs/design/core.md: a global timer that logs every 60 s,
  a `/`-less demo is fine (no commands yet) — e.g. on player join give a demo item built with ItemBuilder
  + PDC key, greet with TextService, scheduled on the entity scheduler.
- Every public type in core has class Javadoc with a snippet; package-info.java per package.
- Bump version to `0.1.0-SNAPSHOT` (already) and run `publishToMavenLocal`; confirm the jars
  (main, sources, javadoc) exist in `~/.m2/repository/com/github/dewarepk/synthetixlib-core/`.
- Update `synthetixlib-core/AGENTS.md` with the final package list; set docs/design/core.md Status to
  "Implemented (0.1.0)" and list any deviations; ROADMAP Phase 2 row 1 → done.

## Acceptance criteria
- [x] demo code compiles (runtime join check left to owner via `runServer`)
- [x] javadoc builds without errors
- [x] `gradlew.bat check smokeTest publishToMavenLocal` green

## Verify
gradlew.bat check smokeTest publishToMavenLocal

## Result
Added join demo + 60 s global timer in `SynthetixExamplePlugin` (skipped in self-test mode), `package-info.java`
for all 5 core packages, removed stale PLACEHOLDER note in `SynthetixLib` javadoc, updated core AGENTS.md,
design doc status, ROADMAP. Ran `gradlew.bat check smokeTest publishToMavenLocal :synthetixlib-core:javadoc`: green
(1.21.11 + 26.x PASS). Jars (main/sources/javadoc) present in ~/.m2. Deviations: none. Manual join test not done.
