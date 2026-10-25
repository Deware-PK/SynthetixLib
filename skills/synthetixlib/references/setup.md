# Setup

How a plugin gets SynthetixLib, builds one shaded jar, and starts and stops the library. The quickest start is the
template: `scripts/new-plugin.(ps1|sh)` copies `templates/consumer-plugin/` (a project that already builds) and renames
the package, the plugin name and the id.

```
pwsh scripts/new-plugin.ps1 -Target ../auction -Package com.acme.auction -Name Auction
bash scripts/new-plugin.sh ../auction com.acme.auction Auction
```

## Coordinates

| What | Value |
|---|---|
| Repository | `https://jitpack.io` |
| Group | `com.github.Deware-PK.SynthetixLib` |
| Artifacts | `synthetixlib-core`, `-config`, `-command`, `-gui`, `-dialog`, `-database` |
| Version | the git tag, **with the `v`**: `v0.7.0` |

Modules depend on each other (`dialog`, `gui`, `command`, `database` bring `config`, which brings `core`), but list
every module you call; it makes the build file honest. Add only the modules you use.

`-PsynthetixLocal=true` (template) switches to `mavenLocal()` and group `com.github.dewarepk` with the snapshot that
`./gradlew publishToMavenLocal` in a SynthetixLib checkout installed (`synthetixLocalVersion` in `gradle.properties`,
e.g. `0.7.0-SNAPSHOT`). JitPack and mavenLocal have different groups on purpose.

## Build file (shade + relocate)

Consumers shade the library and relocate it, so two plugins with different SynthetixLib versions never clash and
buyers get one jar. Compile against `paper-api:1.21.11-R0.1-SNAPSHOT` only (Paper 1.21.11 to 26.x), Java 21.

```kotlin snippet=template-repositories
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    if (synthetixLocal) {
        mavenLocal()
    } else {
        maven("https://jitpack.io")
    }
}
```

```kotlin snippet=template-dependencies
val synthetixGroup = if (synthetixLocal) "com.github.dewarepk" else "com.github.Deware-PK.SynthetixLib"
val synthetixVersion = providers.gradleProperty(if (synthetixLocal) "synthetixLocalVersion" else "synthetixVersion").get()

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // Shaded into your jar and relocated (see shadowJar below). Add only the modules you use.
    for (module in listOf("core", "config", "command", "gui", "dialog" /*, "database" */)) {
        implementation("$synthetixGroup:synthetixlib-$module:$synthetixVersion")
    }
    // Database module: add "database" to the list above, enable the line in MyPluginLoader and add
    // compileOnly("com.zaxxer:HikariCP:7.1.0")   // loaded at runtime by the PluginLoader, never shaded
}
```

```kotlin snippet=template-shadow
// Every consumer shades and relocates SynthetixLib (one jar for buyers). The target package must be yours and must
// not start with com.github.dewarepk.synthetixlib.
val libRootPackage = "com.github.dewarepk.synthetixlib"

tasks.shadowJar {
    archiveClassifier.set("")
    relocate(libRootPackage, "${project.group}.lib.synthetixlib")
}
tasks.jar {
    archiveClassifier.set("plain")
}
tasks.assemble {
    dependsOn(tasks.shadowJar)
}
```

The template also has `verifyRelocation` (part of `check`): it fails when an un-relocated `com/github/dewarepk/synthetixlib/`
class is still in the shaded jar. Ship the file without the `-plain` suffix.

Rules for the package: yours must not start with `com.github.dewarepk.synthetixlib` (the relocation would rewrite your
own classes), and the relocation target (`<your package>.lib.synthetixlib`) must be inside your package.

## paper-plugin.yml

```yaml snippet=template-plugin-yml-loader
# Loads runtime libraries that are not shaded (HikariCP for the database module). Keep it even when empty.
loader: com.example.myplugin.MyPluginLoader
# Only needed to register dialogs in the pause menu: bootstrapper: com.example.myplugin.MyPluginBootstrap
```

