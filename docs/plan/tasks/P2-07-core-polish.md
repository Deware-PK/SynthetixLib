# P2-07 Core polish (review findings)
Status: done · Approved: yes · Model: sonnet · Effort: low
Module: synthetixlib-core, repo root · Depends on: P2-06

## Prompt (paste into Claude Code)
Read AGENTS.md and docs/plan/tasks/P2-07-core-polish.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Findings to fix (from planner review of P2-02..P2-06)
1. **Line endings are mixed.** These files are CRLF while the rest is LF (.editorconfig says LF):
   docs/design/core.md, docs/plan/ROADMAP.md, docs/plan/tasks/P2-06-core-wrapup.md,
   synthetixlib-core/AGENTS.md, synthetixlib-example/.../SynthetixExamplePlugin.java.
   Add `.gitattributes` with `* text=auto eol=lf` (+ `*.bat text eol=crlf`, `*.jar binary`),
   convert those files to LF, run `git add --renormalize .` (staging only, no commit) and check
   `git diff --cached --stat` shows only line-ending changes for them.
2. **Internal factories are public.** `Scheduler.create`, `PdcKeys.create`, `Items.create`,
   `TextService.create` exist only so `SynthetixLib` can wire services. Mark each with
   `@org.jetbrains.annotations.ApiStatus.Internal` and Javadoc "Internal: use SynthetixLib#…()".
   (A consumer creating its own `Scheduler` would bypass `lib.close()` cancellation.)
3. **PdcKey on empty stacks.** Define behaviour for `ItemStack` that `isEmpty()` (AIR / amount 0):
   `get` → `Optional.empty()`, `has` → false, `set`/`remove` → `IllegalArgumentException("empty item")`.
   Add self-test `core.pdc.item.empty`.
4. **Deprecation warnings.** Run `gradlew.bat :synthetixlib-core:compileJava --rerun` and list every
   `-Xlint` deprecation/removal warning in Result. If `ItemMeta#setHideTooltip` (or anything else) is
   deprecated in 1.21.11, replace it with the non-deprecated API **available in 1.21.11**
   (e.g. tooltip display data component) or, if only experimental API exists, keep it and note why.
5. **Manual demo check** (owner does this, agent just reminds in Result): `gradlew.bat runServer`,
   join `localhost`, expect the "Demo ticket" item + welcome message after 1 s.

## Acceptance criteria
- [x] .gitattributes added; no CRLF left in tracked text files except *.bat
- [x] 4 factories annotated `@ApiStatus.Internal`
- [x] empty-stack behaviour implemented + self-test passes on 1.21.11 and 26.3
- [x] compile warnings listed; deprecated calls resolved or justified
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
1. `.gitattributes` added; the 5 listed files converted to LF; `git add --renormalize .` run (staged, not committed).
   The staged stat also shows content changes because those files already had uncommitted edits from P2-02..P2-06.
2. `@ApiStatus.Internal` + "Internal: use SynthetixLib#…()" Javadoc on the 4 `create` factories.
3. `PdcKey` empty-stack: get → empty, has → false, set/remove → IAE("empty item"); self-test `core.pdc.item.empty` PASS on 1.21.11 and 26.3 (34/34 each).
4. `compileJava --rerun-tasks` with `-Xlint:deprecation,removal,unchecked`: **0 warnings**, so `setHideTooltip` is not deprecated in 1.21.11; kept unchanged.
5. Owner: run `gradlew.bat runServer`, join localhost, expect the "Demo ticket" item + welcome message after 1 s.
`gradlew.bat check smokeTest` green.
