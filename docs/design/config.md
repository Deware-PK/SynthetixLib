# synthetixlib-config — API design

Status: **Approved** (2026-10-14, all §9 defaults accepted) · Date: 2026-10-14
Package root: `com.github.dewarepk.synthetixlib.config` · Depends on: core, paper-api only (ADR 0012)

Typed config + locale, written in-house on top of Bukkit `YamlConfiguration` (SnakeYAML ships in Paper).
Fixes SnapPlug bug #6 (comments lost, re-save on every reload).

---

## 1. Defining a config — records + annotations

```java
@Comment("InteractiveAuction main config")
public record AuctionConfig(
        @Comment("Minimum starting bid") @Range(min = 1) int minBid,          // key: min-bid
        @Comment("How long bidding lasts, e.g. 30s, 2m, 1h30m") Duration bidTime,
        @Key("anti-snipe") AntiSnipe antiSnipe,                                // nested record = section
        List<String> blacklist,
        Map<String, Zone> zones) {

    public record AntiSnipe(boolean enabled, @Range(min = 1) int extendSeconds) {}
    public record Zone(String world, int x, int y, int z, @Range(min = 1) int radius) {}

    public static AuctionConfig defaults() {
        return new AuctionConfig(100, Duration.ofSeconds(60), new AntiSnipe(true, 10),
                List.of("BEDROCK"), Map.of("spawn", new Zone("world", 0, 64, 0, 8)));
    }
}
```

Produces (first start):
```yaml
# InteractiveAuction main config
config-version: 1
# Minimum starting bid
min-bid: 100
# How long bidding lasts, e.g. 30s, 2m, 1h30m
bid-time: 60s
anti-snipe:
  enabled: true
  extend-seconds: 10
blacklist:
- BEDROCK
zones:
  spawn: {world: world, x: 0, y: 64, z: 0, radius: 8}
```

Rules
- **Records only** → every loaded config is an immutable snapshot; reload swaps the reference atomically.
- Keys: component name camelCase → kebab-case; `@Key` overrides.
- Annotations: `@Comment` (component or type, multi-line via `String[]`), `@Key`, `@Range(min,max)`,
  `@NotEmpty`. Records may implement `Validated` (`List<String> validate()`) for cross-field rules.
- Defaults come from a `Supplier<T>` (usually `T::defaults`), not from a resource file → single source of truth.

## 2. Loading and using

```java
Configs configs = Configs.create(lib);                  // module service, closed with lib (§7)

ConfigHandle<AuctionConfig> config = configs.file("config.yml", AuctionConfig.class, AuctionConfig::defaults)
        .version(2)
        .migrate(1, 2, tree -> tree.rename("min-price", "min-bid"))
        .load();                                         // sync; call in onEnable

AuctionConfig c = config.get();                         // current snapshot, lock-free
config.onReload(newConfig -> rebuildZones(newConfig.zones()));

ReloadResult r = config.reload();                       // e.g. from /auction reload
r.errors().forEach(sender::sendMessage);                // "zones.spawn.radius: must be >= 1 (was 0) → using 8"
config.reloadAsync().thenAccept(...);                   // IO on lib.scheduler().async(), listeners on global thread
```

