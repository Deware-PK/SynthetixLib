# P2-17 Cooldowns, executesAsync, runOnSender
Status: done · Approved: yes · Model: sonnet · Effort: high
Module: synthetixlib-command · Depends on: P2-15a (P2-16 may run in parallel) · Design: command.md §3, §5

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
- [x] cooldown semantics exactly as command.md §5; bug #4 covered
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
Ran `gradlew.bat check smokeTest`: BUILD SUCCESSFUL. 29 new unit tests (`CommandCooldownTest` 17, `CommandAsyncTest`
12); the module has 75. Smoke 60/60 on Paper 1.21.11 and 26.3 incl. `command.async` (handler off the global thread,
`runOnSender` back on it) and `command.cooldown-console`. ArchUnit rules untouched. Nothing committed.

**How it works**
- `Cooldowns` (in the `CommandService` instance, via `CommandHost.cooldowns()`): one flat `ConcurrentHashMap<(player,
  node path), expiry nanos>`. Every operation is one atomic map operation, so threads of different players never share
  a key and a quit racing a start can only lose or keep that single entry. The key is the node path (`auction bid`),
  not the alias. Expiry compares `expiry - now`, so `nanoTime` wrap and a 292-year cooldown cannot misbehave (capped).
- Check in `Executor` before the handler (after permission / playerOnly): active → `CommandMessages.cooldown` with
  `<time>` (`Durations.format`, rounded up to whole seconds, e.g. `1m31s`) and `<label>` = alias typed, returns 0.
  Console / command blocks / holders of the bypass permission never get an entry (decided once per run).
- Start: `CommandCallImpl.finished()` after a normal return. `fail` and exceptions skip it. `skipCooldown()` and
  `resetCooldown()` set a flag under a per-call lock that `finished()` takes too, so a reset inside the handler cannot be
  undone by the start after it; a reset after it (e.g. from `runOnSender`) removes the entry that started.
- Inheritance as for permission: `cooldown` of a parent applies to children unless overridden; `Duration.ZERO`
  removes an inherited one. `cooldown(Duration, bypassPermission)` validates (negative / blank → IAE).
- Cleanup (bug #4), all three: `PlayerQuitEvent` listener registered by the service (`PluginManager#registerEvent`
  with a lambda `EventExecutor`, unregistered in `close()`, no reflection); **clean on access** (`left()` drops an
  expired entry); **periodic sweep** every 5 minutes on `lib.scheduler().async()` (catches a player who quit while an
  async handler was still running). `close()` clears everything.
- `executesAsync`: `Executor` reads every typed argument on the command thread first (`CommandCallImpl.readAll()`, values
  cached per run, so selectors/positions are never resolved off-thread; a bad value stops there with its message), then
  `host.runAsync` → `lib.scheduler().async().run`. Returns 1 as soon as it is queued. `isAsync()` is true inside.
  Failure / exception handling is the same code as sync (`Executor.execute`).
- `runOnSender`: `SenderThreads` — `entity(player).run(task)` for a player (a retired player drops the task silently),
  `global().run(task)` otherwise; nothing once closed, an `IllegalStateException` from a scheduler that closed
  meanwhile is swallowed. Unit-tested with a Proxy `Scheduler`.
- `CommandHost` gained `cooldowns()` and `runAsync`; `Node` gained `path`, `cooldown`, `async`.

**Concurrency notes / known limit**
- The same player cannot overlap two *sync* runs of a node (one region thread), so check-then-start needs no lock there.
  Two runs of one `executesAsync` node by the same player before the first handler returns both pass the check
  (the cooldown only starts on return, as specified). If a product needs to forbid that it should guard in the handler;
  say so if you want the module to reserve the node while an async handler is in flight.
- Verified with 8 threads x 60 players x 3 rounds: exactly one run per player, all others blocked, one entry each.
- The quit listener and the 5-minute sweep are exercised by being registered at startup (smoke tests); a real quit
  needs a client, so the unit test calls `Cooldowns.clear(uuid)`, which is all the listener does.
