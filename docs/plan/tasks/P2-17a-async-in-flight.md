# P2-17a Async in-flight guard (review findings)
Status: done · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-command · Depends on: P2-16, P2-17 · Design: command.md §3, §5

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/command.md and docs/plan/tasks/P2-17a-async-in-flight.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Findings to fix (from planner review of P2-16 / P2-17)
1. **Owner decision: one async run per player per node at a time.** Today the same player can start an
   `executesAsync` node twice before the first handler returns (the cooldown only starts on return), e.g. a double
   `/auction bid` hitting the database twice. Add an in-flight guard for **every** `executesAsync` node, with or
   without a cooldown:
   - Reserve `(player, node path)` atomically (`putIfAbsent` style, in the `CommandService` instance) in `Executor`
     after the cooldown check and before `readAll()`; if already reserved -> reply new `CommandMessages.busy`
     (default `"<red>Please wait, /<label> is still running."`, with a comment) and return 0.
   - Release in a `finally` around the async handler, on every path: normal return (cooldown then starts as today),
     `fail`, exception, `readAll()` failure, and when `runAsync` itself throws (see 2). Release happens before the
     cooldown start so `cooldownLeft()` never sees both.
   - Console / command blocks: no guard (no UUID). Sync nodes: unchanged (one region thread already serialises them).
   - `Cooldowns.clear(uuid)` on quit also drops reservations; the sweep never removes a reservation of a running handler.
   Tests: two concurrent runs of one async node by one player -> second gets `busy`, handler ran once; released after
   return / fail / exception / readAll failure; different players and different nodes do not block each other;
   8 threads x N players stress.
2. **`host.runAsync` can throw** (`IllegalStateException` when the lib closes between the check and the queueing);
   today it escapes `Executor.run` into Brigadier. Catch it: release the reservation, log at FINE, return 0, no message.
3. **Docs**: command.md §5 gets the in-flight rule and `busy`; §3 Rules: `executesAsync` returns success as soon as it
   is queued. `CommandMessages` Javadoc: `invalidValue` has no `<label>` and its `<reason>` is English (known limit).

## Accepted as is (no change)
P2-16: all deviations in its Result (`Durations` public + internal, converters as `CustomArgumentType.Converted`,
player args resolved in `ctx.get` with `playerNotFound` only for the vanilla not-found key, `finePosition(true)`,
`oneOf` returns the set's spelling, copied enum matching, `custom` without `Class<T>`).
P2-17: flat cooldown map keyed by node path, clean on access + 5 min sweep + quit listener, `Duration.ZERO` removes an
inherited cooldown, args read on the command thread before going async.

## Acceptance criteria
- [x] 1-3 done with the tests named above
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
Ran `gradlew.bat check smokeTest`: BUILD SUCCESSFUL. 12 new unit tests (`CommandInFlightTest`), module now 87; smoke 60/60
on Paper 1.21.11 and 26.3. ArchUnit untouched. Nothing committed.

1. **In-flight guard.** `Cooldowns` got a second map `(player, node path) -> token`: `reserve` is `putIfAbsent`,
   `release(player, node, token)` is `remove(key, token)`, `clear(uuid)` (quit) and `clearAll()` drop reservations, `sweep()`
   does not touch them. `Executor.runAsync` (every `executesAsync` node, cooldown or not): cooldown check -> reserve ->
   `readAll()` -> queue. Already reserved -> new `CommandMessages.busy` ("<red>Please wait, /<label> is still running.",
   comment "Placeholders: <label> <sender>"), return 0. Released in the `finally` around the handler, *before*
   `call.finished()` starts the cooldown, and on the `readAll()` failure and queueing-failure paths. Console / command
   blocks and sync nodes: unchanged. The token is my addition: without it a handler of a player who quit and came back
   would release the new run's reservation when it finally returns (unit-tested).
   Tests: second run while the first runs -> busy, handler once; alias in the message; busy (not cooldown) while running
   and cooldown right after; released after return / fail / exception / readAll failure; different players and nodes run
   together; console and sync not guarded; quit drops it and a stale release keeps the new one; sweep keeps it;
   8 threads x 30 players x 3 rounds -> exactly one run per player, two `busy` each, all released at the end.
2. **`runAsync` throwing.** It used to be swallowed in `SenderThreads` (and then the task silently never ran, which
   would have leaked a reservation). `SenderThreads.runAsync` / `CommandHost.runAsync` now throw `IllegalStateException`
   when the service or scheduler is closed; `Executor` catches only that around the queueing call, releases, logs at FINE
   and returns 0 with no message. `runOnSender` still swallows (a follow-up holds nothing). Test: `failAsync` host ->
   returns 0, nothing reserved, one FINE record, next run works. The `SenderThreads` closed tests now expect the ISE.
3. **Docs.** command.md §3 (`executesAsync` returns 1 once queued), §5 (in-flight rule + `busy`), §6 record with `busy`;
   `CommandMessages` Javadoc: `invalidValue` has no `<label>` and the module's own `<reason>` texts are English (known
   limit); module AGENTS.md. The `Known limit` line about overlapping async runs is gone from it.

Side effects: `CommandMessages` has a new component `busy` (after `cooldown`), so a product that builds the record by
hand must add it; `defaults()` and lang files fill it. The fake host in the tests logs at ALL so FINE records are seen.
