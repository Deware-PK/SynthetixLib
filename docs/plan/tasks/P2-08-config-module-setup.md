# P2-08 Config module setup + core lib.attach + ADR 0013 rules
Status: done · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-config (new), synthetixlib-core, synthetixlib-archtest · Depends on: P2-07 · Design: config.md §6–7

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/config.md and docs/plan/tasks/P2-08-config-module-setup.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
1. New module `synthetixlib-config` following the AGENTS.md "Adding a library module" checklist
   (`synthetix.library`, `api(project(":synthetixlib-core"))`, AGENTS.md + CLAUDE.md, add to archtest + example).
   Packages: `config` (public entry types), `config.annotation`, `config.codec`, `config.migration`,
   `config.mapping` (internal, reflection), `config.yaml` (internal). Empty package-info.java each for now.
2. Core: `SynthetixLib#attach(AutoCloseable)` — on `close()`, attached resources are closed **first**, in
   **reverse** registration order (they may still need the scheduler to flush), then the scheduler is closed. Exceptions while closing one resource are logged and do not stop the others. `attach` after close → ISE.
   Unit-test ordering + exception isolation with fake resources (no server needed if you extract the logic into a
   package-private `Resources` helper).
3. `Configs.create(SynthetixLib lib)` skeleton (public, attaches itself) with no features yet.
4. ArchUnit (ADR 0013): `noReflection` → allow `java.lang.reflect..` only in `..config.mapping..`;
   new rule: no call to `AccessibleObject#setAccessible` anywhere; `Class.forName` still forbidden everywhere.
5. Self-test `config.setup`: `Configs.create(lib)` works and is closed by `lib.close()` (use a separate lib instance).

## Acceptance criteria
- [x] module builds, published by publishToMavenLocal, included in archtest + example
- [x] `lib.attach` implemented with tests; core.md §1 updated to mention it
- [x] ArchUnit rules updated per ADR 0013, green
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
Implemented as specified, no deviations.
- New module `synthetixlib-config` (settings, archtest + example deps, AGENTS.md/CLAUDE.md, package-info for all 6 packages), `Configs.create(lib)` skeleton that attaches itself.
- Core: `SynthetixLib#attach(AutoCloseable)` backed by package-private `Resources` (reverse-order close, per-resource exception logged, attach after close -> ISE); `ResourcesTest` covers order, isolation, idempotence, attach-after-close. docs/design/core.md §1 updated.
- ArchUnit: `java.lang.reflect` allowed only in `..config.mapping..`; new `setAccessible` ban; `Class.forName` still banned everywhere.
- Self-test `config.setup` (own lib instance, closed via `lib.close()`), registered in `SelfTestTrigger`.
- Ran `gradlew.bat check smokeTest publishToMavenLocal`: BUILD SUCCESSFUL; smoke on 1.21.11 and 26.3 PASS incl. `config.setup`; both modules published to Maven local.
