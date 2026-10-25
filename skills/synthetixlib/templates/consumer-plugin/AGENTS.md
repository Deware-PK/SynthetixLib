# MyPlugin — agent guide

A Paper plugin built on **SynthetixLib** (shaded + relocated). **Use the `synthetixlib` skill** (SKILL.md and the
reference for each module you touch) before writing code; install it with `npx skills add Deware-PK/SynthetixLib`.

## Build
- `./gradlew build` → `build/libs/myplugin-<version>.jar` (the shaded jar; `*-plain.jar` is not for servers).
- Library version: `synthetixVersion` in `gradle.properties` (a JitPack tag, e.g. `v0.8.0`). If it is older than the
  skill, read the skill's CHANGELOG.md before using a newer API.
- Using a local SynthetixLib checkout: `-PsynthetixLocal=true` (reads `synthetixLocalVersion`, from `publishToMavenLocal`).

## Rules
1. Paper only (no Spigot API), Java 21, compile against `paper-api` 1.21.11 only; must also run on 26.x.
2. Entry: `SynthetixLib.init(this)` in `onEnable`, `lib.close()` in `onDisable`. No base class, no static/global state,
   no singletons.
3. No NMS / CraftBukkit / reflection / `Class.forName` / class names in strings.
4. Schedule only through `lib.scheduler()` (global / region / entity / async). Never `Bukkit.getScheduler()`.
5. Text is MiniMessage only: no `ChatColor`, no `§`, no legacy serializers. Every player-visible text lives in `lang/`
   (typed `Messages` record), never inline.
6. Config is a typed record ⇄ YAML (`Configs`), never string paths. Keep `@Comment`s; do not re-save on every reload.
7. Commands: the command module (Brigadier), no shared mutable state in a command; messages through the locale.
8. Items: data components + `item_model`, no int custom model data; `PersistentDataContainer` for item/entity data.
9. Inventories: `getHolder(false)` only. Per-player state is not kept in static maps and is cleaned on quit.
10. Database: shut down through `lib.close()` so pending writes finish; HikariCP is loaded by `MyPluginLoader`, never shaded.
11. Your package must not start with `com.github.dewarepk.synthetixlib` (relocation would rewrite it).
12. Third-party runtime libraries are not shaded: load them with the Paper `PluginLoader` (`paper-plugin.yml`).
13. Bundled files (`menus/*.yml`, `dialogs/*.yml`, `config.yml`) are copied once and never overwritten: when a release changes
    one, raise its `config-version`, otherwise servers that update keep the old copy (the library warns when it is behind).

## Layout
`MyPlugin` (wiring) · `MyPluginLoader` (runtime libs) · `Settings` (config.yml) · `Messages` (lang/*.yml) ·
`MainMenu` + `menus/main.yml` · `GreetDialog` + `dialogs/greet.yml` · `MyPluginCommand`.
