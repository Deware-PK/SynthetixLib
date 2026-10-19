# P2-14a Locale: comments for bundled files
Status: done · Approved: yes · Model: sonnet · Effort: low
Module: synthetixlib-config · Depends on: P2-14 · Design: config.md §5

## Prompt (paste into Claude Code)
Read AGENTS.md and docs/plan/tasks/P2-14a-locale-bundled-comments.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Finding (planner review of P2-14 demo output)
`lang/th_TH.yml` copied from the jar has no comments (`# Placeholders: <name>` etc.), because comments are only
written for keys the library adds. Translators then do not see which placeholders a message takes.

## Spec
- When a bundled file is **copied from the jar** (first time only), add the `@Comment` lines of the record to every
  key that has no comment in the copied text, in the same write. Keys that already have a comment keep theirs.
- Never touch the comments of a file that already existed on disk (user files stay as the user wrote them).
- One write at most (copy + comments + missing keys together), atomic as before.

## Tests
Copied bundled file gets the component comments; a key with its own comment in the jar file keeps it; an existing
file on disk is not changed by a plain reload (bytes + mtime).

## Acceptance criteria
- [x] demo `lang/th_TH.yml` on a fresh `runServer` shows the comments (paste it in Result)
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
`FileConfig.computeBundled(text)` fills record comments on uncommented keys + missing keys in one string;
`LocaleHandle.copyBundled` writes that once (existing files untouched). Tests added in `LocaleHandleTest`.
`gradlew.bat check smokeTest` green (52/52 on 1.21.11 and 26.3). Smoke-server copy (`selftest-lang/th_TH.yml`, fresh):
```
config-version: 1
# Placeholders: <name>
hello: <yellow>Sawasdee <name>
lines:
- <gray>line one
- <gray>line two
```
(The demo `lang/th_TH.yml` under `runServer` was not regenerated; the smoke copy shows the same path.)