The whole file (`name`, `version: '${version}'` filled by `processResources`, `main`, `api-version: '1.21'`,
`folia-supported: true`) is `templates/consumer-plugin/src/main/resources/paper-plugin.yml`. Use `paper-plugin.yml`, not
`plugin.yml`: the loader and the bootstrapper exist only there.

## Init and close

Your plugin `extends JavaPlugin` (no base class). `SynthetixLib.init(this)` makes the instance everything hangs off;
there is no static or global state. Create each service (`Configs`, `CommandService`, `GuiService`, `DialogService`,
`Database`) from `lib`; `lib.close()` closes them all (the database waits for its pending
writes before the pool closes).

```java snippet=template-enable
lib = SynthetixLib.init(this);
final Configs configs = Configs.create(lib);
settings = configs.file("config.yml", Settings.class, Settings::defaults).load();
lang = configs.locale("lang", Messages.class, Messages::defaults).defaultLocale(Locale.US).load();

final DialogService dialogs = DialogService.create(lib, configs).messages(lang, Messages::dialog);
final GreetDialog greet = GreetDialog.create(dialogs, lang);
final GuiService gui = GuiService.create(lib, configs).messages(lang, Messages::gui)
        .link("dialog", dialogs.opener());
final MainMenu menu = MainMenu.create(gui);

CommandService.create(lib)
        .messages(lang, Messages::commands)
        .register(MyPluginCommand.build(settings, lang, menu, greet));

// Database module: Database.create(lib, <DatabaseSettings from config.yml>); closed together with lib.
getServer().getPluginManager().registerEvents(this, this);
```

```java snippet=template-disable
if (lib != null) {
    lib.close();
}
```

Order that works: config (`Configs`, then the files you load) before everything that reads it; dialogs before the gui
service when menus link to dialogs (`link("dialog", dialogs.opener())`); commands last. The commands are registered in
the Paper `COMMANDS` lifecycle event right after `onEnable`, so build the `CommandSpec` in `onEnable`, not later.

## PluginLoader (runtime libraries)

Third-party runtime libraries are never shaded. The only one SynthetixLib needs is HikariCP (database module); a
`PluginLoader` adds it from Maven Central at the first start.

```java snippet=template-loader
@Override
public void classloader(final PluginClasspathBuilder classpath) {
    // Database module: add HikariCP (needs the import com.github.dewarepk.synthetixlib.database.SynthetixLibraries)
    // SynthetixLibraries.database(classpath);
}
```

With the database module: uncomment that line, add `compileOnly("com.zaxxer:HikariCP:<version of the lib>")` to the build,
and keep `loader:` in `paper-plugin.yml`. Paper itself ships the SQLite and MySQL JDBC drivers.

## Bootstrapper (optional)

Needed only to put a dialog in the pause menu (quick actions) or in the dialog registry: `SynthetixDialogs.bootstrap(context)`
runs while the server starts, and the registry object is handed to the plugin through its constructor (`createPlugin`),
then to `DialogService.bootstrapped(registry)`. See `references/dialog.md`.

## Folia

`folia-supported: true` is safe as long as nothing touches `Bukkit.getScheduler()` or a world/entity from the wrong
thread: schedule through `lib.scheduler()` (global / region / entity / async). See `references/core.md`.

## Pitfalls

| Wrong | Right |
|---|---|
| `implementation(...)` without `relocate` | `shadowJar { relocate(...) }`; keep `verifyRelocation` in `check` |
| Version `0.7.0` on JitPack | the tag: `v0.7.0` |
| Group `com.github.dewarepk` on JitPack | `com.github.Deware-PK.SynthetixLib` (only mavenLocal uses `com.github.dewarepk`) |
| Plugin package `com.github.dewarepk.synthetixlib.myplugin` | any other package, e.g. `com.acme.myplugin` |
| `plugin.yml` with `loader:` | `paper-plugin.yml` |
| HikariCP in `implementation` | `compileOnly` + `PluginLoader` |
| Uploading `*-plain.jar` | upload the shaded `<name>-<version>.jar` |
| Compiling against Paper 26.x | compile against `1.21.11-R0.1-SNAPSHOT`; it runs on 26.x |
