# P2-13 Locale
Status: todo · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-config · Depends on: P2-11a · Design: config.md §5

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/config.md and docs/plan/tasks/P2-13-locale.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- `configs.locale(String dir, Class<T>, Supplier<T> defaults)` → `.defaultLocale(Locale)`, `.bundled(String... tags)`,
  `.perPlayer(boolean)`, `.version/.migrate` → `.load()` → `LocaleHandle<T>`.
- Files `<dir>/<tag>.yml` (tag like `th_TH`); default locale file generated from defaults; bundled files copied
  from the plugin jar resources `<dir>/<tag>.yml` if missing, then missing keys filled (same rules as P2-11).
- `send(Audience, Function<T,String>, TagResolver...)`, `send` overload for `Function<T,List<String>>` (one line each),
  `get(Audience, ...)` → Component (list → lines joined with newline), `get(Locale, ...)`. Empty string = disabled
  (send does nothing). Per-player: `Player#locale()` → exact tag, then language-only match, then default.
  Rendering via `lib.text()` (or a `TextService` passed with `.text(TextService)` so prefix tags work).
- `reload()` reloads all locale files.

## Tests
Unit: fallback chain (th_TH → th → default), empty disables, list messages, bundled copy + fill, placeholders.
Self-test `config.locale.send` to console.

## Acceptance criteria
- [ ] API matches config.md §5
- [ ] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
