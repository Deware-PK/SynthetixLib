# P2-16 Command argument types (Args)
Status: todo · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-command · Depends on: P2-15 · Design: command.md §4

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/command.md and docs/plan/tasks/P2-16-command-arg-types.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- Every row of the table in command.md §4 not done in P2-15: `player()`, `players()` (selectors resolved against the
  source; none → `CommandMessages.playerNotFound` with `<input>`), `offlinePlayer()` (cached only, online-name
  suggestions, never blocking), `world()`, `blockPosition()` / `position()` → `Location` (relative coords resolved),
  `itemStack()`, `registry(RegistryKey)`, `material()`, `enumOf(Class)` (same matching as config enums, suggestions in
  lower kebab case), `duration()` (reuse the config Duration parser: expose an internal helper in config rather than
  copying), `uuid()`, `key()`, `oneOf(Supplier<Collection<String>>)` (checked again at execution), `custom(native,
  parse, suggest)` via Paper `CustomArgumentType.Converted`.
- Converter errors → `CommandSyntaxException` with a locale message (`CommandMessages.invalidValue`, `<input>`,
  `<reason>`) through `MessageComponentSerializer`; vanilla errors untouched.
- `Arg.suggests(fn)`: filtered by the typed prefix, case-insensitive; runs on Paper's suggestion thread (document).
- No deprecated API (list warnings in Result).

## Tests
Unit: converters (duration, enumOf, oneOf, offlinePlayer cache miss, custom), suggestions filtering, error message
text. Self-tests `command.args.registry`, `.material`, `.world`, `.position` (console dispatch with absolute
coordinates), `.player-not-found`.

## Acceptance criteria
- [ ] all types of §4 available and tested
- [ ] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
