# config

`synthetixlib-config` (since 0.2.0). Typed YAML: a `record` is the config, `config.yml`, one-file-per-object
collections and per-language message files. Comments kept, never re-saved on a plain reload, migrations, hot reload.
Depends on core. Design: `docs/design/config.md` (read its section 10, "Deviations").

## When to use / when not

- Use it for every tunable and every player-visible text: the YAML files are the user interface of your plugin.
- `file(...)` for plugin-wide settings, `collection(...)` for things players/admins create at runtime (one file per
  airdrop, zone, crate), `locale(...)` for messages (one file per language).
- Never `getConfig().getString("a.b")`, never string paths, never hand-written `YamlConfiguration` code.
- Not for a database: use `database` (it can reuse `Codecs` for JSON columns).

## Minimal example

A config is a **public record**; defaults come from a method, not from a resource file.

```java snippet=config-settings-record
@Comment("SynthetixExample settings")
public record Settings(@Comment("Give every joining player a demo ticket") boolean welcomeTicket,
                       @Key("default-airdrop")
                       @Comment("New airdrops start from this one (the name is replaced by the id)")
                       Airdrop defaultAirdrop,
                       @Comment("Where the airdrop claims are stored (sqlite or mysql); a restart applies a change")
                       DatabaseSettings database,
                       @Comment("Dialogs: how long their buttons work; debug logs the thread of the first click at INFO")
                       DialogSettings dialog,
                       @Key("sell-min-price")
                       @Comment("The sell dialog: the lowest price a player may type")
                       long sellMinPrice,
                       @Key("sell-max-price")
                       @Comment("The sell dialog: the highest price a player may type")
                       long sellMaxPrice,
                       @Key("give-wait")
                       @Comment("/example give: how long a player waits between uses; a reload applies a change")
                       Duration giveWait) {

    public static Settings defaults() {
        return new Settings(true, Airdrop.standard(),
                DatabaseSettings.sqlite("example.db").withTablePrefix("ex_"),
                new DialogSettings(Duration.ofMinutes(5), true), 1, 1_000_000, Duration.ofSeconds(10));
    }
}
```

```java snippet=config-wiring
final Configs configs = Configs.create(lib);
DatabaseSettings.register(configs.codecs());
settings = configs.file("settings.yml", Settings.class, Settings::defaults).load();
lang = configs.locale("lang", Messages.class, Messages::defaults)
        .defaultLocale(Locale.US)
        .bundled("th_TH")
        .perPlayer(true)
        .load();
airdrops = configs.collection("airdrops", Airdrop.class, id -> settings.get().defaultAirdrop().withName(id))
        .load();
```

`Configs.create(lib)` attaches itself to `lib` (closed with it). `load()` is synchronous: call it in `onEnable`.
`handle.get()` is a lock-free immutable snapshot; read it each time you need a value, do not cache fields of it.
(The `DatabaseSettings.register` line belongs to the database module; drop it if you do not use it.)

## YAML the buyer edits

Keys are the record component names in kebab-case (`welcomeTicket` -> `welcome-ticket`); `@Key("x")` overrides.
A nested record is a section, `List<T>` a list, `Map<String, T>` a map, `Duration` a string like `30s`, `5m`,
`1h30m`, `250ms`. Language files hold one MiniMessage string per key:

```yaml snippet=lang-th-commands
commands:
  player-only: <red>ใช้ได้เฉพาะผู้เล่น
  cooldown: <red>รอ <time> ก่อนใช้ /<label> อีกครั้ง
  busy: <red>รอสักครู่ /<label> ยังทำงานอยู่
  player-not-found: "<red>ไม่พบผู้เล่น: <input>"
  invalid-value: "<red><input> ไม่ถูกต้อง: <reason>"
  error: <red>เกิดข้อผิดพลาด ดูที่คอนโซล
  help-header: "<yellow>คำสั่งของ /<command>:"
  help-line: <yellow>/<command> <usage> <gray>- <description>
```

## Records and annotations

