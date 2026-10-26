# P4-04 Self-test kit for products: `synthetixlib-selftest` + dev/smoke servers in the template
Status: review · Approved: yes · Model: sonnet · Effort: high
Module: synthetixlib-selftest (new) · Depends on: P4-03 · Design: this task (owner approves by approving the task)
Requested by: InteractiveAuction 2.0 (items, players, displays can only be tested in a real server), lib gap L-5 part 2

## Prompt (paste into Claude Code)
Read AGENTS.md (incl. "Adding a library module"), docs/plan/tasks/P4-04-selftest-kit.md,
`synthetixlib-example/src/main/java/.../selftest/` (SelfTest, SelfTestTrigger) and
`build-logic/src/main/kotlin/synthetix.paper-plugin.gradle.kts` (runServer / smokeTest), then implement the task.
Run `gradlew.bat check smokeTest`, build the template against the snapshot, tick the boxes, fill in "Result", set
Status: review. Do not commit.

## Goal
Products get the same "boot Paper headless, run in-server checks, fail the build if one fails" loop that the library
uses, without copying the example's code: one small published module + three Gradle tasks in the template.

## Decisions for owner (defaults chosen)
1. Self-test code ships **inside the product jar, inert** unless the JVM is started with `-Dsynthetix.selftest=true`
   (a few KB; no listener, no command registered otherwise). Alternative: a second test-only plugin jar (more Gradle
   work, cannot reach package-private code). **Default: inside, inert.**
2. Extra plugins on the test servers (Vault, an economy, PacketEvents…) are downloaded by run-paper's
   `downloadPlugins` (Modrinth / Hangar / GitHub / URL) declared in the product's build file. **Default: yes.**

