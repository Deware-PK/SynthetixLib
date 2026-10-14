# synthetixlib-core — API design

Status: **Implemented (0.1.0)** — approved 2026-10-14; no deviations · Task: P1-01 · Date: 2026-10-14
Package root: `com.github.dewarepk.synthetixlib.core`

Core = the things almost every plugin needs, with **only paper-api** as dependency:
lifecycle, scheduling, text, items, PDC. Everything else is a separate module.

---

## 1. Lifecycle

```java
public final class AuctionPlugin extends JavaPlugin {
    private SynthetixLib lib;

    @Override public void onEnable() {
        lib = SynthetixLib.init(this);
        lib.scheduler().global().runTimer(20, 20, task -> tickAuctions());
    }

    @Override public void onDisable() {
        lib.close();            // cancels every task this instance scheduled; idempotent
    }
}
```

- `SynthetixLib.init(JavaPlugin)` → new instance. No static instance anywhere (ADR 0004).
- Instance exposes services: `scheduler()`, `text()`, `items()`, `keys()`, `plugin()`.
  Services are created eagerly in `init`, are thread-safe, and become unusable after `close()`
  (scheduling after close throws `IllegalStateException`).
- `lib.attach(AutoCloseable)` ties a module service (e.g. `Configs`) to the lib: on `close()` attached
  resources are closed first, in reverse registration order (they may still need the scheduler to flush),
  then the scheduler. A failure closing one resource is logged and does not stop the others;
  `attach` after close throws `IllegalStateException`.
- **Bootstrap hook: not in core yet.** The first module that needs `PluginBootstrap`
  (command or dialog) designs it. Core stays bootstrap-free.

## 2. Scheduler — `core.scheduler`

Thin facade over Paper's region-aware schedulers (`GlobalRegionScheduler`, `RegionScheduler`,
`EntityScheduler`, `AsyncScheduler`). Those exist on plain Paper too, so **one implementation works on
Paper and Folia**; the legacy `BukkitScheduler` is never used.

```java
Scheduler s = lib.scheduler();

// Global region (world-independent game logic: timers, broadcasts)
s.global().run(() -> broadcast());
s.global().runLater(40, () -> ...);                       // ticks
SynthetixTask t = s.global().runTimer(0, 20, task -> {    // delay 0 allowed (facade handles it)
    if (done) task.cancel();
});

// Region owning a location / chunk (blocks, world changes)
s.region(location).run(() -> block.setType(Material.GOLD_BLOCK));
s.region(world, chunkX, chunkZ).runLater(10, () -> ...);

// Entity (follows the entity across regions; "retired" runs if the entity is removed first)
s.entity(player).run(() -> player.sendMessage(msg));
s.entity(npc).run(() -> walk(), () -> log("npc gone"));

// Async (IO, DB, HTTP) — real time, not ticks
s.async().run(() -> saveToDb());
s.async().runLater(Duration.ofSeconds(5), () -> ...);
s.async().runTimer(Duration.ZERO, Duration.ofMinutes(1), task -> autosave());

// Thread checks
s.isGlobalThread();  s.owns(location);  s.owns(entity);
```

Types
- `Scheduler` — `global()`, `region(Location)`, `region(World,int,int)`, `entity(Entity)`, `async()`,
  `isGlobalThread()`, `owns(Location)`, `owns(Entity)`.
- `TickScheduler` (global/region/entity share it) — `run(Runnable)`, `runLater(long ticks, Runnable)`,
  `runTimer(long delayTicks, long periodTicks, Consumer<SynthetixTask>)`.
  Entity variant adds overloads with `Runnable retired`.
- `AsyncScheduler` — same three methods with `Duration`.
- `SynthetixTask` — `cancel()`, `isCancelled()`, `isDone()`.

Behaviour
- Delays: Paper requires ≥ 1 tick for delayed/fixed-rate; facade maps `0` → immediate/1 as needed so
  callers can pass 0. Negative → `IllegalArgumentException`. Period must be ≥ 1.
- Entity scheduling on an already-removed entity: returns a task with `isCancelled() == true` and runs
  `retired` (if given) — never returns null.
- Every task is tracked by the instance; `lib.close()` cancels all still-pending ones. Finished tasks
  are untracked (no leak).
- Exceptions inside a task are caught and logged via the plugin logger with the task's origin; a
  repeating task keeps running.
- Only `core.scheduler` may touch `org.bukkit.scheduler..` or `io.papermc.paper.threadedregions..`
  (ArchUnit).

## 3. Text — `core.text`

MiniMessage only. We **reuse Adventure types** (`TagResolver`, `Placeholder`) instead of inventing ours.

```java
TextService text = lib.text();

Component c = text.parse("<gold>Lot <id> sold to <player>",
        Placeholder.unparsed("id", "12"),
        Placeholder.component("player", player.name()));

text.send(player, "<green>Bid placed!");                      // any Audience
text.send(Bukkit.getServer(), "<yellow>Auction starts in <s>s", Placeholder.unparsed("s", "10"));

// Derived service with extra tags (immutable: returns a new TextService)
TextService auctionText = text.with(Placeholder.parsed("prefix", "<dark_gray>[<gold>Auction</gold>]</dark_gray>"));
auctionText.send(player, "<prefix> <gray>You were outbid");

String plain = text.plain(component);                         // for logs / DB
String safe  = text.escape(userInput);                        // user input never parsed as tags
```

