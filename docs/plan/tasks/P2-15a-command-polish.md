# P2-15a Command polish (review findings)
Status: done · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-command, synthetixlib-config · Depends on: P2-15 · Design: command.md §3, §5

## Prompt (paste into Claude Code)
Read AGENTS.md and docs/plan/tasks/P2-15a-command-polish.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Findings to fix (from planner review of P2-15)
1. **Permission gap through alias redirects.** P2-15 found that Paper's alias redirect skips the target's `requires`,
   and `Executor` re-checks only the node's own effective permission. A sub with its own permission
   (`admin` = `auction.admin`) therefore never checks the root's (`auction.use`) when run via `/ah admin ...`.
   Fix: `Node` carries every distinct permission on its path (root -> node); `Executor` requires all of them.
   Tests: root perm + child own perm, run through the alias redirect and directly; sender with only the child perm
   is refused (returns 0, handler not run, no message - same as a hidden node).
2. **`label()` is wrong under redirects** (`/execute ... run ah bid 1` gives `execute`; bug #7 says: the alias the
   player typed). Resolve it from the Brigadier context instead of the first input token:
   - if the context's first parsed node is the spec's root literal -> that literal's text from the input range;
   - else (the root was reached through a redirect: alias, `namespace:name`, `/execute run`) -> the input token
     ending right before the context's range start;
   - strip `namespace:` in both cases. `input()` stays the full input.
   Tests (dispatcher with Paper-like alias + namespaced redirects and an `execute ... run` redirect to the root):
   `/auction bid 1` -> `auction`, `/ah bid 1` -> `ah`, `/plugin:ah bid 1` -> `ah`, `/execute as @s run ah bid 1`
   -> `ah`, `/execute run auction bid 1` -> `auction`.
3. **`CommandCall.locale()` falls back to `Locale.US`.** Add `LocaleHandle#defaultLocale()` (public getter, config
   module, Javadoc) and let `CommandService.messages(lang, ...)` remember it; non-players get that locale, without a
   lang handle `Locale.US` as today. Test both.

## Accepted as is (no change)
All other P2-15 deviations: `spec()` called at the parent's `build()`, `executesAsync` stub, `get(arg)` of an optional
returns the declared default, `Arg.suggests(Function<CommandSourceStack, Collection<String>>)`, the extra build-time
checks, root-name-taken warning, kebab-case lang keys.

## Acceptance criteria
- [x] 1-3 fixed with the tests named above
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
- `Node` has `permissions` (distinct, root -> node, built in `SpecBuilder.toNode`); `Executor` requires all of them.
- `Inputs.label(CommandContext)`: a context rooted at the dispatcher root parsed the name itself (its first node's text);
  otherwise it was reached through a redirect and the label is the token before the context's range. `namespace:`
  stripped. (Uses `context.getRootNode() instanceof RootCommandNode` instead of comparing with the spec's root name,
  so a sub named like the root cannot be mistaken for it.)
- `LocaleHandle#defaultLocale()` added; `CommandService.messages` remembers it, `CommandHost.defaultLocale()` feeds
  `CommandCall.locale()` for non-players (`Locale.US` without a lang handle).
- Tests: `CommandExecutionTest` (permission through alias redirect, label x5 inputs, locale of console),
  `LocaleHandleTest` (getter). `CommandService.messages` remembering the locale is not unit-tested (needs a lib).
- Ran `gradlew.bat check` and `gradlew.bat smokeTest`: green (1.21.11 and 26.x).
