# P3-06 Eval suite, fixes, ship 0.7.0 (first JitPack release)
Status: todo · Approved: yes · Model: sonnet · Effort: high · Ship: yes (skill 0.7.0)
Module: synthetixlib-skill (docs, not a Gradle module) · Depends on: P3-05 · Design: docs-skill.md §5 · ADR 0015

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/docs-skill.md and docs/plan/tasks/P3-06-skill-evals-and-ship.md, then implement the task.
Part A first; Part B needs the owner. When all boxes are ticked, run `gradlew.bat check smokeTest publishToMavenLocal`,
set Status: review, then ship: `powershell -ExecutionPolicy Bypass -File scripts/ship-module.ps1 -Module skill -Version 0.7.0`.

## Spec — Part A (agent)
- `skills/synthetixlib/evals/`: 3 files (auction-lite, shop, cooldown-command), each = the prompt given to a fresh agent
  (plain product language, no API names) + a checklist the reviewer scores: builds; shaded + relocated; no static
  state; scheduler facade only; MiniMessage only; typed config, no string paths; player texts via locale; correct
  module for the job (dialog for text input); close() in onDisable; dupe-safe inputs (gui input slots / dialog submit).
- `evals/RUNNING.md`: how the owner runs one: empty folder, `npx skills add Deware-PK/SynthetixLib` (or copy
  `skills/synthetixlib` into `.claude/skills/`), new Claude Code session with Sonnet, paste the prompt, no other help;
  then a second session runs `evals/REVIEW.md` (reviewer prompt: read the result, score the checklist, list doc gaps).
- `evals/RESULTS.md`: table per lib version (eval, builds, score, gaps, fixed by).
- Version 0.7.0-SNAPSHOT; ROADMAP Phase 3 row; HANDOFF.

## Spec — Part B (owner + agent)
- Owner runs the 3 evals (RUNNING.md) and pastes each review into RESULTS.md.
- Agent turns every gap into a doc fix (reference / rule / troubleshooting) or, if the API is at fault, writes it in
  Result as a proposed task (no API change in this task). Re-run the failed evals once.

## Acceptance criteria
- [ ] Part A files exist
- [ ] 3 evals run by the owner, every gap fixed or proposed, re-run green (≥ 9/10 checklist each)
- [ ] after ship: owner opens https://jitpack.io/#Deware-PK/SynthetixLib/v0.7.0 → build log green, modules listed
      (if the groupId differs from ADR 0015, update ADR + setup.md + template)
- [ ] `gradlew.bat check smokeTest publishToMavenLocal` green

## Verify
gradlew.bat check smokeTest publishToMavenLocal

## Result
(agent fills in)
