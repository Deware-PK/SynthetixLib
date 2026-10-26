---
name: synthetixlib
description: >-
  Build Paper (Minecraft) plugins with SynthetixLib: typed YAML config and language files, Brigadier commands,
  YAML-defined inventory menus, native dialogs and async SQL (SQLite/MySQL), Folia-safe. Use when the user asks for a
  Paper plugin, mentions SynthetixLib, or wants a Minecraft plugin with menus, dialogs, config, commands or a database.
---

# SynthetixLib

SynthetixLib is a library for **Paper** plugins (Java 21, Paper 1.21.11 to 26.x, Folia-safe). The plugin `extends JavaPlugin`
and calls `SynthetixLib.init(this)`; the library is **shaded and relocated** into the plugin jar, so buyers get one jar.
Menus and dialogs are YAML files that server owners can edit; code only registers the behaviour they refer to by name.

Written for SynthetixLib 0.8.0.
Check the library version in the project's build file (`synthetixVersion` in `gradle.properties`, or the
`synthetixlib-*` dependency). If it is older than the version above, read `CHANGELOG.md` before using an API: every API
in the references is marked `since` / `changed in`.

## Start a new plugin (30 seconds)

Copy the template, it already builds and uses core + config + command + gui + dialog:

```
pwsh skills/synthetixlib/scripts/new-plugin.ps1 -Target ../auction -Package com.acme.auction -Name Auction
bash skills/synthetixlib/scripts/new-plugin.sh ../auction com.acme.auction Auction
cd ../auction && ./gradlew build        # -> build/libs/auction-1.0.0.jar (the shaded one)
```

Then delete what you do not need and read the reference of each module you touch. The heart of the plugin class:

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

## Rules (do / never)

These are enforced in the library's own build; break one and a reviewer or the buyer's server will notice.

1. **Paper only**, never Spigot-only API. Compile against `paper-api:1.21.11-R0.1-SNAPSHOT`, never against 26.x. Java 21.
2. **Entry:** `SynthetixLib.init(this)` in `onEnable`, `lib.close()` in `onDisable`. No base class.
3. **No static or global state**, no singletons, no static per-player maps. Everything hangs off the `lib` instance;
   per-player state is cleaned on quit and is thread-safe.
4. **No NMS / CraftBukkit / reflection**, no `Class.forName`, no class names in strings.
5. **Threads:** schedule only through `lib.scheduler()` (`global()`, `region(...)`, `entity(e)`, `async()`). Never
   `Bukkit.getScheduler()`, never `runTask*`. Never block the server thread (database and files are async).
6. **Text is MiniMessage / Adventure only.** No `ChatColor`, no `§`, no legacy serializer. Escape player input
   (`lib.text().escape(...)`) before putting it into a MiniMessage string; use placeholders.
7. **Every player-visible text goes through the locale** (a typed `Messages` record, one file per language), also command
   messages and menu/dialog texts (`<lang:key>`). No inline strings in code.
8. **Config is typed:** a record/class mapped to YAML. Never `getConfig().getString("a.b")`. Keep the `@Comment`s;
   do not re-save on every reload.
9. **Commands:** the command module (native Brigadier). No `BasicCommand`, no `plugin.yml` commands, no shared mutable
   state in a command (Folia races), report the alias actually used.
10. **Items:** data components and `item_model`, never int custom model data. **PDC** (`lib.keys()`) for item/entity data.
11. **Inventories:** `getHolder(false)` only. A menu is one definition and one view **per viewer**.
12. **Packaging:** shade + relocate (`<your package>.lib.synthetixlib`); your package must not start with
    `com.github.dewarepk.synthetixlib`; runtime libraries (HikariCP) are loaded by a `PluginLoader`, never shaded.

Details and wrong → right examples: `rules/` (one file per rule) and the pitfalls of each reference.

## Rules by impact

Each file in `rules/` has the why and a wrong -> right pair.

**CRITICAL**
- `core-paper-only-java21`, `core-close-in-ondisable`, `core-no-static-state`, `core-no-nms-reflection`
- `config-typed-not-string-paths`, `config-all-text-through-locale`
- `command-no-shared-state`
- `state-no-static-maps`
- `threads-no-bukkit-scheduler`, `threads-async-then-back`
- `text-minimessage-only`, `text-escape-player-input`
- `gui-getholder-false`, `gui-one-view-per-viewer`, `gui-deny-by-default`, `gui-no-double-click-collect`, `gui-shift-click-input-only`
- `dialog-validate-on-server`
- `db-hikari-via-loader`, `db-close-waits-for-writes`, `db-never-block-server-thread`, `db-parameters-not-concatenation`

**HIGH**
- `core-package-not-lib-prefix`, `core-items-data-components`
- `config-no-resave-on-reload`
- `command-use-brigadier`, `command-messages-through-locale`, `command-register-in-onenable`
- `threads-no-blocking-server-thread`
- `gui-take-inputs-atomically`, `gui-item-model-not-custom-model-data`, `gui-async-action-for-io`
- `dialog-instead-of-anvil`, `dialog-one-use-callbacks`, `dialog-money-is-a-number-field`
- `files-bump-config-version`
- `db-migrations-are-append-only`

**MEDIUM**
- `config-keep-comments`, `config-public-records`
- `command-report-alias-used`, `command-cooldown-not-on-failure`

## Module map: problem → module → reference

| You need | Use | Read |
|---|---|---|
| Build file, JitPack, shade + relocate, `paper-plugin.yml`, loader, bootstrap | setup | `references/setup.md` |
| Scheduler (Folia), MiniMessage text, `ItemBuilder`, PDC keys, `init`/`close` | `synthetixlib-core` | `references/core.md` |
| Settings, per-item config files, language files, hot reload | `synthetixlib-config` | `references/config.md` |
| Commands with subcommands, typed arguments, cooldowns, async work | `synthetixlib-command` | `references/command.md` |
| Inventory menus (pages, lists, inputs, states), editable in YAML | `synthetixlib-gui` | `references/gui.md` |
| Text input, confirmations, forms (player types a price/name) | `synthetixlib-dialog` (**not an anvil GUI**) | `references/dialog.md` |
| SQLite / MySQL, migrations, async queries, safe shutdown | `synthetixlib-database` | `references/database.md` |
| End-to-end: shop menu + buy dialog + SQLite; cooldown command; reloadable config | several | `references/recipes.md` |
| An error or a symptom | any | `references/troubleshooting.md` |

Read the reference of a module before you call it; do not guess the API from the names. A plugin that uses only core +
config + command needs only those three artifacts.

## Which module does the player see

- Player types text or a number, picks an option, answers yes/no: **dialog**.
- Player browses, pages through, drops items into a slot, toggles: **menu (gui)**.
- Both can open each other from a button (`menu:<id>`, `dialog:<id>` in the YAML).

## Working method

1. Start from the template; keep its layout (one class per menu, dialog and command; wiring in the plugin class).
2. Put every tunable and every text in config or `lang/`; the YAML files are the user interface of the plugin.
3. Build with `./gradlew build`; fix `verifyRelocation` failures (an un-relocated library class in the jar) first.
4. Test on a Paper server (1.21.11 and, if you can, 26.x): start, `/<command> reload`, open each menu and dialog.
5. When something fails, look it up in `references/troubleshooting.md` before changing the design.
