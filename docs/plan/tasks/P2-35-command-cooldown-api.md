# P2-35 Command cooldowns: reloadable duration, per-player reset, readable time
Status: todo · Approved: yes (owner 2026-10-25) · Model: sonnet · Effort: medium
Module: synthetixlib-command · Depends on: P2-34 · Design: command.md §5 · Found by: eval cooldown-command run 1

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/command.md and docs/plan/tasks/P2-35-command-cooldown-api.md, then implement the task.
Run `gradlew.bat syncSnippets check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Why
The eval agent wrote its own cooldown map: `.cooldown(Duration)` is fixed at registration (a config reload cannot change
it) and an admin cannot clear one player's wait. (Not an API gap: `throw ctx.fail(...)` already leaves the cooldown
unused — the agent did not know; document it prominently.)

## Spec
1. `.cooldown(Function<CommandSender, Duration>)` and `.cooldown(Function<CommandSender, Duration>, bypassPermission)`:
   read at each successful run, so `settings.get().wait()` follows a reload. `Duration.ZERO` = no cooldown this time.
   The `Duration` overloads stay (they delegate).
2. `CommandService#cooldowns()` → public `CommandCooldowns`: `remaining(Player|UUID, String commandPath)`,
   `reset(UUID, String commandPath)`, `reset(UUID)` (every command), thread-safe; `commandPath` = the command path as
   written in code (`"kit"`, `"kit reset"`), documented with the alias rule. Existing `ctx.resetCooldown()` unchanged.
3. Readable time: core or config gets `TimeFormat` (not static: an instance from the locale) → `"1m 12s"` with the
   unit words from the consumer's lang (`units: {d: 'd', h: 'h', m: 'm', s: 's'}` style record) and a default English
   set; `CommandMessages.cooldown`'s `<time>` uses it. Decide core vs config by where locale lives; say which in Result.
4. Docs: command.md (design) §5 + deviations; skill `references/command.md` replaces the "own cooldown map" workaround with
   these APIs (snippets) and states in bold that `ctx.fail(...)` never starts the cooldown; rule
   `command-cooldown-not-on-failure.md` (MEDIUM). CHANGELOG 0.8.0.
5. Example: the demo command with a cooldown reads it from settings and has an admin `reset <player>`.

## Tests
Unit: duration function read per run (changes after "reload"), ZERO, bypass, reset one/all, remaining, fail keeps no
cooldown, TimeFormat table (0s, 59s, 1m 12s, 1h 0m, 2d 3h). Self-test `command.cooldown-reset` on both versions.

## Acceptance criteria
- [ ] API + docs (design, skill reference, rule, CHANGELOG)
- [ ] `gradlew.bat syncSnippets check smokeTest` green

## Verify
gradlew.bat syncSnippets check smokeTest

## Result
(agent fills in)
