# P2-01 Async-capable self-test runner
Status: done · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-example · Depends on: P0-01 · Design: docs/design/core.md §7

## Prompt (paste into Claude Code)
Read AGENTS.md and docs/plan/tasks/P2-01-selftest-async.md, then implement the task exactly.
Run `gradlew.bat check smokeTest`, tick the acceptance boxes, fill in "Result", set Status: review. Do not commit.

## Goal
Scheduler tests need to wait for ticks. Let self-tests be asynchronous without blocking the server
thread, while keeping the existing synchronous tests working.

## Spec
- `SelfTest` keeps `name` and gains an async body. Shape:
  - `SelfTest.sync(String name, Check check)` — current behaviour (Check throws to fail).
  - `SelfTest.async(String name, Duration timeout, Supplier<CompletableFuture<?>> body)` — passes when
    the future completes normally within `timeout`, fails on exceptional completion or timeout.
  - Default timeout for async when not given: 10 s. Overall run hard cap: 60 s.
- Runner (`SelfTestTrigger`): runs tests **sequentially** in registration order; the next test starts
  after the previous finished. Must never block the server thread (no `join()`/`get()` on it).
  Timeouts: use `CompletableFuture.orTimeout`.
- A test body that throws synchronously while creating its future counts as failure.
- After the last test: write the same JSON as today (add `"durationMs"` per test), log the summary,
  then `getServer().shutdown()` **from the global region thread** (use `Bukkit.getGlobalRegionScheduler()`
  directly — the example is not library code, ArchUnit does not apply).
- Update existing `CoreSelfTests` to `SelfTest.sync(...)`.
- Add one permanent sanity test `harness.async` that completes a future after 2 ticks via
  `Bukkit.getGlobalRegionScheduler().runDelayed(...)` — keeps the async path covered.
- Update `synthetixlib-example/AGENTS.md` to describe sync vs async tests.

## Acceptance criteria
- [x] `SelfTest.sync` / `SelfTest.async` exist; old record constructor removed or private
- [x] runner sequential, non-blocking, per-test timeout + 60 s global cap
- [x] JSON has `durationMs` per test; `status` logic unchanged
- [x] `harness.async` passes on 1.21.11 and 26.3
- [x] a deliberately failing async test (temporarily added, then removed) makes `smokeTest` fail — mention in Result
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
Implemented `SelfTest` (final class, private ctor, `sync`/`async`), sequential non-blocking runner in
`SelfTestTrigger` (inner `Run`, hops back to the global region thread after each completion, 60 s cap via
delayed global task, `durationMs` in JSON), `HarnessSelfTests` (`harness.async`), `CoreSelfTests` → `SelfTest.sync`,
example `AGENTS.md` updated.
Verified: `gradlew.bat check smokeTest` green (1.21.11 and 26.3, 4/4 tests). A temporary
`harness.TEMPFAIL` async test (never-completing future, 1 s timeout) made `smokeTest` fail (exit 255) and the
following tests still ran; it was removed afterwards. The 60 s cap path itself was not exercised.
