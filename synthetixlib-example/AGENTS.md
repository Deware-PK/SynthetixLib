# synthetixlib-example

NOT published. Two jobs:
1. **Smoke-test bed.** `./gradlew smokeTest` boots Paper 1.21.11 + 26.x with `-Dsynthetix.selftest=true`;
   `ExampleSelfTests` hands every `SelfTest` to the `synthetixlib-selftest` kit (`SelfTests`), which runs them after
   `ServerLoadEvent`, writes `run/smoke-<ver>/selftest-result.json` and stops the server. The Gradle task fails unless
   the file starts with `{"status":"PASS"`. So the library's own self-tests prove the kit.
2. **Real consumer + docs examples.** Code here is what users copy, so it must use only public API and
   read like documentation.

Adding tests for a new module: create `<Module>SelfTests` (see `CoreSelfTests`), add one line in
`ExampleSelfTests` (inside the `addLater` supplier), add `implementation(project(":synthetixlib-<module>"))` in build.gradle.kts.
Test ids: `<module>.<feature>`.

Two kinds of test (`SelfTest`, from `synthetixlib-selftest`; also `SelfTest.withPlayer` = SKIP without a player, `Checks`):
- `SelfTest.sync(name, check)` — body runs on the global region thread; throw to fail.
- `SelfTest.async(name, [timeout,] () -> CompletableFuture<?>)` — passes when the future completes normally
  within the timeout (default 10 s); fails on exceptional completion, timeout, or a throw while creating it.
  Complete futures via schedulers; never `join()`/`get()` (the server thread must not block).

The runner executes tests sequentially in registration order, has a 120 s hard cap for the whole run (`-Dsynthetix.selftest.cap`),
records `durationMs` per test and shuts the server down from the global region thread.
`harness.async` (in `HarnessSelfTests`) permanently covers the async path.

Package is `com.github.dewarepk.synthetixexample` on purpose: a consumer package must not start with
`com.github.dewarepk.synthetixlib`, otherwise relocation would also rewrite it.

Modules whose registration is tied to server start (commands) install in `ExampleSelfTests.install` (called from `onEnable`), because
Paper fires `LifecycleEvents.COMMANDS` right after `onEnable`, before `ServerLoadEvent`; see `CommandSelfTests`.
