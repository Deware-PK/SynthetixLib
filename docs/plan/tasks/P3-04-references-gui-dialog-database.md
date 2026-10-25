# P3-04 References + rules: gui, dialog, database + recipes + troubleshooting
Status: done · Approved: yes · Model: sonnet · Effort: medium
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
- [x] every public type of the modules appears in a reference (list any left out on purpose in Result)
- [x] no API name in the references that does not exist in the code
- [x] `gradlew.bat syncSnippets check smokeTest` green

## Verify
gradlew.bat syncSnippets check smokeTest

## Result
Written: `references/gui.md`, `dialog.md`, `database.md`, `recipes.md`, `troubleshooting.md` (about 130 rows from the thrown/logged
messages of all six modules, by module); 15 new files in `rules/` (gui-, dialog-, db-; with the 22 of P3-03 that is 37) and the
list in SKILL.md "Rules by impact". Every never-again bug has a rule (#1 db-close-waits-for-writes, #2 gui-no-double-click-collect,
#3 gui-shift-click-input-only, #5 gui-one-view-per-viewer, #8 gui-getholder-false; #4/#6/#7 were P3-03).
Snippets: markers in the example (menu/dialog builders, ClaimStore, bootstrap, YAML files, paper-plugin.yml) plus more methods in
`docs/DocExamples.java`; new package `demo/recipes` (ShopRecipe, CooldownRecipe, ReloadRecipe, RecipeMessages, RecipeSettings,
menus/dialogs/db resources) covered by the new self-test `recipes.build` (RecipeSelfTests): files copied from the jar, no
file/registration mismatch, the SQL runs on SQLite, the command spec builds.
Left out of the references on purpose: `gui.file.*`, `dialog.file.*` (public only for the codecs) and the `@ApiStatus.Internal`
self-test hooks (`MenuType#preview/previewTitle/attach`, `DialogService#executor/click`, `DialogType#preview/attach/click`).
No SQL snippet: the snippet markers only accept `//` and `#` comments, so migration SQL is described, not copied.
Design doc drift: dialog.md of the design still says `dialogs.linkMenus(gui)`; the references use `link("menu", gui.opener())`.
Ran: `gradlew.bat syncSnippets check smokeTest` green (85/85 self-tests on 1.21.11 and 26.x). Not committed.

## Review (planner, 2026-10-25)
Approved. Smoke 85/85 (new `recipes.build`), 37 rules (all 8 never-again bugs covered), troubleshooting ~130 rows.
Design drift fixed: dialog.md §8 now says `link("menu", gui.opener())`. Small: recipes.md intro talks about
`RecipeMessages` but shows the template's wiring snippet; let the evals (P3-06) decide if it confuses an agent.
Carry-over to P3-05: snippet markers for SQL (`-- @snippet:<name>` / `-- @end`, fence `sql snippet=`), then make the
recipe migration a real snippet.
