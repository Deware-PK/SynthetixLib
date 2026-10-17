# P2-13 Locale
Status: done · Approved: yes · Model: sonnet · Effort: medium
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
- [x] API matches config.md §5
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
Ran `gradlew.bat check smokeTest`: green (Paper 1.21.11 and 26.3, incl. `config.locale.send`).
- New public API in `config`: `Configs#locale` -> `LocaleBuilder` (`defaultLocale`, `bundled`, `perPlayer`, `text`, `version`,
  `migrate`, `load`) -> `LocaleHandle` (`send`, `sendLines`, `get(Audience|Locale)`, `getLines(Audience|Locale)`, `reload`).
- **Deviation from the spec text (needs your OK or a rename):** the `Function<T,List<String>>` variants are called
  `sendLines` / `getLines`, not overloads of `send` / `get`: `Function<T,String>` and `Function<T,List<String>>` have the
  same erasure, so Java does not allow the overload.
- Language of an `Audience`: `Player#locale()` for players, otherwise the Adventure `Identity.LOCALE` pointer (same value on
  Paper, and testable without a server); none -> default. Order: exact tag -> file of the language -> any file of the same
  language (the default locale's file first) -> default file; tags compared ignoring case. `perPlayer` defaults to `false`.
- Files: default locale file is created from the defaults; bundled files come from the jar path `<dir>/<tag>.yml` (via
  `ConfigHost.resource`, `plugin.getResource`) if missing; all files get missing keys filled like a config. Other
  `<tag>.yml` files in the folder (name like `de_DE`) are loaded too; other yml names are ignored. `reload()` re-creates a
  deleted default/bundled file, a broken file keeps its old messages, a plain reload writes nothing.
- Empty string = disabled (`send` no-op, `get` returns `Component.empty()`); an empty list sends nothing.
- Self-test resource: `synthetixlib-example/src/main/resources/selftest-lang/th_TH.yml`.
- Tests: `LocaleHandleTest` (21: fallback chain, case, perPlayer off, disabled, lists, placeholders, text service, bundled
  copy + fill + never overwrite + missing in jar, reload edits/new/removed/broken, no rewrite).

