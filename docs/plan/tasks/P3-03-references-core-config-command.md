# P3-03 References + rules: core, config, command
Status: todo · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-skill (docs, not a Gradle module) · Depends on: P3-02 · Design: docs-skill.md §3, §1 (rules/)

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/docs-skill.md and docs/plan/tasks/P3-03-references-core-config-command.md, then implement the task.
Run `gradlew.bat syncSnippets check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- Write `references/` core.md, config.md, command.md with the shape of docs-skill.md §3. Source of truth: `docs/design/<module>.md`
  **including its "Deviations (as built)" section** and the module AGENTS.md; check every API name against the code
  (no invented methods). Mark APIs `since 0.x` (version the module shipped in).
- Every code/YAML example is a snippet (P3-01): add markers to the example plugin or template; add small example code
  to `synthetixlib-example` where none exists (it must compile and, if it touches the server, be covered by an existing
  or new self-test).
- `rules/` files for these areas (prefixes core-, config-, command-, state-, threads-, text-), vercel-react-best-practices style: frontmatter `title`,
  `impact` (CRITICAL | HIGH | MEDIUM), `tags`; one paragraph why; ❌ Incorrect / ✅ Correct code (incorrect code is
  plain markdown, not a snippet, since it must not compile into the example). Cover every AGENTS.md locked decision and
  never-again bug that belongs to these modules. Add the list to SKILL.md ("Rules by impact").


## Acceptance criteria
- [ ] every public type of the modules appears in a reference (list any left out on purpose in Result)
- [ ] no API name in the references that does not exist in the code
- [ ] `gradlew.bat syncSnippets check smokeTest` green

## Verify
gradlew.bat syncSnippets check smokeTest

## Result
(agent fills in)
