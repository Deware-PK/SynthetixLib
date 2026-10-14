# P2-02 Scheduler facade
Status: todo · Approved: yes · Model: sonnet · Effort: high
Module: synthetixlib-core · Depends on: P2-01 · Design: docs/design/core.md §1–2

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/core.md (§1–2) and docs/plan/tasks/P2-02-scheduler.md, then implement the task.
Concurrency correctness matters more than brevity: think through cancel/close races before writing code.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Goal
Folia-safe scheduler facade owned by the `SynthetixLib` instance, exactly as in design §2.

## Spec
- Package `com.github.dewarepk.synthetixlib.core.scheduler`. Public: `Scheduler`, `TickScheduler`,
  `EntityTickScheduler` (extends TickScheduler, adds `retired` overloads), `AsyncScheduler`,
  `SynthetixTask`. Implementations package-private.
- Wrap only Paper's `GlobalRegionScheduler`, `RegionScheduler`, `Entity#getScheduler()`,
  `AsyncScheduler` (`io.papermc.paper.threadedregions.scheduler`). Never `BukkitScheduler`.
- Delay rules from design: 0 allowed (map to immediate run or 1 tick as the Paper method requires),
  negative → IAE, period < 1 → IAE. Async uses `Duration` (ms precision, `TimeUnit.MILLISECONDS`).
- Entity on removed entity (Paper returns null): return a cancelled `SynthetixTask`, run `retired` if given.
- Tracking: instance keeps a concurrent set of live tasks; one-shot tasks remove themselves on
  completion; repeating ones on cancel. `close()` cancels all and makes further scheduling throw ISE.
  Race to consider: task scheduled concurrently with `close()` must end up cancelled, never leaked.
- Exceptions in task bodies: catch, log SEVERE via plugin logger with task kind + where it was scheduled
  (capture caller `StackTraceElement` cheaply only when a debug flag is on; otherwise kind only); timers continue.
- `SynthetixLib` gains `scheduler()`; `close()` delegates.
- `isGlobalThread()` → `Bukkit.isGlobalTickThread()`; `owns(...)` → `Bukkit.isOwnedByCurrentRegion(...)`.
- ArchUnit: extend `schedulerOnlyThroughFacade` so only `..core.scheduler..` may depend on
  `org.bukkit.scheduler..` **or** `io.papermc.paper.threadedregions..`.
- Javadoc on every public type/method with a short usage snippet (no `@Override` lines inside snippets).

## Tests
- Unit (core `src/test`): argument validation, delay mapping helper, tracking set add/remove/close
  logic extracted into a pure class testable without a server.
- Self-tests (`CoreSchedulerSelfTests`, async via P2-01): `core.scheduler.global.run`,
  `.global.later` (≥ N ticks elapsed), `.global.timer-cancel` (runs 3× then cancels, no 4th),
  `.region.run` (runs on owning thread: `owns(loc)` true inside), `.entity.run` and `.entity.retired`
  (spawn an armor stand/marker, remove it, schedule → retired runs), `.async.run` (not global thread),
  `.close-cancels` (use a **separate** `SynthetixLib.init(plugin)` instance, schedule a delayed task, close,
  assert it never runs), `.exception-isolated` (timer throws once, keeps running).

## Acceptance criteria
- [ ] public API matches design §2 (names, signatures); any deviation listed in Result with reason
- [ ] no `BukkitScheduler` usage anywhere; ArchUnit rule extended and green
- [ ] close() race handled (explain approach in Result)
- [ ] all listed unit + self-tests pass on 1.21.11 and 26.3
- [ ] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
