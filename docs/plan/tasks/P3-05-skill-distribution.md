# P3-05 llms.txt, marketplace, CHANGELOG, skills CLI check, wrap-up rule
Status: done · Approved: yes · Model: sonnet · Effort: low
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
- Carry-over from P3-04 review: the snippet tool accepts SQL markers `-- @snippet:<name>` / `-- @end` (fence
  `sql snippet=<name>`) + a unit test; replace the prose description of the recipe migration in recipes.md with the snippet.
- README.md at repo root (short, for humans): what it is, install skill, JitPack coordinates, link to SKILL.md.

## Acceptance criteria
- [x] llms.txt, marketplace, CHANGELOG, README exist and links resolve (relative paths checked by a small script or by hand)
- [x] skills CLI check documented in Result
- [x] `gradlew.bat check` green

## Verify
gradlew.bat check

## Result
Done. `gradlew.bat check` green (includes `checkSnippets` and the new `SnippetsTest.sqlMarkersAndFence`).
- `llms.txt` (generated from the files: SKILL, CHANGELOG, template, 9 references, 37 rules, README, AGENTS; absolute GitHub `main` URLs).
  There is no separate rules index file; every rule is listed in llms.txt with its title.
- `.claude-plugin/marketplace.json` (plugin `synthetixlib`, `source: "./"`, `skills: ["./skills/synthetixlib"]`) + `.claude-plugin/plugin.json`.
  Docs used: https://code.claude.com/docs/en/plugin-marketplaces. `claude plugin validate .` -> "Validation passed with warnings" (only a missing icon).
  Install: `/plugin marketplace add Deware-PK/SynthetixLib`, `/plugin install synthetixlib@synthetixlib`.
- Skills CLI: `npx --yes skills add . --list` (local path) -> "Local path validated", "Found 1 skill": `synthetixlib` with the full description from SKILL.md.
- `skills/synthetixlib/CHANGELOG.md` 0.1.0..0.7.0; no API breaks found (marked "Breaking: none"); two behaviour changes in 0.2.0 listed.
  0.1.0 and 0.2.0 were never tagged (tags start at v0.3.0).
- SQL snippets: `Snippets.kt` accepts `-- @snippet:` / `-- @end`, fence `sql snippet=`, `.sql` sources; test added;
  `V1__purchases.sql` marked `recipe-migration`, recipes.md now shows it.
- AGENTS.md (rule 6) and tasks/README.md: wrap-up must update reference + rules + CHANGELOG.
- README.md at root. Link check by script over llms.txt, README, CHANGELOG: 0 missing.
- Not done: ship (that is P3-06); README JitPack coordinates use `v0.7.0`, which exists only after P3-06 ships.

## Review (planner, 2026-10-25)
Approved. `claude plugin validate .` ok (icon warning only), `npx skills add . --list` finds 1 skill, links checked,
SQL snippets + `recipe-migration`. Planner fixed CHANGELOG: JitPack only from v0.7.0 (old tags have no jitpack.yml), and
noted the 0.6.0 gui link-parse change.
