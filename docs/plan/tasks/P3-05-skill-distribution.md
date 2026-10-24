# P3-05 llms.txt, marketplace, CHANGELOG, skills CLI check, wrap-up rule
Status: todo · Approved: yes · Model: sonnet · Effort: low
Module: synthetixlib-skill (docs, not a Gradle module) · Depends on: P3-04 · Design: docs-skill.md §6

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/docs-skill.md and docs/plan/tasks/P3-05-skill-distribution.md, then implement the task.
Run `gradlew.bat check`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- `llms.txt` at repo root (llmstxt.org format): title, one-line summary, links to SKILL.md, every reference and
  rules index (relative GitHub URLs on `main`).
- `.claude-plugin/marketplace.json` + plugin manifest so `/plugin marketplace add Deware-PK/SynthetixLib` offers
  `synthetixlib` (skill path `skills/synthetixlib`). Follow the current Claude Code plugin docs; note the doc URL used.
- Check the repo layout works with the open skills CLI (`npx skills add Deware-PK/SynthetixLib --list` or the CLI's
  documented dry run, against a local path if the CLI allows): skill found, name/description read. Note the command
  and output in Result; if npx is not available, explain what was checked by reading the CLI docs.
- `skills/synthetixlib/CHANGELOG.md`: 0.1.0..0.7.0, one block per version from the git tags / ROADMAP, API breaks
  marked **Breaking**.
- AGENTS.md: module wrap-up (`Ship: yes`) tasks must update the module reference + rules + CHANGELOG, and
  `checkSnippets` stays green (docs-skill.md §5 last point). Add the same line to docs/plan/tasks/README.md.
- README.md at repo root (short, for humans): what it is, install skill, JitPack coordinates, link to SKILL.md.

## Acceptance criteria
- [ ] llms.txt, marketplace, CHANGELOG, README exist and links resolve (relative paths checked by a small script or by hand)
- [ ] skills CLI check documented in Result
- [ ] `gradlew.bat check` green

## Verify
gradlew.bat check

## Result
(agent fills in)