```java snippet=config-airdrop-record
@Comment("An airdrop. Edit this file and reload, or let a command change it.")
public record Airdrop(@Comment("Name shown to players") String name,
                      @Comment("Time between two drops, e.g. 30m or 1h30m") Duration interval,
                      @Comment("The drop waits until this many players are online") @Range(min = 0) int minPlayers,
                      @Comment({"What a drop gives. Types: item, command", "A command can use %player%"})
                      @NotEmpty List<Reward> rewards) {

    /** A starting point for every new airdrop; the plugin's settings may replace it. */
    public static Airdrop standard() {
        return new Airdrop("Airdrop", Duration.ofMinutes(30), 1, List.of(
                new ItemReward(Material.DIAMOND, 3, 25),
                new ItemReward(Material.GOLDEN_APPLE, 1, 50),
                new CommandReward("say %player% found an airdrop!")));
    }

    /** @return a copy with another name */
    public Airdrop withName(final String name) {
        return new Airdrop(name, interval, minPlayers, rewards);
    }
}
```

| Annotation | Effect |
|---|---|
| `@Comment("...")` or `@Comment({"a","b"})` | YAML comment above the key (component or record) |
| `@Key("name")` | override the kebab-case key |
| `@Range(min, max)` | numeric bounds; violation = error, field default used |
| `@NotEmpty` | list/map/string must not be empty |
| `@TypeName("x")` | discriminator of a sealed-interface variant |

Supported types: `boolean/int/long/double/float` (+ boxed), `String`, enums (case-insensitive), `Duration`, `UUID`,
`NamespacedKey`, `Key`, registry types by key (`Material`, `Sound`, `EntityType`, `Enchantment`, `PotionEffectType`,
`Biome`), `Location`, `ItemStack`, `List`, `Set`, `Map<String, T>`, `Optional<T>`, nested records, sealed interfaces.
Nullable: `Optional<T>` or `org.jspecify.annotations.Nullable` (the jetbrains one is ignored).

Polymorphism: a sealed interface is a list of `type: ...` entries. `type` = kebab-case of the record name minus the
interface name (`ItemReward` -> `item`).

```java snippet=config-sealed-reward
public sealed interface Reward permits ItemReward, CommandReward {
}
```

```java snippet=config-item-reward
public record ItemReward(@Comment("Material key, e.g. diamond") Material material,
                         @Range(min = 1, max = 64) int amount,
                         @Comment("Chance in percent") @Range(min = 1, max = 100) int chance) implements Reward {
}
```

Records must be `public` (mapping uses reflection without `setAccessible`). There is no `Validated` interface:
cross-field rules go in an `onReload` listener or the code that reads the snapshot.

## Reloading

```java snippet=config-reload
private void run(final CommandCall ctx) {
    final List<String> errors = new ArrayList<>(settings.reload().errors());
    airdrops.reload().files().forEach((file, result) -> errors.addAll(result.errors()));
    lang.reload().files().forEach((file, result) -> errors.addAll(result.errors()));
    if (errors.isEmpty()) {
        ctx.reply(lang, Messages::reloaded);
    } else {
        ctx.reply(lang, Messages::reloadFailed, Placeholder.unparsed("errors", String.join("; ", errors)));
    }
}
```

```java snippet=config-reload-result
final ReloadResult result = handle.reload();                      // never rewrites the file
result.errors().forEach(error -> lib.plugin().getLogger().warning(error));
handle.reloadAsync().thenAccept(async -> lib.plugin().getLogger().info("changed: " + async.changed()));
```

Load algorithm: missing file -> written from defaults with comments. Missing keys are added with their comment. An invalid
value gives the field default plus an error entry, **the file text is kept**. Unknown keys are kept (warning). The file
is rewritten only on create, migrate or when keys were added, never on a plain reload (a write keeps comments but drops
blank lines). `reloadAsync()` does the IO on the async scheduler and notifies listeners on the global thread;
`onReload(listener)` receives the new snapshot.

## Versions and migrations

```java snippet=config-migration
final ConfigHandle<Airdrop> handle = configs.file("airdrop.yml", Airdrop.class, Airdrop::standard)
        .version(2)
        .migrate(1, 2, tree -> tree.rename("min-players", "min-online"))
        .load();
```

Missing `config-version` means 1. A migration runs on the raw tree (`ConfigTree`: `rename`, `move`, `remove`, `set`,
`get`, `has`, `transform`, `importFrom(Path)`), after a backup `config.yml.v<old>.bak`. `ConfigTree` paths use dots, so a
key containing a dot cannot be addressed.

