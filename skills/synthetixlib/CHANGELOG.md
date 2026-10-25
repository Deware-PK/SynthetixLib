# Changelog

SynthetixLib release notes for people (and agents) using the library. Newest first. API breaks are marked **Breaking**.
Releases are git tags `v<x.y.z>` (0.1.0 and 0.2.0 were never tagged). JitPack works from **v0.7.0** on
(`com.github.Deware-PK.SynthetixLib:synthetixlib-<module>:v<x.y.z>`); older tags have no `jitpack.yml` and do not build there.

## 0.7.0
- First JitPack release. Agent skill (`skills/synthetixlib`), `llms.txt`, Claude Code marketplace, consumer template and
  `new-plugin` script. No library API change.
- **Breaking:** none.

## 0.6.0 - dialog
- New `synthetixlib-dialog`: Paper Dialog API wrapper, YAML dialogs, typed submit with server-side validation and re-show
  with errors, registry dialogs at bootstrap (`SynthetixDialogs.bootstrap`), gui <-> dialog links through `ScreenOpener`.
- gui: a click action `<prefix>:<arg>` with an unknown prefix now parses as a link (reported at load, item inert)
  instead of a parse error; a bare unknown name is still an error.
- Registry dialog clicks are rate-limited per player (`DialogSettings.registryClickInterval`, default 250 ms).
- **Breaking:** none.

## 0.5.0 - database
- New `synthetixlib-database`: async SQLite/MySQL, HikariCP loaded by the consumer's `PluginLoader` (never shaded),
  migrations (`V<n>__name.sql`, `.mysql.sql` overrides, code migrations), `Copy` between databases, shutdown waits for
  pending writes before the pool closes.
- **Breaking:** none.

## 0.4.0 - gui
- New `synthetixlib-gui`: menus defined in YAML (char pattern, custom-font title), one view per viewer, dupe-proof
  clicks (double-click collect, shift-click, input slots with `takeInputs`), `<lang:key>` texts, async icons, `update-every`.
- An unknown `<lang:key>` becomes a translatable component and warns once.
- **Breaking:** none.

## 0.3.0 - command
- New `synthetixlib-command`: native Brigadier fluent API, typed arguments, cooldowns, async handlers with in-flight
  guard, locale messages, `helpSub()`; the alias actually typed is reported.
- **Breaking:** none.

## 0.2.0 - config
- New `synthetixlib-config`: record <-> YAML codecs, file configs that keep comments and do not re-save on reload,
  per-object collections, locale files (`LocaleHandle`), migrations.
- `LocaleHandle.get(Locale)` / `getLines(Locale)` honour the given locale even when `perPlayer` is off.
- `ConfigCollection.create(id)` refuses a file that differs from the id only in letter case (and says the name to use).
- **Breaking:** none.

## 0.1.0 - core
- `synthetixlib-core`: `SynthetixLib.init(plugin)` / `close()`, Folia-safe scheduler (global / region / entity / async),
  MiniMessage text service, `ItemBuilder` (data components, `item_model`), typed PDC keys.
- **Breaking:** none.
