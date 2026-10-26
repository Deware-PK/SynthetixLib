# Changelog

SynthetixLib release notes for people (and agents) using the library. Newest first. API breaks are marked **Breaking**.
Releases are git tags `v<x.y.z>` (0.1.0 and 0.2.0 were never tagged). JitPack works from **v0.7.0** on
(`com.github.Deware-PK.SynthetixLib:synthetixlib-<module>:v<x.y.z>`); older tags have no `jitpack.yml` and do not build there.

## 0.9.0
- gui: `MenuBuilder#actionAsync(name, handler)` and `actionAsync(name, Duration, handler)`: like `action`, but the view stays
  busy until the returned `CompletableFuture` completes (default timeout 30 s), so a double click cannot start the work twice.
  Later click steps of the same item wait for it and are skipped if it fails. A failure is logged and the player gets
  `gui.action-failed` if the lang `gui` map has the key `action-failed`. Your update must still be atomic.
- gui: menu items may set `busy:` (an item, shown in the slot while an `actionAsync` started from it runs).
- docs: rule `gui-async-action-for-io`; the template's `Messages` gains the `action-failed` gui key.

## 0.8.0
- command: `cooldown(Function<CommandSender, Duration>)` and `cooldown(fn, bypassPermission)`: the length is asked at every
  successful run, so a config reload applies; `Duration.ZERO` = no cooldown this time. The `Duration` overloads stay.
- command: `CommandService#cooldowns()` returns the new `CommandCooldowns`: `remaining(Player | UUID, commandPath)`,
  `reset(UUID, commandPath)`, `reset(UUID)`. `commandPath` is the path as written in code (`"kit reset"`), never an alias.
- command: `<time>` of the cooldown message reads `1m 12s` / `1h 0m` / `2d 3h` (was `1m12s`). New `CommandMessages.timeUnits`
  (**Breaking:** the record has a new last component, `CommandMessages.defaults()` fills it; lang files gain `time-units:`).
- config: new `TimeFormat` (an instance: `TimeFormat.of(TimeUnits)`) and `TimeUnits` (`d h m s` words for the lang file).
- docs: `ctx.fail(...)` never starts the cooldown; rule `command-cooldown-not-on-failure`.
- dialog: `type: number-field` input, a typed number for money and wide ranges (`format: integer | decimal`, `min`, `max`,
  `initial`, `max-length`); the record gets `long` / `int` or `double` / `BigDecimal`; `1,000` and `1_000` are accepted.
  `click.response().number(key)` / `integer(key)` read it too.
- dialog: limits from code per showing: `DialogBuilder#inputRange(key, view -> Range)` (slider or number-field) and
  `#maxLength(key, view -> int)`; the answer is checked against the values the player saw, `<min>` / `<max>` of the error
  line use them. New `Range`.
- dialog: `DialogBuilder#validate(name, Record.class, (form, click) -> Optional<FieldError>)` and `#validateAll(... List<FieldError>)`
  run before the handler of a `submit` / `submitAsync`; an error re-shows the dialog like a file limit. New `FieldError`.
- dialog: the config codecs gained a `BigDecimal` codec (registered by `DialogService`).
- gui + dialog: a bundled menu / dialog file is still copied once and never overwritten, but when the jar ships a higher
  `config-version` than the copy in the data folder, the library logs one warning at load (`[dialogs/sell.yml] the plugin ships
  config-version 2, this file is 1: delete it to get the new default or update it by hand (old copy kept)`). New rule
  `files-bump-config-version`: raise the version whenever a release changes a bundled file.
- config (behaviour change, not breaking): language files (`LocaleHandle`) now also get the **map entries** they lack
  (`dialog: { sell-error-too-high: ... }`), from the bundled `lang/<tag>.yml` of that language if it has the key, else from the
  default locale; before, a key added in a release never reached an existing `lang/*.yml` and the client showed the raw key.
  `''` still switches a message off. File configs and collections keep their rule (a removed map entry is not added back).
- gui + dialog: a `<lang:key>` that is in the default language but not in the viewer's language file is shown in the
  default language (logged once) instead of as a translatable.
- docs: `references/command.md` shows how an admin child opts out of an inherited cooldown (`cooldown(Duration.ZERO)`).
- **Breaking:** `CommandMessages` has a new last component `timeUnits` (code that calls its constructor directly must
  add it; `CommandMessages.defaults()` and lang files are filled automatically). Nothing else.

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
