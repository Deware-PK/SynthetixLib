# selftest

`synthetixlib-selftest` (since 0.9.0). In-server checks for **your** plugin: `./gradlew smokeTest` boots Paper 1.21.11 and
26.x headless, runs your `SelfTest`s once the server loaded, writes a JSON result and stops the server. The build fails
unless both servers say PASS. Depends on core. The template already has it wired (`MyPlugin`, `build.gradle.kts`).

## When to use / when not

| What you test | How |
|---|---|
| Pure logic: money maths, parsing, a state machine | plain JUnit |
| Plain SQL / your store class | JUnit with `Database.standalone` (see `database.md`) |
| `ItemStack`s, items in inventories, entities, worlds, displays, menus, a command run by the console, a Paper registry | **self-test** (needs a real server) |
| Something only a human can judge (looks, feel) | `./gradlew runServer` and play |

A self-test is slower (a server boot) but runs the real code on the real API, so it also catches a Paper change on 26.x.

## Wire it (3 steps)

1. Dependency: `selftest` in the module list of `build.gradle.kts` (shaded and relocated like the others).
2. In `onEnable`, after `SynthetixLib.init(this)`:

```java snippet=template-selftest
SelfTests.of(lib)
        .add(SelfTest.sync("template.lang-loaded", () -> {
            Checks.equal(Locale.US, lang.defaultLocale(), "default locale");
            final Component welcome = lang.get(Locale.US, Messages::welcome, Placeholder.unparsed("name", "Test"));
            Checks.isTrue(!Component.empty().equals(welcome), "welcome text is not empty");
        }))
        .runIfRequested();
```

3. The three Gradle tasks come with the template (`runServer`, `runServer26`, `smokeTest`):

```kotlin snippet=template-dev-servers
val paperMin = "1.21.11"
val paperLatest = "26.3"   // the newest 26.x Paper build you test on

runPaper {
    // The shaded jar is wired explicitly below, exactly once per server.
    disablePluginJarDetection()
}

// Extra plugins your product needs on the test servers (downloaded once into run-paper's cache, used by runServer*
// and smokeTest). Uncomment what you need; look up the exact version string on the plugin's page.
val extraPlugins = runPaper.downloadPluginsSpec {
    // github("MilkBowl", "Vault", "1.7.3", "Vault.jar")
    // modrinth("essentialsx", "2.21.2")
    // modrinth("packetevents", "2.11.0")
}

fun RunServer.devServer(mcVersion: String, javaVersion: Int, dirName: String, port: Int) {
    minecraftVersion(mcVersion)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(javaVersion)) })
    val dir = layout.projectDirectory.dir("run/$dirName")
    runDirectory.set(dir)
    pluginJars(tasks.shadowJar.flatMap { it.archiveFile })
    downloadPlugins.from(extraPlugins)
    systemProperty("com.mojang.eula.agree", "true")
    jvmArgs("-Xms1G", "-Xmx2G")
    val propsFile = dir.file("server.properties").asFile
    doFirst {
        // Only seeds a fresh server; edit run/<dir>/server.properties freely afterwards.
        if (!propsFile.exists()) {
            propsFile.parentFile.mkdirs()
            propsFile.writeText(
                """
                server-port=$port
                online-mode=false
                level-type=minecraft\:flat
                generate-structures=false
                spawn-protection=0
                view-distance=4
                simulation-distance=4
                motd=${project.name} dev $mcVersion
                """.trimIndent() + "\n"
            )
        }
    }
}

tasks.runServer {
    description = "Runs Paper $paperMin (Java 21) with this plugin."
    devServer(paperMin, 21, paperMin, 25565)
}

tasks.register<RunServer>("runServer26") {
    group = "run paper"
    description = "Runs Paper $paperLatest (Java 25) with this plugin."
    devServer(paperLatest, 25, paperLatest, 25566)
}

fun registerSmoke(name: String, mcVersion: String, javaVersion: Int, port: Int) =
    tasks.register<RunServer>(name) {
        group = "verification"
        description = "Boots Paper $mcVersion headless, runs the self-tests, fails unless all pass."
        devServer(mcVersion, javaVersion, "smoke-$mcVersion", port)
        val result = layout.projectDirectory.file("run/smoke-$mcVersion/selftest-result.json").asFile
        systemProperty("synthetix.selftest", "true")
        systemProperty("synthetix.selftest.output", result.absolutePath)
        timeout.set(Duration.ofMinutes(6))
        isIgnoreExitValue = true
        outputs.upToDateWhen { false }
        doFirst { result.delete() }
        doLast {
            if (!result.exists()) {
                throw GradleException("[$mcVersion] no self-test result: the plugin failed to enable or the server crashed. See the log above.")
            }
            val json = result.readText()
            if (!json.startsWith("{\"status\":\"PASS\"")) {
                throw GradleException("[$mcVersion] self-test FAILED:\n$json")
            }
            logger.lifecycle("[$mcVersion] self-test PASS")
        }
    }

val smokeMin = registerSmoke("smokeTestMin", paperMin, 21, 25575)
val smokeLatest = registerSmoke("smokeTestLatest", paperLatest, 25, 25576)
smokeLatest.configure { mustRunAfter(smokeMin) }

tasks.register("smokeTest") {
    group = "verification"
    description = "Self-tests on Paper $paperMin and $paperLatest."
    dependsOn(smokeMin, smokeLatest)
}
```

