# P3-06 Eval suite, fixes, ship 0.7.0 (first JitPack release)
Status: review · Approved: yes · Model: sonnet · Effort: high · Ship: yes (skill 0.7.0)
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
- Evals run **before** the ship, so v0.7.0 is not on JitPack and the skill is not on GitHub yet: RUNNING.md uses the
  local checkout — `gradlew.bat publishToMavenLocal` first, copy `skills/synthetixlib` into `<eval folder>/.claude/skills/`,
  and tell the eval agent (one line appended to the prompt) to build with `-PsynthetixLocal=true` and the current
  `synthetixLocalVersion`. After the ship, later evals use `npx skills add Deware-PK/SynthetixLib` + JitPack.
- Root `version=0.7.0-SNAPSHOT` and template `synthetixLocalVersion=0.7.0-SNAPSHOT` together (P3-02 review note).
- `evals/RUNNING.md`: how the owner runs one: empty folder outside the repo (e.g. `D:\Minecraft\Evals\<eval>`), new Claude Code session with Sonnet, paste the prompt, no other help;
  then a second session runs `evals/REVIEW.md` (reviewer prompt: read the result, score the checklist, list doc gaps).
- `evals/RESULTS.md`: table per lib version (eval, builds, score, gaps, fixed by).
- ROADMAP Phase 3 row; HANDOFF.

## Spec — Part B (owner + agent)
- Owner runs the 3 evals (RUNNING.md) and pastes each review into RESULTS.md.
- Agent turns every gap into a doc fix (reference / rule / troubleshooting) or, if the API is at fault, writes it in
  Result as a proposed task (no API change in this task). Re-run the failed evals once.

## Acceptance criteria
- [x] Part A files exist
- [x] 3 evals run by the owner, every gap fixed or proposed, re-run green (≥ 9/10 checklist each)
- [ ] after ship: owner opens https://jitpack.io/#Deware-PK/SynthetixLib/v0.7.0 → build log green, modules listed
      (if the groupId differs from ADR 0015, update ADR + setup.md + template)
- [ ] `gradlew.bat check smokeTest publishToMavenLocal` green

## Verify
gradlew.bat check smokeTest publishToMavenLocal

## Result
(Part B done: doc gaps fixed in dialog.md, command.md, config.md; API gaps proposed as P2-34 / P2-35. JitPack box is left for the owner after the push.)

### Result — Part A
Files made in `skills/synthetixlib/evals/`: `auction-lite.md`, `shop.md`, `cooldown-command.md` (each = plain-language prompt, no API names, + the 10-item checklist with a how-to-verify column and unscored task-specific bullets), `RUNNING.md` (local-checkout flow before the ship, `npx skills add` flow after, re-run rule), `REVIEW.md` (reviewer prompt: score, evidence, doc gaps, API problems), `RESULTS.md` (table for 0.7.0, empty rows). ROADMAP Phase 3 and HANDOFF updated.
Version: root `gradle.properties` `version=0.7.0-SNAPSHOT` and template `synthetixLocalVersion=0.7.0-SNAPSHOT` (together). Nothing else referenced 0.6.0-SNAPSHOT.
Ran: `gradlew.bat syncSnippets check publishToMavenLocal` green (0.7.0-SNAPSHOT installed in mavenLocal). Not committed, ship script not run.
Choices: `evals/` is deleted from the skill copy in the eval folder so the builder cannot read the checklists; the reviewer reads the checklist from the repo path. Cooldown eval does not allow N/A on check 10.

How the owner starts eval 1 (auction-lite), details in `skills/synthetixlib/evals/RUNNING.md`:
1. Already installed in mavenLocal (`publishToMavenLocal` above).
2. `$eval='D:\Minecraft\Evals\auction-lite'; New-Item -ItemType Directory -Force "$eval\.claude\skills"; Copy-Item -Recurse -Force 'D:\Minecraft\Plugins\SynthetixLib\skills\synthetixlib' "$eval\.claude\skills\synthetixlib"; Remove-Item -Recurse -Force "$eval\.claude\skills\synthetixlib\evals"`
3. `cd $eval; claude --model sonnet` (new session), paste the `## Prompt` block of `auction-lite.md` plus the line: `Build with gradlew build -PsynthetixLocal=true -PsynthetixLocalVersion=0.7.0-SNAPSHOT (SynthetixLib is in mavenLocal, not on JitPack yet).`
4. When it finishes, open a second session in the same folder and paste the prompt from `REVIEW.md` (`<eval>` = `auction-lite`); put the output in `RESULTS.md`.
