# P2-02 Scheduler facade
Status: done · Approved: yes · Model: sonnet · Effort: high
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
- [x] public API matches design §2 (names, signatures); any deviation listed in Result with reason
- [x] no `BukkitScheduler` usage anywhere; ArchUnit rule extended and green
- [x] close() race handled (explain approach in Result)
- [x] all listed unit + self-tests pass on 1.21.11 and 26.3
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
Ran `gradlew.bat check smokeTest`: BUILD SUCCESSFUL. Smoke: 1.21.11 PASS 16/16, 26.3 PASS 16/16
(12 new `core.scheduler.*` self-tests). Unit tests: `DelaysTest`, `TaskRegistryTest`, `TaskTrackerTest` (core, incl. a
6-thread schedule-vs-close stress test, 50 rounds). Nothing committed.

**Files**: `core.scheduler` = public `Scheduler`, `TickScheduler`, `EntityTickScheduler`, `AsyncScheduler`,
`SynthetixTask`; package-private `SchedulerImpl`, `TaskTracker`, `TaskRegistry`, `TaskHandle`, `Delays`,
`BaseTickScheduler`, `Global/Region/EntityTickScheduler(Impl)`, `AsyncSchedulerImpl`. `SynthetixLib` gained `scheduler()`;
`close()` delegates. ArchUnit: `schedulerOnlyThroughFacade` now also forbids `io.papermc.paper.threadedregions..` outside
the facade; new rule `neverLegacyBukkitScheduler` (no `Bukkit/Server#getScheduler`, `BukkitScheduler`, `BukkitRunnable` anywhere,
facade included). Example: `CoreSchedulerSelfTests`, registered in `SelfTestTrigger`.

**close() race**: `TaskRegistry.register` adds to a concurrent set, then re-checks the volatile `closed`; `close()` sets `closed`
first, then snapshots the set. So a task is either rejected (ISE, nothing scheduled) or in the snapshot and cancelled.
Second gap: close may land between registering and creating the Paper task. `TaskHandle` is a CAS state machine
(PENDING → COMPLETED/CANCELLED); `cancel()` writes state then reads `scheduled`, `attach()` writes `scheduled` then reads state,
so whichever runs second cancels the Paper task. A cancelled handle also skips its body in `execute`.

**Deviations from design §2 (all additive, please review):**
1. `Scheduler` has a public static `create(Plugin)` and extends `AutoCloseable` (`close()`, no checked exception): `SynthetixLib`
   lives in another package, so it needs a public way to build and close the scheduler (same pattern as `TextService.create()`).
   Users should still call `lib.close()`.
2. `Scheduler.entity(...)` returns `EntityTickScheduler` (design: "entity variant adds retired overloads"); `region(...)`/`global()` return `TickScheduler`.
3. `SynthetixTask.isDone()` = Future semantics: true after a one-shot ran **or** after any cancel.
4. Entity already removed at schedule time: `retired` runs immediately on the **calling thread** (Paper does not invoke it
   in that case). Retired is skipped when the caller/close() cancelled the task. Verified by `entity.retired` (runs exactly once).
5. Debug origin: system property `synthetix.scheduler.debug=true` (read once in `Scheduler.create`); then failures log
   "scheduled at <first frame outside core.scheduler>" via `StackWalker`. Off by default → kind only.
6. Delay 0 → 1 tick for global/region/entity (same moment as Paper's `run`); async `runLater(ZERO)` → `runNow`, async timer
   start 0 → 1 ms; sub-ms positive durations round up to 1 ms; period < 1 ms → IAE; huge durations capped.
7. Added `jspecify` `@Nullable` on `retired` params and internal nullable fields (comes with paper-api, compile only).
8. Region scheduler snapshots the chunk coords of the `Location` at call time (`blockX >> 4`).

Extra self-tests beyond the list: `region.later`, `async.later`, `async.timer`. `exception-isolated` intentionally logs one SEVERE
stack trace ("expected failure ...") in the server log; the result is still PASS.
