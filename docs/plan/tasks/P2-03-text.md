# P2-03 Text service (MiniMessage)
Status: done · Approved: yes · Model: sonnet · Effort: low
Module: synthetixlib-core · Depends on: P0-01 · Design: docs/design/core.md §3

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/core.md (§3) and docs/plan/tasks/P2-03-text.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- Package `com.github.dewarepk.synthetixlib.core.text`. Public `TextService` (final class or interface +
  package-private impl). Immutable, thread-safe.
- Methods: `parse(String, TagResolver...)`, `parseItem(String, TagResolver...)` (italic false unless the
  markup sets it), `send(Audience, String, TagResolver...)`, `with(TagResolver...)` → new instance,
  `plain(Component)`, `escape(String)`.
- Use Adventure's `MiniMessage`, `TagResolver`, `Placeholder`, `PlainTextComponentSerializer` (all in paper-api).
  No legacy serializer.
- `SynthetixLib` gains `text()` (created in init).

## Tests
- Unit: placeholders (unparsed/parsed/component), `with()` does not mutate original, `escape` prevents
  tag injection (`"<red>x"` stays literal), `parseItem` italic default false but `<italic>` still works,
  `plain` output.
- Self-test `core.text.send` (send to console sender, no exception).

## Acceptance criteria
- [x] API matches design §3
- [x] unit tests above pass; self-test passes on both versions
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
- Added `core.text.TextService` (final, immutable; `create()`, `parse`, `parseItem`, `send`, `with`, `plain`, `escape`)
  and `SynthetixLib.text()` (created in init). `create()` is public because `SynthetixLib` lives in another package.
- Tests: `TextServiceTest` (placeholders, with() immutability, escape, italic default, plain);
  self-test `core.text.send` in `CoreSelfTests`.
- Ran `gradlew.bat check smokeTest`: BUILD SUCCESSFUL, self-tests PASS on 1.21.11 and 26.3.
