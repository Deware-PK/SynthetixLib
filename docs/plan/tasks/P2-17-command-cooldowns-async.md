# P2-17 Cooldowns, executesAsync, runOnSender
Status: todo · Approved: yes · Model: sonnet · Effort: high
Module: synthetixlib-command · Depends on: P2-15 (P2-16 may run in parallel) · Design: command.md §3, §5

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/command.md and docs/plan/tasks/P2-17-command-cooldowns-async.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.
Concurrency matters (Folia: the same player can not run two commands at once, but different players can, on
different threads): think it through before coding.

## Spec
- `cooldown(Duration)` / `cooldown(Duration, String bypassPermission)` per player per **executable node**, stored in
  the `CommandService` instance (no statics), inherited by children unless overridden. Checked before the handler;
  active → `CommandMessages.cooldown` with `<time>` (config duration format, rounded up to seconds) and return 0.
  Started only when the handler returns normally (`fail` / exception → not started). Console / command blocks: none.
- `CommandCall.cooldownLeft()`, `resetCooldown()`, `skipCooldown()` real now.
- Entries removed on `PlayerQuitEvent` (listener registered by the service, unregistered on close) and expired entries
  never accumulate (clean on access or periodically via `lib.scheduler().async()`; say which in Result). SnapPlug bug #4.
- `executesAsync`: arguments parsed on the command thread, handler on `lib.scheduler().async()`; `isAsync()` true;
  cooldown starts when it returns normally; exceptions handled like sync ones (logged + `error`).
- `runOnSender(task)`: player → `lib.scheduler().entity(player)` (retired: dropped silently), otherwise global.

## Tests
Unit with a fake clock and fake scheduler: cooldown blocks second call, not started on fail/exception, bypass
permission, per node (bid vs sell), reset/skip, quit clears, expiry cleanup, 8 threads × N players no lost/extra
cooldowns; async handler runs off-thread, cooldown after return, runOnSender targets. Self-test
`command.async` (console: async handler then runOnSender back to global).

## Acceptance criteria
- [ ] cooldown semantics exactly as command.md §5; bug #4 covered
- [ ] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
