# P2-36 Release 0.8.0: pom license, eval re-run, ship
Status: todo · Approved: yes · Model: sonnet · Effort: low · Ship: yes (dialog 0.8.0)
Module: synthetixlib-dialog, synthetixlib-command · Depends on: P2-35 · Design: docs-skill.md §5

## Prompt (paste into Claude Code)
Read AGENTS.md and docs/plan/tasks/P2-36-release-0.8.0.md, then do Part A only and stop (Part B needs the owner).
After the owner's eval results are in, do Part C: run `gradlew.bat syncSnippets check smokeTest publishToMavenLocal`,
set Status: review, then ship: `powershell -ExecutionPolicy Bypass -File scripts/ship-module.ps1 -Module dialog -Version 0.8.0`.

## Part A (agent)
- build-logic `synthetix.library`: pom metadata (name, description, url, MIT license, developer Deware-PK, scm) for
  every published module. Version 0.8.0-SNAPSHOT (root + template `synthetixLocalVersion`), SKILL.md version line,
  CHANGELOG 0.8.0 complete. `publishToMavenLocal`, check a generated .pom has the license.
## Part B (owner)
- In game (`runServer`): `/example dialog sell` — type `1,000`, `abc`, a price above the max; error re-show keeps the text.
- Re-run evals **auction-lite** and **shop** (RUNNING.md "Re-run", folders `<eval>-2`, Sonnet medium,
  `synthetixLocalVersion=0.8.0-SNAPSHOT`) and **cooldown-command** (`-2`). The planner reviews them.
- Expected: price is a number-field with `inputRange`/`validate` (no hand-made re-show), cooldown via the library with
  `cooldowns().reset`. Results in RESULTS.md under "0.8.0".
## Part C (agent)
- Fix any new doc gaps from the reviews, then ship (prompt above). After the push the owner opens JitPack for v0.8.0.

## Acceptance criteria
- [ ] pom license, versions, CHANGELOG
- [ ] 3 evals re-run ≥ 9/10 and use the new APIs (or gaps fixed)
- [ ] `gradlew.bat syncSnippets check smokeTest publishToMavenLocal` green; JitPack v0.8.0 green (owner)

## Verify
gradlew.bat syncSnippets check smokeTest publishToMavenLocal

## Result
(agent fills in)