`runIfRequested()` does **nothing** unless the JVM has `-Dsynthetix.selftest=true`: no listener, no file, no shutdown. The
code is a few KB inside your jar and inert on a live server.

**Never set the property on a live server.** When the run ends it stops the server.

## Writing tests

```java
SelfTest.sync("items.round-trip", () -> Checks.equal(3, roundTrip(stack).getAmount(), "amount"));
SelfTest.async("orders.saved", () -> orders.save(order));                  // passes when the future completes
SelfTest.async("orders.slow", Duration.ofSeconds(30), () -> orders.bigJob());
SelfTest.withPlayer("gui.opens", player -> menu.open(player));            // SKIP when nobody is online
SelfTest.sync("vault.hooked", () -> {
    if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
        SelfTest.skip("Vault is not installed");
    }
    ...
});
```

- Ids: `<area>.<feature>`. Bodies run on the **global region thread**, one test at a time, in the order added. Never
  `join()` / `get()`: complete futures through `lib.scheduler()`.
- `Checks.equal(expected, actual, what)`, `Checks.isTrue(cond, what)`, `Checks.throwsType(Type.class, () -> ...)` throw an
  `AssertionError` with a readable message. `SelfTest.require(cond, message)` also exists.
- A test that needs a player uses `withPlayer`; the kit does not simulate clients. With nobody online it is **SKIP**
  (counted separately, never a failure), so `smokeTest` stays green and a human can run it by joining `runServer`.
- Set-up that needs worlds or other plugins: `SelfTests#before(Runnable)` (once, first) and `addLater(Supplier<Collection>)`
  (the tests are built after the server loaded, not in `onEnable`). A throwing set-up is the failed test `selftest.before`.
- Per-test timeout: 10 s for `async` (give a `Duration` to change it). The whole run is capped at 120 s
  (`-Dsynthetix.selftest.cap=<seconds>`); the stuck test and the rest are reported as failed.
- Test commands that must be registered before the server starts (Paper's `COMMANDS` event fires right after `onEnable`)
  are registered in `onEnable`; the library's own wiring shows it:

```java snippet=selftest-wiring
final CommandSelfTests commandTests = CommandSelfTests.install(plugin, lib);
SelfTests.of(lib)
        .addLater(() -> {
            final List<SelfTest> tests = new ArrayList<>();
            tests.addAll(HarnessSelfTests.all(plugin));
            tests.addAll(CoreSelfTests.all(plugin, lib));
            tests.addAll(CoreSchedulerSelfTests.all(plugin, lib));
            tests.addAll(CorePdcSelfTests.all(plugin, lib));
            tests.addAll(CoreItemSelfTests.all(plugin, lib));
            tests.addAll(ConfigSelfTests.all(plugin));
            tests.addAll(ConfigBukkitSelfTests.all(plugin, lib));
            tests.addAll(ConfigFileSelfTests.all(plugin, lib));
            tests.addAll(ConfigCollectionSelfTests.all(plugin, lib));
            tests.addAll(ConfigLocaleSelfTests.all(plugin, lib));
            tests.addAll(commandTests.all());
            tests.addAll(GuiSelfTests.all(plugin, lib));
            tests.addAll(DatabaseSelfTests.all(plugin, lib));
            tests.addAll(DialogSelfTests.all(plugin, lib));
            tests.addAll(RecipeSelfTests.all(plugin, lib));
            tests.addAll(UpgradeSelfTests.all(plugin, lib));
            // one line per module as modules are added
            return tests;
        })
        .runIfRequested();
```

## Extra plugins on the test servers

Vault, an economy, PacketEvents: declare them in the `extraPlugins` block of `build.gradle.kts`
(`github(owner, repo, tag, asset)`, `modrinth(id, version)`, `hangar(plugin, version)`, `url(...)`). They are downloaded
once and loaded by `runServer`, `runServer26` and `smokeTest` alike. A test for a hook says `SelfTest.skip(...)` when the
plugin is missing.

## Result file

`run/smoke-<version>/selftest-result.json` (or the path in `-Dsynthetix.selftest.output`; without it, `selftest-result.json`
in the plugin's data folder). `status` is the first key; the build matches it at the start of the file.

```json
{"status":"PASS","minecraftVersion":"1.21.11","total":3,"failed":0,"skipped":1,"tests":[
  {"name":"orders.saved","passed":true,"durationMs":12},
  {"name":"items.round-trip","passed":true,"durationMs":3},
  {"name":"gui.opens","passed":true,"skipped":true,"durationMs":0,"reason":"needs an online player"}]}
```

A failed test has `"passed":false` and `"error":"AssertionError: amount: expected <3> but was <2>"`; the stack trace is in
the server log (`[selftest] FAIL <name>`). `total` counts skipped tests; `failed` and `skipped` are counted separately.

## Pitfalls

- Java 25 must be installed for `runServer26` / the 26.x smoke test (Gradle finds installed JDKs).
- The latest-26.x version is a literal in the template (`paperLatest`); raise it when a newer 26.x Paper build exists.
- Do not touch static state from a test; use fields of your plugin or objects built inside the test.
- A self-test that needs the console sender or dispatches a command must run on the global thread (the default).