## Collections: one file per object

```java snippet=config-collection
airdrops.create("spawn");                                         // IllegalStateException if it exists
airdrops.update("spawn", drop -> drop.withName("Spawn drop"));    // rewrites only the changed keys
final Optional<Airdrop> one = airdrops.get("spawn");
final Map<String, Airdrop> all = airdrops.all();                  // immutable snapshot
airdrops.rename("spawn", "hub");
final boolean deleted = airdrops.delete("hub");                   // moved to airdrops/.deleted/

airdrops.onChange(change -> {
    switch (change) {
        case Change.Added<Airdrop> added -> lib.plugin().getLogger().info("added " + added.id());
        case Change.Updated<Airdrop> updated -> lib.plugin().getLogger().info("updated " + updated.id());
        case Change.Removed<Airdrop> removed -> lib.plugin().getLogger().info("removed " + removed.id());
    }
});
final FilesReloadResult result = airdrops.reload();               // a broken file is skipped, the rest load
result.files().forEach((file, outcome) -> outcome.errors().forEach(lib.plugin().getLogger()::warning));
```

- Created in the wiring above with `configs.collection("airdrops", Airdrop.class, id -> defaults).load()`. Optional
  `.version(n)`, `.migrate(...)`, `.template("_template.yml")`. Files starting with `_` are ignored.
- Id = file name without `.yml`, `[a-z0-9_-]{1,64}`, lower-cased; otherwise `IllegalArgumentException`.
  `create` of an existing id (and `rename` onto an existing one) throws `IllegalStateException`; `update`/`rename` of a
  missing id throws `NoSuchElementException`. The `command-fail` snippet in `command.md` shows the handling.
- The snapshot changes at once; file writes go through one async queue per collection, atomically (temp file + move), and
  `close()` drains it. `delete` moves the file to `.deleted/`. `Change` is sealed: `Added | Updated | Removed`.
- Hand edits are picked up by `reload()` (no file watcher). A broken file is skipped with an error, the rest load.
- Make records easy to copy: add `with...` methods by hand (`withName`), `update` takes a `UnaryOperator`.

## Language files

```java snippet=config-locale-send
lang.send(player, Messages::welcome, Placeholder.unparsed("name", player.getName()));
lang.sendLines(player, Messages::help);                           // List<String>: one chat line each
final Component line = lang.get(player, Messages::reloaded);      // when you need the Component
final Component forConsole = lang.get(lang.defaultLocale(), Messages::reloaded);
```

- `configs.locale("lang", Messages.class, Messages::defaults).defaultLocale(Locale.US).bundled("th_TH").perPlayer(true)
  .load()`. `defaultLocale` writes `lang/en_US.yml` from `defaults()`; `bundled` copies `lang/th_TH.yml` from the jar if
  missing and fills missing keys.
- Language of an audience: exact tag (`th_TH`) -> language file (`th`) -> any file of that language -> default. An explicit
  `Locale` is always honoured; `perPlayer` (default `false`) only decides whether an `Audience` is asked for its locale.
- An empty string disables a message. A `List<String>` message is read with `sendLines` / `getLines` (not `send`/`get`).
- Messages are MiniMessage rendered through `lib.text()`; use placeholders, never string concatenation of player input.
- Nest `CommandMessages` and `Map<String, String>` (menu and dialog texts, used as `<lang:key>`) in the same record so
  every text lives in the language file.

## Codecs (serialization outside YAML)

```java snippet=config-codec
final Codecs codecs = configs.codecs();
codecs.register(Money.class, Codec.string(Money::parse, Money::format));

final Object tree = codecs.encode(new Money(1250));              // "12.50"
final Money back = codecs.decode(tree, Money.class);              // DecodeException (with path) if invalid
final String json = codecs.toJson(Airdrop.standard());
final Airdrop airdrop = codecs.fromJson(json, Airdrop.class);
```

`configs.codecs()` is the registry (built-ins plus Bukkit types). `decode(encode(x)).equals(x)` holds for every
supported type. JSON uses the Gson that ships with Paper. `Codec.of(decode, encode)` and `Codec.string(parse, format)`
build custom codecs; `TypeToken<T>` carries generics. Register with `codecs.register(...)` (it is **not**
`configs.codec(...)`). Codecs live per `Codecs` instance: no static codec registries.

