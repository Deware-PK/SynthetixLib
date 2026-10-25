# core

`synthetixlib-core` (since 0.1.0). Lifecycle, Folia-safe scheduler, MiniMessage text, `ItemBuilder`, PDC keys. Depends
only on `paper-api`. Every other module builds on it. Design: `docs/design/core.md`.

## When to use / when not

- Use it in **every** plugin: `SynthetixLib.init(this)` is the entry point and owns the scheduler and the lifecycle.
- Schedule through `lib.scheduler()`, never `Bukkit.getScheduler()` (breaks on Folia).
- Build items with `lib.items()`, store item/entity data in PDC with `lib.keys()`, send text with `lib.text()`.
- Not in core: config and language files (`config`), commands (`command`), menus (`gui`), dialogs (`dialog`), SQL
  (`database`). Do not keep per-player data in static maps; use PDC or per-module state.

## Minimal example

```java snippet=hello-init
lib = SynthetixLib.init(this);
```

```java snippet=hello-close
if (lib != null) {
    lib.close();
}
```

`close()` cancels every task this instance scheduled, closes attached module services (reverse order) and is
idempotent. Scheduling after `close()` throws `IllegalStateException`. The plugin class stays a normal `JavaPlugin`;
there is no base class and no static instance, so keep `lib` in a field and pass it (or the services) to your classes.

## API table

| Type | For | Since |
|---|---|---|
| `SynthetixLib` | `init(JavaPlugin)`, `close()`, `isClosed()`, `scheduler()`, `text()`, `items()`, `keys()`, `plugin()`, `attach(AutoCloseable)` | 0.1.0 |
| `Scheduler` | `global()`, `region(Location)`, `region(World, chunkX, chunkZ)`, `entity(Entity)`, `async()`, `isGlobalThread()`, `owns(Location)`, `owns(Entity)` | 0.1.0 |
| `TickScheduler` | `run`, `runLater(ticks, r)`, `runTimer(delay, period, Consumer<SynthetixTask>)` for global and region | 0.1.0 |
| `EntityTickScheduler` | `TickScheduler` plus `run(r, retired)` and `runLater(ticks, r, retired)`; `retired` runs if the entity is gone | 0.1.0 |
| `AsyncScheduler` | `run`, `runLater(Duration, r)`, `runTimer(Duration, Duration, Consumer<SynthetixTask>)` (real time) | 0.1.0 |
| `SynthetixTask` | `cancel()`, `isCancelled()`, `isDone()` | 0.1.0 |
| `TextService` | `parse`, `parseItem`, `send(Audience, ...)`, `with(TagResolver...)`, `hasTag`, `plain`, `escape` | 0.1.0 |
| `Items` / `ItemBuilder` | `of(Material)`, `from(ItemStack)`; `name`, `itemName`, `lore`, `clearLore`, `itemModel`, `amount`, `glint`, `hideTooltip`, `set(PdcKey, v)`, `meta`, `edit`, `build` | 0.1.0 |
| `PdcKeys` / `PdcKey<T>` | `string`, `integer`, `longKey`, `doubleKey`, `bool`, `uuid`, `bytes`, `of(name, type)`; `get`, `has`, `set`, `remove` on any holder or `ItemStack`; `key()` | 0.1.0 |
| `ScreenOpener` | `boolean open(Player, String id)`: how a menu opens a dialog and back (`gui.link("dialog", dialogs.opener())`) | 0.6.0 |

`attach(AutoCloseable)` is for module services (`Configs`, `CommandService`, ... attach themselves); you rarely call it.

## Scheduler

```java snippet=core-scheduler
final Scheduler scheduler = lib.scheduler();

scheduler.global().runLater(40, () -> lib.plugin().getLogger().info("two seconds later"));
final SynthetixTask timer = scheduler.global().runTimer(0, 20, task -> {
    if (lib.isClosed()) {
        task.cancel();
    }
});

scheduler.region(location).run(() -> block.setType(Material.GOLD_BLOCK));
scheduler.entity(player).run(() -> player.sendMessage("hi"), () -> lib.plugin().getLogger().info("player left"));

scheduler.async().run(() -> lib.plugin().getLogger().info("off the server thread"));
scheduler.async().runTimer(Duration.ZERO, Duration.ofMinutes(1), task -> lib.plugin().getLogger().info("autosave"));
```

- Delays: sync schedulers use **ticks**, `async()` uses `Duration`. `0` is allowed (the facade maps it); negative or a
  period below 1 throws `IllegalArgumentException`.
