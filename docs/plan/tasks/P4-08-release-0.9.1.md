# P4-08 Release 0.9.1: database gaps from IA2-03
Status: todo · Approved: yes · Model: sonnet · Effort: low · Ship: yes (database 0.9.1)
Module: synthetixlib-database · Depends on: P4-06, P4-07 · Design: ROADMAP Phase 4 (0.9.x)

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/plan/ROADMAP.md (Phase 4) and docs/plan/tasks/P4-08-release-0.9.1.md, then do the task: run
`gradlew.bat syncSnippets check smokeTest publishToMavenLocal` (MySQL tests must run), set Status: review, then ship:
`powershell -ExecutionPolicy Bypass -File scripts/ship-module.ps1 -Module database -Version 0.9.1`.
If the permission classifier blocks the PowerShell call, stop and tell me; do not hand-edit what the script does.

## Goal
Ship `insert` (P4-06) and aborted transactions (P4-07) on JitPack as `v0.9.1` so InteractiveAuction 2.0 can drop its
workarounds (IA2-03b).

## Steps
- Version `0.9.1-SNAPSHOT` in root `gradle.properties` and the template's `synthetixLocalVersion`.
- CHANGELOG **0.9.1** (patch: additions only, no breaking change), README status `0.9.1`, `llms.txt` (new APIs + the new
  rule), SKILL.md "Rules by impact" lists `db-expected-race-no-severe`.
- After the push: tell me to check `https://jitpack.io/#Deware-PK/SynthetixLib/v0.9.1` (all modules built).

## Acceptance criteria
- [ ] full check + smokeTest green, MySQL tests not skipped
- [ ] tag `v0.9.1` pushed; JitPack build green (owner)

## Result