## API table

| Type | For | Since |
|---|---|---|
| `Configs` | `create(lib)`, `file`, `collection`, `locale`, `codecs()`, `lib()`, `isClosed()`, `close()` | 0.2.0 |
| `ConfigFileBuilder<T>` | `version`, `migrate`, `load` | 0.2.0 |
| `ConfigHandle<T>` | `get`, `path`, `onReload`, `reload`, `reloadAsync` | 0.2.0 |
| `ConfigCollectionBuilder<T>` | `version`, `migrate`, `template`, `load` | 0.2.0 |
| `ConfigCollection<T>` | `get`, `all`, `ids`, `create`, `update`, `rename`, `delete`, `onChange`, `reload`, `reloadAsync` | 0.2.0 |
| `Change<T>` | sealed: `Added`, `Updated` (has `previous`), `Removed`; `id()` | 0.2.0 |
| `LocaleBuilder<T>` | `defaultLocale`, `bundled`, `perPlayer`, `text`, `version`, `migrate`, `load` | 0.2.0 |
| `LocaleHandle<T>` | `send`, `sendLines`, `get`, `getLines`, `defaultLocale`, `reload` | 0.2.0 |
| `ReloadResult` | `errors()`, `warnings()`, `changed()`, `ok()` | 0.2.0 |
| `FilesReloadResult` | per-file `files()`, `removed()`, `errors()`, `warnings()`, `changed()`, `ok()` | 0.2.0 |
| `annotation.Comment/Key/Range/NotEmpty/TypeName` | see table above | 0.2.0 |
| `migration.ConfigTree`, `migration.Migration` | edit the raw tree in a migration | 0.2.0 |
| `codec.Codec`, `Codecs`, `DecodeContext`, `DecodeException`, `Problem`, `TypeToken`, `Durations` | serialization layer; `Durations.parse/format` | 0.2.0 |

Left out on purpose (not for consumers): `codec.BukkitCodecs` (internal, `configs.codecs()` already has it), `config.mapping.*`
(`CodecResolver`, `LenientResult`, `Schema`, `TypeRef`) and `config.yaml.*` (`AtomicFiles`, `FileConfig`,
`YamlTrees`) are internal implementation.

## Threads

- `load()` and `reload()` are synchronous (call from `onEnable` or the command thread); `reloadAsync()` and the
  collection write queue use `lib.scheduler().async()`. Reload listeners of `reloadAsync` run on the global thread.
- Snapshots are immutable, so reading from any thread is safe. Do not hand a Bukkit object from a snapshot to another
  thread without checking the thread rules in `core.md`.

## Pitfalls

- Wrong: `plugin.getConfig().getInt("min-bid")` -> right: a record component read from `settings.get()` (rule
  `config-typed-not-string-paths`).
- Wrong: `saveConfig()` / `saveDefaultConfig()` on reload -> right: `handle.reload()`, which never rewrites the file
  (rule `config-no-resave-on-reload`).
- Wrong: dropping `@Comment`, or stripping comments when copying defaults -> right: comments are written for fixed paths
  automatically (rule `config-keep-comments`).
- Wrong: `player.sendMessage("<red>No money")` / inline strings -> right: a `Messages` record + `lang.send` (rule
  `config-all-text-through-locale`).
- Wrong: a package-private or nested private config record -> right: `public record`.
- Wrong: `configs.collection(...)` id from player input without a check -> right: catch
  `IllegalArgumentException` / `IllegalStateException` / `NoSuchElementException` and answer through the locale.
- Wrong: relying on blank lines between entries surviving a write (a write drops them).

## Limits

- Comments only for fixed paths: a key keeps its `@Comment`, a **list entry never gets one** (nor a record inside a
  `Map`/`List`); put the comment on the key that holds the list. Invalid list element is dropped, invalid map entry
  falls back to the entry with the same default key or is ignored; the file text stays.
- No file watcher; reload is explicit (`/yourcommand reload`).
- No `Validated` interface (design deviation).

Design: `docs/design/config.md`.
