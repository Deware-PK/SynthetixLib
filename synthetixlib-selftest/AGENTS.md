# synthetixlib-selftest

Package: `com.github.dewarepk.synthetixlib.selftest` (only this module may use it). Task: docs/plan/tasks/P4-04-selftest-kit.md.
Depends on core. Products ship it inside their jar (shaded + relocated like every module); it is **inert** unless the JVM has
`-Dsynthetix.selftest=true`, which only the Gradle `smokeTest` / a developer sets.

Types
- `SelfTest` (public): `sync`, `async` (default 10 s), `withPlayer` (SKIP when nobody is online), `skip(reason)`, `require`;
  nested `Check`, `PlayerCheck`, `Skipped`. `body()` is package-private (the run calls it).
- `Checks` (public): `equal`, `isTrue`, `throwsType` -> `AssertionError` with a readable message. No JUnit at runtime.
- `SelfTests` (public builder): `of(lib)`, `add`, `addAll`, `addLater(Supplier)` (built after the server loaded: set-up that needs
  worlds / other plugins), `before(Runnable)`, `runIfRequested()`. Property names are public constants.
- Package-private: `SelfTestRun` (sequential run on the global thread, per-test timeout, global cap, result, shutdown),
  `SelfTestResult` (record + the JSON writer), `SelfTestHost` (what a run needs from the server) + `ServerHost` (real one: lib
  scheduler, plugin logger, `ServerLoadEvent` listener that fires once).

Rules specific to selftest
- Nothing static; everything hangs off `lib`. Scheduling only through `lib.scheduler().global()`.
- Without the property `runIfRequested()` registers no listener, writes nothing, stops nothing (`SelfTestsTest`).
- Result JSON starts with `{"status":"PASS|FAIL"`: builds match with `startsWith`. Per-test `status` keys would break that, so
  tests carry `passed` (+ `skipped`, `reason`, `error`). SKIP never makes the run fail.
- Unit tests use a fake `SelfTestHost` with a manual global queue; the real host is proved by the example's `smokeTest`.
