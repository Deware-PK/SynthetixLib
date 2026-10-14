# P2-14 Config wrap-up: example demo, docs, publish
Status: todo · Approved: yes · Model: sonnet · Effort: low
Module: synthetixlib-config, synthetixlib-example · Depends on: P2-10..P2-13

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/config.md and docs/plan/tasks/P2-14-config-wrapup.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- Example plugin demo (outside self-test mode): `settings.yml` (Settings record with a nested default airdrop),
  `airdrops/` collection with a sealed `Reward` (item / command), `lang/en_US.yml` + bundled `lang/th_TH.yml`.
  No commands yet (command module comes later): on enable log loaded airdrop ids; create `example` airdrop if none.
- Javadoc with snippet on every public type; package-info for every package.
- config.md Status → "Implemented (0.2.0)" + deviations; ROADMAP row 2 → done; module AGENTS.md final.
- Version → `0.2.0-SNAPSHOT`; `publishToMavenLocal`.

## Acceptance criteria
- [ ] demo files generated on `runServer` look clean and commented (paste settings.yml + one airdrop file in Result)
- [ ] `gradlew.bat check smokeTest publishToMavenLocal` green

## Verify
gradlew.bat check smokeTest publishToMavenLocal

## Result