## Spec
### Module `synthetixlib-selftest` (published, depends on core)
- Package `com.github.dewarepk.synthetixlib.selftest`. Move `SelfTest` (sync / async with timeout) from the example
  here (public API, Javadoc + snippet); the example then uses the module (so the library's own 88+ self-tests prove it).
- `SelfTests.of(SynthetixLib lib)` → builder: `add(SelfTest)`, `addAll(Collection)`, `before(Runnable)` (setup),
  `runIfRequested()`. `runIfRequested()` does nothing unless `Boolean.getBoolean("synthetix.selftest")`; then on
  `ServerLoadEvent` (once) runs the tests one by one on the global thread with their timeouts and a global cap
  (`synthetix.selftest.cap`, default 120 s), writes the JSON to `synthetix.selftest.output` (same format as today:
  status PASS/FAIL, total, failed, one entry per test with name, ms, error), logs one line per failed test, then shuts the
  server down. Nothing static (lib rules); everything hangs off `lib`.
- Small helpers in `Checks`: `equal(expected, actual, what)`, `isTrue(cond, what)`, `throwsType(Class, Check)` → throw
  `AssertionError` with a readable message (no JUnit at runtime).
- `FakePlayer`-free: the kit does not simulate clients; tests that need a player say so in their name and are skipped
  with status SKIP (not FAIL) when no player is online — counted separately in the JSON.
- Add to `settings.gradle.kts`, archtest, example (per the AGENTS checklist). `synthetixlib-selftest` is relocated with the
  rest of the lib in products.

### Template (`skills/synthetixlib/templates/consumer-plugin`)
- `build.gradle.kts`: run-paper plugin (version from the lib catalog, written as a literal in the template), tasks
  `runServer` (1.21.11, Java 21, port 25565, `run/1.21.11`), `runServer26` (latest 26.x, Java 25, port 25566),
  `smokeTest` (both headless with `-Dsynthetix.selftest=true`, fails unless both JSON files say PASS), same server
  properties seed as build-logic (flat world, offline mode, view distance 4).
- A commented `downloadPlugins { }` block showing Vault + EssentialsX + PacketEvents (the IA case) so a product only
  uncomments lines; the same plugins are used by `runServer*` and `smokeTest`.
- The template plugin registers one self-test (`template.lang-loaded`) through `SelfTests.of(lib)...runIfRequested()`.
- `gradle.properties`: `synthetixLocalVersion=0.9.0-SNAPSHOT`.

### Docs (same task)
- New reference `skills/synthetixlib/references/selftest.md` (when to use unit tests vs self-tests, the API, the Gradle
  tasks, JSON format, SKIP for player-only tests, "never set the property on a live server"); module map line in
  SKILL.md; rule `test-in-server-for-bukkit-state.md` (MEDIUM): ItemStacks, entities, worlds, inventories → self-test,
  plain SQL → unit test with `Database.standalone` (P4-03).
- `docs/design/README.md`: list the module; ROADMAP products line unchanged. CHANGELOG "0.9.0".

## Tests
- The library's own `smokeTest` runs entirely through the moved `SelfTest` / `SelfTests` (same count, all PASS).
- Unit: `runIfRequested()` without the property registers nothing; JSON writer format; SKIP counting; global cap.
- Template: `gradlew build smokeTest -PsynthetixLocal=true` inside a fresh copy (`new-plugin` script) → PASS on both
  versions (paste the two JSON summaries in Result).

## Acceptance criteria
- [x] module published locally, relocated in the template jar (`verifyRelocation` green)
- [x] lib smoke count unchanged and green on 1.21.11 + 26.x
- [x] a fresh template project runs `smokeTest` green on both versions
- [x] without the property: no listener, no output, no shutdown (unit + manual start)
- [x] reference, rule, SKILL.md map, CHANGELOG; `checkSnippets` green

## Verify
gradlew.bat check smokeTest publishToMavenLocal · then in a fresh template copy: gradlew.bat build smokeTest -PsynthetixLocal=true

## Result
Done as specified; two small additions and one deviation (see below). Not committed.

**Ran**
- `gradlew.bat check smokeTest publishToMavenLocal` → BUILD SUCCESSFUL (1m41s). Smoke: 1.21.11 PASS, 26.3 PASS, **89 tests each**
  (63 sync + 26 async, same as before the move; 0 skipped).
  `{"status":"PASS","minecraftVersion":"1.21.11","total":89,"failed":0,"skipped":0,...}` and the same for `"26.3"`.
- `syncSnippets` + `checkSnippets` green (new snippets: `template-selftest`, `template-dev-servers`, `selftest-wiring`).
- Fresh template copy (`new-plugin.sh <dir> com.acme.demo Demo`), `gradlew.bat build smokeTest -PsynthetixLocal=true` →
  BUILD SUCCESSFUL (59s, `verifyRelocation` ran in `build`):
  - `{"status":"PASS","minecraftVersion":"1.21.11","total":1,"failed":0,"skipped":0,"tests":[{"name":"template.lang-loaded","passed":true,"durationMs":21}]}`
  - `{"status":"PASS","minecraftVersion":"26.3","total":1,"failed":0,"skipped":0,"tests":[{"name":"template.lang-loaded","passed":true,"durationMs":146}]}`
- Without the property: unit test `withoutThePropertyNothingIsRegistered` (no listener, no log, no file, no shutdown) and a manual
  start of the template jar on Paper 1.21.11 without `-Dsynthetix.selftest`: `[Demo] Enabling Demo v1.0.0`, "Done", no `[selftest]` line, no
  result file, server ran until `stop` was typed.

**What changed**
- New module `synthetixlib-selftest`: `SelfTest` (moved from the example, + `withPlayer`, `skip`, `Skipped`), `Checks`, `SelfTests`
  builder, package-private `SelfTestRun` / `SelfTestResult` (JSON) / `SelfTestHost` + `ServerHost`. Unit tests: `SelfTestsTest`
  (inert without the property, run order + shutdown, fail/skip counting + JSON escaping, failing set-up, global cap, `Checks`).
- Example: `SelfTestTrigger` removed, `ExampleSelfTests.install` builds the list; the library's own tests run through the kit.
- build-logic: `smokeTest` matches `{"status":"PASS"` at the start of the file (was `contains`).
- Template: run-paper, `runServer` / `runServer26` / `smokeTest`, `extraPlugins` block (commented Vault / EssentialsX / PacketEvents),
  `selftest` module, one self-test `template.lang-loaded`. Root and template version `0.9.0-SNAPSHOT`.
- Docs: `references/selftest.md`, rule `test-in-server-for-bukkit-state` (MEDIUM), SKILL.md map + rule list, CHANGELOG 0.9.0,
  `docs/design/README.md`, AGENTS.md (modules, layout), module and example AGENTS.md, template AGENTS.md / README.

**Deviations / additions (owner may veto)**
- Added `SelfTests#addLater(Supplier<Collection<SelfTest>>)`: the library's tests do real work while being built
  (`Configs.create`, `DialogService.create`) that must happen after the server loaded, not in `onEnable`. Also useful for products.
- Added `SelfTest.skip(reason)` + `SelfTest.Skipped` (needed for "plugin not installed" tests, same SKIP path as `withPlayer`).
- JSON per test keeps `passed` and gains `skipped` / `reason` (no per-test `status` key, it would fool a `contains("status":"PASS")` check).
  Global cap default is now 120 s (was 60 s in the example), property `synthetix.selftest.cap`.
- Not done by this task: `Written for SynthetixLib` line and `synthetixVersion` in the template (P4-05 ship script does them).
