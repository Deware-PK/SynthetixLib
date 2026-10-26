# P4-05 Release 0.9.0: product gaps from IA 2.0 planning
Status: review · Approved: yes · Model: sonnet · Effort: low · Ship: yes (selftest 0.9.0)
Module: synthetixlib-selftest, synthetixlib-gui, synthetixlib-dialog, synthetixlib-database · Depends on: P4-01, P4-02, P4-03, P4-04 · Design: ROADMAP Phase 4 (0.9.0), docs-skill.md §5

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/plan/ROADMAP.md (Phase 4) and docs/plan/tasks/P4-05-release-0.9.0.md, then do Part A and stop
(Part B needs the owner). After the owner's results are in, do Part C: run
`gradlew.bat syncSnippets check smokeTest publishToMavenLocal`, set Status: review, then ship:
`powershell -ExecutionPolicy Bypass -File scripts/ship-module.ps1 -Module selftest -Version 0.9.0`.

## Goal
Ship the four additions (gui `actionAsync`, number-field `decimals`, `Database.standalone`, `synthetixlib-selftest` +
template dev/smoke servers) on JitPack as `v0.9.0` now, so products (InteractiveAuction 2.0 first) use the released
library instead of a local snapshot. Owner decision 2026-10-26: these belong in the lib anyway; IA 2.0 does not have to
be finished first. Gaps that IA 2.0 hits later ship as 0.9.x.

## Part A (agent)
- Version `0.9.0-SNAPSHOT` in root `gradle.properties` and the template's `synthetixLocalVersion` (if P4-04 did not).
- `skills/synthetixlib/CHANGELOG.md`: section **0.9.0** collecting P4-01..P4-04 (mark any **Breaking** — none expected).
- SKILL.md: module map has `selftest`; "Rules by impact" lists the two new rules (`gui-async-action-for-io` HIGH,
  `test-in-server-for-bukkit-state` MEDIUM). The "Written for SynthetixLib …" line is set by the ship script.
- `README.md`: module table gets `synthetixlib-selftest`; Status → `0.9.0`; "Planned next": `validateAsync`, 0.10.0
  hardening. `llms.txt`: the new module and APIs.
- `jitpack.yml` / build: the new module is published by JitPack like the others (check the module list the JitPack
  build uses, if any).
- `gradlew.bat syncSnippets check smokeTest publishToMavenLocal` green; fresh template copy
  `gradlew.bat build smokeTest -PsynthetixLocal=true` green on both versions.

## Part B (owner)
- In game (`runServer` of the example): `/example menu` → double-click and spam the async "claim" item: one claim,
  the `busy:` item shows while it runs. `/example dialog sell` with `decimals: 2`: `12.345` refused with the error line,
  `1,000.25` accepted.
- Optional: re-run the eval **auction-lite** (`-3`, Sonnet medium, `synthetixLocalVersion=0.9.0-SNAPSHOT`) — expected:
  the mailbox/claim uses `actionAsync`, the price uses `decimals`. Results in RESULTS.md under "0.9.0".

## Part C (agent)
- Fix anything Part B found (with a test), then the Prompt's last two commands.
- After the push: tell the owner to open `https://jitpack.io/#Deware-PK/SynthetixLib/v0.9.0` and check that every
  module (incl. `synthetixlib-selftest`) built.

## Acceptance criteria
- [x] CHANGELOG 0.9.0, README, llms.txt, SKILL.md map/rules updated
- [x] `check smokeTest publishToMavenLocal` green; template smoke green on 1.21.11 + 26.x
- [x] owner's Part B passed
- [x] shipped: tag `v0.9.0` pushed; JitPack build green (owner)

## Verify
gradlew.bat syncSnippets check smokeTest publishToMavenLocal

## Result
Part A done: version/template 0.9.0-SNAPSHOT, CHANGELOG, SKILL.md rules/map were already in place; README (module table, Status 0.9.0, planned, dependency tag) and llms.txt (selftest ref, 2 rules) updated. jitpack.yml publishes all modules, no change. `syncSnippets check smokeTest publishToMavenLocal` green; fresh template `build smokeTest -PsynthetixLocal=true` green (1.21.11 + 26.3). Waiting for Part B (owner).
Part B passed (owner); fixed `<lang:>` error-line values (DialogRenderer.Pass.withExtra) + self-test. Part C: syncSnippets check smokeTest publishToMavenLocal green; shipped via ship-module.ps1.
