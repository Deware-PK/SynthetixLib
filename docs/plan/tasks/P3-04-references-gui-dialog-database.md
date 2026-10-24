# P3-04 References + rules: gui, dialog, database + recipes + troubleshooting
Status: todo · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-skill (docs, not a Gradle module) · Depends on: P3-03 · Design: docs-skill.md §3, §1 (rules/)

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/docs-skill.md and docs/plan/tasks/P3-04-references-gui-dialog-database.md, then implement the task.
Run `gradlew.bat syncSnippets check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- Write `references/` gui.md, dialog.md, database.md, recipes.md, troubleshooting.md with the shape of docs-skill.md §3. Source of truth: `docs/design/<module>.md`
  **including its "Deviations (as built)" section** and the module AGENTS.md; check every API name against the code
  (no invented methods). Mark APIs `since 0.x` (version the module shipped in).
- Every code/YAML example is a snippet (P3-01): add markers to the example plugin or template; add small example code
  to `synthetixlib-example` where none exists (it must compile and, if it touches the server, be covered by an existing
  or new self-test).
- `rules/` files for these areas (prefixes gui-, dialog-, db-), vercel-react-best-practices style: frontmatter `title`,
  `impact` (CRITICAL | HIGH | MEDIUM), `tags`; one paragraph why; ❌ Incorrect / ✅ Correct code (incorrect code is
  plain markdown, not a snippet, since it must not compile into the example). Cover every AGENTS.md locked decision and
  never-again bug that belongs to these modules. Add the list to SKILL.md ("Rules by impact").
- `recipes.md`: 3 end-to-end recipes (shop menu + buy dialog + SQLite; per-player cooldown command with locale;
  reloadable config + menu + dialog) built from snippets of a new `demo/recipes` package in the example.
- `troubleshooting.md`: every exception / warning message the library itself logs or throws (grep the sources) that a
  consumer can cause → cause → fix. Table, sorted by module.

## Acceptance criteria
- [ ] every public type of the modules appears in a reference (list any left out on purpose in Result)
- [ ] no API name in the references that does not exist in the code
- [ ] `gradlew.bat syncSnippets check smokeTest` green

## Verify
gradlew.bat syncSnippets check smokeTest

## Result
(agent fills in)