Load algorithm (fixes bug #6)
1. File missing → write defaults with comments. Done.
2. Read file (`parseComments(true)`), read `config-version` (missing = 1).
3. If version < target: backup `config.yml.v<old>.bak`, run migrations in order on the raw tree.
4. Map tree → record. Missing keys → default value (**added to file with its comment**).
   Invalid value → default for that field + error entry (file value is **kept**, not overwritten).
   Unknown keys → kept in file + warning.
5. **Save only if** step 1/3 happened or keys were added. Plain reload never rewrites the file.
   User comments are preserved (round-trip through `YamlConfiguration` with parsed comments).

## 3. Supported types
`boolean/int/long/double/float` (+ boxed), `String`, enums (case-insensitive), `Duration` (`30s`, `5m`,
`1h30m`, `250ms`), `UUID`, `NamespacedKey`, `Key`, registry types by key (`Material`, `Sound`,
`EntityType`, … via Paper `RegistryAccess` — invalid key = validation error), `List<T>`, `Set<T>`,
`Map<String,T>`, `Optional<T>` (missing/null allowed), nested records.
Custom types: `configs.codec(Type.class, Codec<Type>)` where `Codec` = `decode(Object raw) / encode(T)`.

## 3b. Serialization layer (reusable outside YAML)

Object ⇄ data conversion is its own layer, independent of files, so other modules reuse it
(database rows/JSON columns, PDC blobs, network/JSON via Gson that ships with Paper).

```java
Codecs codecs = configs.codecs();                       // registry: built-ins + custom codecs

Object tree = codecs.encode(airdrop);                   // → Map/List/String/Number/Boolean tree
Airdrop back = codecs.decode(tree, Airdrop.class);      // → record (DecodeException with path on error)
String json  = codecs.toJson(airdrop);  Airdrop a2 = codecs.fromJson(json, Airdrop.class);   // Gson from Paper

codecs.register(Money.class, Codec.of(                  // custom type
        raw -> Money.parse((String) raw),               // decode
        money -> money.toString()));                    // encode
codecs.register(Region.class, Codec.string(Region::parse, Region::serialize));   // helper for string-backed types
```

- **Records** are encoded automatically (component by component, kebab-case keys); generics
  (`List<Map<String, Zone>>`) resolved from the record's declared types.
- **Polymorphism via sealed interfaces** — e.g. airdrop rewards:
  ```java
  public sealed interface Reward permits ItemReward, CommandReward, MoneyReward {}
  public record ItemReward(ItemStack item, int chance) implements Reward {}
  public record CommandReward(String command) implements Reward {}
  ```
  ```yaml
  rewards:
  - {type: item, item: {material: DIAMOND, amount: 3}, chance: 50}
  - {type: command, command: "eco give %player% 100"}
  ```
  Discriminator `type` = kebab-case of the record name minus the interface name suffix (`ItemReward` → `item`),
  override with `@TypeName("...")`. Unknown `type` → decode error with path.
- `null`: only allowed for `Optional<T>` / `@Nullable` components; otherwise default + error.
- Guarantee: `decode(encode(x)).equals(x)` for every supported type — enforced by round-trip unit tests.
- YAML files, collections and locale are just this layer + `YamlConfiguration` + comments.

## 4. Migrations (needed for InteractiveAuction 1.0 → 2.0)
`ConfigTree` = mutable view of the raw YAML map used only inside migrations:
`rename(from, to)`, `move(from, to)`, `remove(path)`, `set(path, value)`, `get(path)`,
`transform(path, Function<Object,Object>)`. Paths use dots (`anti-snipe.enabled`).
A migration can also read another file (e.g. old Okaeri config) via `tree.importFrom(Path)` — for one-off
product migrations.

## 4b. Collections — one file per object (`airdrops/<id>.yml`)

For things players/admins create at runtime (airdrops, zones, crates, NPCs…). Same record mapping,
comments, validation and migrations as `file(...)`, but one YAML file per object, keyed by id.

```java
// settings.yml — plugin-wide (user edits the file, code only reads)
ConfigHandle<Settings> settings = configs.file("settings.yml", Settings.class, Settings::defaults).load();

// airdrops/*.yml — one file per airdrop (created/edited by commands, user may also edit by hand)
ConfigCollection<Airdrop> airdrops = configs.collection("airdrops", Airdrop.class,
                id -> settings.get().defaultAirdrop().withName(id))   // per-object defaults may use settings
        .version(1)
        .template("_template.yml")       // optional: if present, new objects start from this file
        .load();                         // loads every airdrops/*.yml (files starting with "_" are ignored)

// /airdrop create myairdrop
Airdrop a = airdrops.create("myairdrop");                       // writes airdrops/myairdrop.yml with comments
airdrops.create("vip", new Airdrop("VIP drop", loc, Duration.ofMinutes(30), loot, true));

// /airdrop setloc myairdrop
airdrops.update("myairdrop", d -> d.withLocation(player.getLocation()));  // only changed keys rewritten, comments kept

Optional<Airdrop> one = airdrops.get("myairdrop");
Map<String, Airdrop> all = airdrops.all();                      // immutable snapshot
airdrops.rename("myairdrop", "spawn-drop");
airdrops.delete("spawn-drop");                                  // moved to airdrops/.deleted/, not destroyed

airdrops.onChange(change -> switch (change) {                   // sealed: Added / Updated / Removed
    case Added<Airdrop> add     -> spawner.start(add.id(), add.value());
    case Updated<Airdrop> upd   -> spawner.restart(upd.id(), upd.value());
    case Removed<Airdrop> rem   -> spawner.stop(rem.id());
});
airdrops.reload();   // re-reads all files; a broken file is skipped with an error, the rest still load
```

Rules
- Id = file name without `.yml`; validated `[a-z0-9_-]{1,64}` (lower-cased), else `IllegalArgumentException`.
  Command layer shows a friendly message (locale).
- `create` fails if the id exists; `update` fails if it does not. In-memory snapshot changes immediately;
  file writes go through a **single-writer queue per collection on `lib.scheduler().async()`**, written
  atomically (temp file + `ATOMIC_MOVE`) → no half-written files, no lost updates.
- Hand edits are picked up by `reload()` (or a future optional file watcher). Missing keys filled from
  the per-object default; invalid fields → default + error, file kept (same as §2).
- Records get `with...` copy helpers by hand (Java has no withers); `update` takes `UnaryOperator<T>`.
- Same atomic write is used for `file(...)` saves.

## 4c. Extra types needed by collections
- `Location` → `{world, x, y, z, yaw, pitch}` (world by name; unknown world = validation error, kept in file).
- `ItemStack` → readable form when possible (`material, amount, name, lore, item-model` as MiniMessage),
  falls back to `base64: …` (`ItemStack#serializeAsBytes`) for items with data we cannot express. Never lossy.

## 5. Locale

```java
public record Messages(
        @Comment("Placeholders: <amount>, <item>") String bidPlaced,
        String outbid,
        List<String> help) {                              // multi-line message
    public static Messages defaults() {
        return new Messages("<green>You bid <amount> on <item>", "<red>You were outbid!",
                List.of("<gold>/auction bid <amount>", "<gold>/auction info"));
    }
}

LocaleHandle<Messages> lang = configs.locale("lang", Messages.class, Messages::defaults)
        .defaultLocale(Locale.US)          // lang/en_US.yml generated from defaults()
        .bundled("th_TH")                  // copies lang/th_TH.yml from the jar if missing, fills missing keys
        .perPlayer(true)                   // uses player.locale(), falls back to default
        .load();

lang.send(player, Messages::bidPlaced,
        Placeholder.unparsed("amount", "100"), Placeholder.component("item", itemName));
Component c = lang.get(player, Messages::outbid);
```
- Messages are MiniMessage strings rendered through `lib.text()` (so `text.with(prefix)` tags work).
- Empty string = message disabled (`send` does nothing). `List<String>` = one line each.
- Same load/migrate/no-resave rules as configs. Locale files reload together.

## 6. Reflection (ADR 0013)
Mapping records needs `Class#getRecordComponents()` and the canonical constructor
(`java.lang.reflect`). Proposal: allow `java.lang.reflect` **only in `config.mapping`** package, only on
classes passed in by the consumer (no `Class.forName`, no strings → relocation-safe). ArchUnit rule
updated accordingly. Mapping happens at load time only, never per access.

## 7. Module wiring (small core change)
Modules need to close with `lib`. Proposal: core gets `lib.attach(AutoCloseable)` — closed in reverse
order on `lib.close()`. `Configs.create(lib)` attaches itself. Same pattern for command/gui/database later.

## 8. Testing
Almost everything is unit-testable without a server (`YamlConfiguration` works in plain JUnit):
mapping of every type, kebab-case, defaults fill-in (+comments written), invalid → default + error,
unknown keys kept, no re-save on unchanged reload (file mtime/bytes unchanged), user comments preserved,
migrations + backup, locale fallback. Self-tests only for registry types (`Material`, `Sound`) and
per-player locale send.

## 9. Decisions for owner (defaults chosen)
1. **Mapping: records + annotations via narrow reflection** (§6, chosen — closest to Okaeri, least code
   for users) vs explicit codec DSL without reflection (verbose: every field declared twice).
2. **Records only** (immutable snapshots, chosen) vs also mutable classes.
3. **Invalid value → field default + error, file kept** (chosen) vs refuse to load / disable plugin.
4. **YAML engine: Bukkit `YamlConfiguration`** (chosen, ships with Paper) vs own parser.
5. **`lib.attach(AutoCloseable)` in core** for module lifecycle (chosen).
6. Locale file layout `lang/<locale>.yml` with `Locale` tags like `th_TH` (chosen).
7. **Collections** (§4b): one file per object, id = file name, delete moves to `.deleted/` (chosen) vs hard delete.
8. **Template file** `_template.yml` optional per collection (chosen) — lets server owners set their own defaults.
9. **Serialization layer reusable outside config** (§3b, chosen) incl. sealed-interface polymorphism with `type:`
   and JSON via Paper's Gson.