- Pick the scheduler by what you touch: **global** for world-independent logic, **region** for a block or location,
  **entity** for a player or mob (follows it across regions), **async** for IO, database, HTTP.
- An exception in a task is logged with where it was scheduled; a timer keeps running.
- An entity removed before the task runs returns a task with `isCancelled() == true` and runs `retired`.
- `isGlobalThread()` / `owns(...)` answer "am I allowed to touch this here" (use them in checks, not as a way to skip
  the scheduler).

## Text

```java snippet=core-text
final TextService text = lib.text();

text.send(player, "<green>Bid of <amount> on <item>",
        Placeholder.unparsed("amount", "100"),
        Placeholder.component("item", itemName));

final TextService prefixed = text.with(Placeholder.parsed("prefix", "<dark_gray>[<gold>Shop</gold>]"));
prefixed.send(player, "<prefix> <gray>Welcome");

final String safe = text.escape(userInput);
text.send(player, "<yellow>You typed: " + safe);
text.send(player, "<yellow>You typed: <input>", Placeholder.unparsed("input", userInput));
```

- MiniMessage only. `parse` never throws on bad markup (unknown tags render literally).
- `TextService` is immutable and thread-safe; `with(resolvers)` returns a new one (a plugin prefix tag).
- **Player-provided text** goes through `Placeholder.unparsed` or `escape`, never concatenated raw into a MiniMessage
  string, or the player can inject tags (`<click:run_command:...>`).
- Player-visible texts of a product belong in the language files (see `config.md`), not in code.

## Items

```java snippet=core-items
final ItemBuilder ticket = lib.items().of(Material.PAPER)
        .name("<gold>Auction ticket")
        .lore("<gray>Lot #<id>", Placeholder.unparsed("id", "12"))
        .itemModel(new NamespacedKey(lib.plugin(), "auction_ticket"))
        .glint(true)
        .set(lotId, "12");
final ItemStack first = ticket.build();
final ItemStack second = ticket.amount(2).build();      // build() returns a fresh stack every time

final ItemStack expired = lib.items().from(existing).clearLore().lore("<red>Expired").build();
```

- `name(...)` is the custom name with italic off; `itemName(...)` is the base item name; `lore(String)` appends a line,
  `lore(List<Component>)` replaces.
- The builder is mutable and **not** thread-safe; `build()` returns an independent stack every call.
- `item_model` instead of int custom model data. Anything not covered: `meta(Consumer<ItemMeta>)` /
  `edit(Consumer<ItemStack>)`.

## PDC

```java snippet=core-pdc
final PdcKey<String> lotId = lib.keys().string("lot_id");
final PdcKey<Integer> bids = lib.keys().integer("bids");

lotId.set(stack, "13");
final Optional<String> lot = lotId.get(stack);
final boolean tagged = lotId.has(stack);
bids.set(player, 3);                                    // any PersistentDataHolder
lotId.remove(stack);
```

- Keys are namespaced to your plugin automatically. Names match `[a-z0-9/._-]+`.
- Create keys once (a field), not per call. `uuid` is stored as 16 bytes.
- On an empty stack (`AIR`) `set`/`remove` throw `IllegalArgumentException`. For a tile entity call `state.update()` after
  `set`.

## Threads

`SynthetixLib` services are thread-safe. Bukkit API calls are not: touch a player on `entity(player)`, a block on
`region(location)`, global state on `global()`, and do IO on `async()`. Come back to the server thread before you use
the Bukkit API with results from async work.

## Pitfalls

- Wrong: `Bukkit.getScheduler().runTaskLater(plugin, r, 20)` -> right: `lib.scheduler().global().runLater(20, r)`
  (rule `threads-no-bukkit-scheduler`).
- Wrong: `static SynthetixLib LIB;` or a static `Map<UUID, ...>` -> right: fields on your instance (rules
  `core-no-static-state`, `state-no-static-maps`).
- Wrong: `lib.text().parse("<red>" + playerName)` -> right: `Placeholder.unparsed("name", playerName)` (rule
  `text-escape-player-input`).
- Wrong: forgetting `lib.close()` -> tasks and module services of a reloaded plugin keep running (rule
  `core-close-in-ondisable`).
- Wrong: a consumer package starting with `com.github.dewarepk.synthetixlib` -> relocation rewrites it (see `setup.md`).

## Limits

- Paper 1.21.11 API only, Java 21; runs on 26.x. No NMS, no reflection, no `ChatColor` / `§`.
- Scheduling after `close()` throws; schedule from `onEnable` onward.
- Paper reads item data through item meta here; experimental data-component API goes through `meta` / `edit`.

Design: `docs/design/core.md` (no deviations).
