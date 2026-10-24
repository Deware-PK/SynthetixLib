# P3-01 Skill skeleton + snippet sync/check
Status: done · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-skill (docs, not a Gradle module) · Depends on: P2-33 · Design: docs-skill.md §1, §4

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/docs-skill.md and docs/plan/tasks/P3-01-snippet-sync.md, then implement the task.
Run `gradlew.bat check`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- Create the tree of docs-skill.md §1 under `skills/synthetixlib/` with placeholder files (one heading + "TODO P3-0x").
- Snippet markers: `// @snippet:<name>` … `// @end` in Java (example plugin, later the template) and
  `# @snippet:<name>` … `# @end` in YAML resources. Names `[a-z0-9-]+`, unique across the repo. Markers are
  comments only: no effect on code; the shown block is the lines between, de-indented to the shallowest line.
- In markdown under `skills/`: a fenced block whose info string is `java snippet=<name>` or `yaml snippet=<name>`.
- Root Gradle tasks (in build-logic or the root build, plain Kotlin, no new third-party plugin):
  `syncSnippets` rewrites every tagged block from its source; `checkSnippets` fails listing file:line for a block that
  differs, an unknown name, a duplicate name, or an unclosed marker. `check` depends on `checkSnippets`.
  Sources scanned: `synthetixlib-example/src/**`, `skills/synthetixlib/templates/**`.
- Add 2 real markers in the example (`hello-init` around `SynthetixLib.init` + `close`, `demo-menu-yml` in
  `menus/demo.yml`) and use them in `references/setup.md` so the tasks are exercised.
- Document the marker syntax in AGENTS.md (one short section "Docs snippets").

## Tests
Unit tests for the extractor (de-indent, nested markers refused, unclosed, duplicate, unknown) in build-logic or a
small test source set. `checkSnippets` red when a block is edited by hand, green after `syncSnippets`.

## Acceptance criteria
- [x] tree exists, `syncSnippets` / `checkSnippets` work, `check` depends on it
- [x] `gradlew.bat check` green

## Verify
gradlew.bat syncSnippets check

## Result
Extractor + tasks: `build-logic/src/main/kotlin/Snippets.kt`, `synthetix.snippets.gradle.kts` (applied in root). Tests: `build-logic/src/test/kotlin/SnippetsTest.kt` (10 cases; `check` depends on `:build-logic:test`). Markers: `hello-init`, `hello-close` (init and close are in different methods, so two snippets), `demo-menu-yml`; used in `references/setup.md`. Placeholder tree under `skills/synthetixlib/`, `llms.txt`, `.claude-plugin/marketplace.json` (`{}`). AGENTS.md "Docs snippets" added. Ran: `syncSnippets`, `check` green; hand-edited block made `checkSnippets` fail, `syncSnippets` fixed it. Not committed.

## Review (planner, 2026-10-25)
Approved. `check` depends on `checkSnippets` + `:build-logic:test`; red/green verified by hand. Extra `hello-close` marker ok.
recipes.md / troubleshooting.md: the design tree was misleading (planner) → moved to `references/`, tree fixed.
`.claude-plugin/marketplace.json` is `{}` (invalid until P3-05 fills it; fine while unreleased).
Carry-over to P3-02: `new-plugin` strips `@snippet` / `@end` marker lines from the copied project.