- `TextService` is immutable and thread-safe. `with(TagResolver...)` returns a new instance.
- `parse` never throws on bad markup (MiniMessage lenient); unknown tags render literally.
- `parseItem(...)` = `parse(...)` with `italic=false` as default decoration (used by items).
- Player-provided strings must go through `Placeholder.unparsed` or `escape` — documented loudly.

## 4. Items — `core.item`

Mutable builder, **`build()` returns a fresh `ItemStack` every call** (builder reusable as a template).
Uses `ItemMeta` for stable features (`item_model`, name, lore, glint, tooltip) — no int custom model data.

```java
ItemStack ticket = lib.items().of(Material.PAPER)
        .name("<gold>Auction ticket")                         // MiniMessage, non-italic
        .lore("<gray>Lot #<id>", Placeholder.unparsed("id", "12"))
        .lore("<dark_gray>Right-click to view")               // appends a line
        .itemModel(new NamespacedKey(plugin, "auction_ticket"))
        .amount(1)
        .glint(true)
        .hideTooltip(false)
        .set(LOT_ID, "12")                                     // PDC via typed key (§5)
        .meta(meta -> meta.setMaxStackSize(1))                // escape hatch
        .build();

ItemStack edited = lib.items().from(existing)                 // copies, never mutates the input
        .clearLore()
        .lore("<red>Expired")
        .build();
```

- Builder is **not** thread-safe; the produced `ItemStack` is independent.
- `name` → custom name with italic off; `itemName(...)` sets the base item name.
- `lore(String, TagResolver...)` appends; `lore(List<Component>)` replaces; `clearLore()`.
- `meta(Consumer<ItemMeta>)` and `edit(Consumer<ItemStack>)` escape hatches for anything not covered
  (e.g. experimental data-component API) — keeps the builder small.

## 5. PDC — `core.pdc`

Typed keys, automatically namespaced to the consumer plugin.

```java
// usually created once in onEnable and kept in a field
PdcKey<String>  LOT_ID   = lib.keys().string("lot_id");
PdcKey<Integer> BIDS     = lib.keys().integer("bids");
PdcKey<UUID>    SELLER   = lib.keys().uuid("seller");
PdcKey<Boolean> LOCKED   = lib.keys().bool("locked");
PdcKey<long[]>  CUSTOM   = lib.keys().of("history", PersistentDataType.LONG_ARRAY);

Optional<String> lot = LOT_ID.get(itemStack);                 // read-only view, no copy
LOT_ID.set(itemStack, "13");                                   // edits the stack in place
SELLER.set(entity, player.getUniqueId());                      // any PersistentDataHolder
boolean has = LOCKED.has(chest);                               // TileState is a PersistentDataHolder (call chest.update() after set)
BIDS.remove(itemStack);
```

- Factory methods: `string, integer, longKey, doubleKey, bool, uuid, bytes, of(name, PersistentDataType)`.
- Key names validated (`[a-z0-9/._-]`), `IllegalArgumentException` otherwise.
- `uuid` stored as 16-byte array (custom `PersistentDataType`).
- ItemStack reads use `getPersistentDataContainer()` (view), writes use `editPersistentDataContainer`.

## 6. Not in core
Config/locale (config), commands (command), GUIs (gui), database + PluginLoader helper (database),
dialogs (dialog), NPCs (npc), event-listener helpers (decide later), player metadata maps (never — use PDC
or per-module state).

## 7. Testing plan
| Part | Unit tests (no server) | Self-tests (smokeTest, 1.21.11 + 26.x) |
|---|---|---|
| Scheduler | tick/duration validation, delay mapping | global/region/entity/async run, runLater, runTimer+cancel, entity retired, close() cancels pending, exception in task logged and timer continues |
| Text | parse, placeholders, escape, plain, with() immutability, italic default | send to console |
| Items | — (needs server registry) | build name/lore/model/glint, from() does not mutate input, build() returns distinct stacks |
| PDC | key name validation, uuid type round-trip | round-trip every type on ItemStack and an entity |

Async self-tests require the runner upgrade in P2-01.

## 8. Decisions for owner (defaults chosen, change if you disagree)
1. Scheduler style: `lib.scheduler().region(loc).run(...)` (chosen) vs flat `lib.runAt(loc, ...)`.
2. Sync delays in **ticks**, async delays in **Duration** (chosen, mirrors Paper) vs Duration everywhere.
3. ItemBuilder **mutable, build() copies** (chosen) vs immutable builder.
4. Placeholders: **reuse Adventure `Placeholder`/`TagResolver`** (chosen) vs own wrapper.
5. Bootstrap hook deferred to command/dialog module design (chosen).
