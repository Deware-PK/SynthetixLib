# command

`synthetixlib-command` (since 0.3.0). Native Brigadier commands behind one fluent API: typed arguments validated by the
client, permissions, cooldowns, async handlers, messages from your language files. Depends on core and config.
Design: `docs/design/command.md` (read its section 10, "Deviations").

## When to use / when not

- Use it for every command. One root spec composes subcommand classes; each subcommand is one file.
- Not `plugin.yml` commands, not `BasicCommand`, not `CommandMap`. Brigadier tree is built on Paper's `COMMANDS` event.
- A command that needs text input from the player: open a **dialog**, not an anvil (see `dialog.md`).

## Minimal example

The root only lists its parts:

```java snippet=command-root
return CommandSpec.literal("example")
        .aliases("ex")
        .description("SynthetixLib example plugin")
        .sub(new AirdropCommand(airdrops, claims, lang))
        .sub(new DialogCommand(dialogs))
        .sub(new DbCommand(database, settings, lang))
        .sub(new GiveCommand(settings, lang))
        .sub(new GiveResetCommand(cooldowns, lang))
        .sub(new MenuCommand(menus))
        .sub(new ReloadCommand(settings, airdrops, lang))
        .helpSub()
        .build();
```

Registration (in `onEnable`, **before** the first `COMMANDS` event; a later `register` throws `IllegalStateException`):

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

`register` only stores the immutable specs; the service rebuilds the nodes every time Paper fires the event (server start
and `/minecraft:reload`). After `lib.close()` the command replies nothing.

## One class per subcommand

```java snippet=command-give
final class GiveCommand implements SubCommand {

    private final ConfigHandle<Settings> settings;
    private final LocaleHandle<Messages> lang;
    private final Arg<Player> target = Arg.of("player", Args.player());
    private final Arg<Material> material = Arg.of("material", Args.material());
    private final Arg<Integer> amount = Arg.of("amount", Args.integer(1, 64));

    GiveCommand(final ConfigHandle<Settings> settings, final LocaleHandle<Messages> lang) {
        this.settings = settings;
        this.lang = lang;
    }

    @Override
    public SubSpec spec() {
        return SubSpec.literal("give")
                .permission("example.give")
                .allowConsole()
                .argument(target).argument(material).optional(amount, 1)
                .cooldown(sender -> settings.get().giveWait(), "example.admin")   // read per run: a reload applies
                .usage("<player> <material> [amount]").description("Give items to a player")
                .executes(this::run);
    }

    private void run(final CommandCall ctx) {
        final Player player = ctx.get(target);
        final Material type = ctx.get(material);
        final int count = ctx.get(amount);
        player.getInventory().addItem(new ItemStack(type, count));
        ctx.reply(lang, Messages::gave,
                Placeholder.unparsed("amount", String.valueOf(count)),
                Placeholder.unparsed("material", type.key().value()),
                Placeholder.unparsed("player", player.getName()));
    }
}
```

Shape:

- `SubCommand` is `SubSpec spec()`, called once at `register`. Dependencies are fields; `Arg<T>` handles are fields.
- `CommandSpec.literal(name)` (root: `aliases`, `description`, `build()`) and `SubSpec.literal(name)` share the builder
  `SpecBuilder`: `argument(Arg)`, `optional(Arg, default)`, `sub(SubCommand | SubSpec)`, `executes`, `executesAsync`,
  `permission`, `playerOnly`, `allowConsole`, `cooldown`, `usage`, `description`, `helpSub`.
- Names (literals and arguments) match `[a-z0-9_-]+`; a duplicate child throws at `build()`.
- **Modifiers are inherited by children**: `permission`, `playerOnly` / `allowConsole`, `cooldown`. A child may add a
  permission but never loosen its parent's.
- A group is just a `SubCommand` with children and no handler:

```java snippet=command-group
@Override
public SubSpec spec() {
    return SubSpec.literal("airdrop")
            .permission("example.admin")
            .allowConsole()
            .description("Manage airdrops")
            .sub(new AirdropCreateCommand(airdrops, lang))
            .sub(new AirdropListCommand(airdrops, lang))
            .sub(new AirdropDeleteCommand(airdrops, lang))
            .sub(new AirdropClaimCommand(airdrops, claims, lang))
            .sub(new AirdropStatsCommand(airdrops, claims, lang));
}
```

- One-liners may be inline: `.sub(SubSpec.literal("reload").executes(ctx -> ...))`.
- `helpSub()` adds `/<cmd> help` (and shows the branch's help for a handler-less group). A subcommand named `help` next to it
  is rejected at `build()`. Help lines come from `usage` + `description` of each node.

## Arguments

`Arg.of(name, type)` is a typed handle; read it with `ctx.get(arg)`. Every type maps to a native Brigadier type, so the
client validates and colours the input.

| `Args.` | Java type | Notes |
|---|---|---|
| `word()`, `string()`, `greedy()` | `String` | `greedy` only as the last argument |
| `integer(min,max)`, `longRange(min,max)`, `decimal(min,max)` | `int`, `long`, `double` | bounds checked client-side |
| `bool()` | `boolean` | |
| `player()`, `players()` | `Player`, `List<Player>` | selectors resolved when read; none found -> `playerNotFound` |
| `offlinePlayer()` | `OfflinePlayer` | cached players only, never a blocking lookup |
| `world()` | `World` | |
| `blockPosition()`, `position()` | `Location` | `~` resolved against the source |
| `itemStack()` | `ItemStack` | |
| `registry(RegistryKey.X)` | e.g. `Enchantment`, `Sound` | |
| `material()` | `Material` | |
| `enumOf(Class<E>)` | `E` | kebab/lower-case input |
| `duration()` | `Duration` | same syntax as config (`30s`, `1h30m`) |
| `uuid()`, `key()` | `UUID`, `NamespacedKey` | |
| `oneOf(Supplier<Collection<String>>)` | `String` | must be in the set at execution time |
| `custom(nativeType, parse[, suggest])` | `T` | for your own types |

- `Arg.suggests(source -> collection)` sets suggestions of a word/string argument (case-insensitive filter). Suggestion
  functions run on Paper's suggestion thread: read only thread-safe snapshots (`collection.ids()`).
- `optional(arg, fallback)` builds the node with and without the argument (Brigadier has none); read it with
  `ctx.get(arg)` (the fallback is returned) or `ctx.optional(arg)` / `ctx.has(arg)`.
- Parse failures of our converters become red client-side errors from `CommandMessages.invalidValue`.

## CommandCall (the `ctx`)

An interface (mockable in your unit tests), one instance per execution, never shared.

| Group | Methods |
|---|---|
| who | `sender()`, `isPlayer()`, `isConsole()`, `player()` (throws if not a player), `playerIfPresent()`, `hasPermission(p)`, `locale()` |
| where | `source()`, `executor()` (`/execute as`), `location()` |
| input | `get(arg)`, `get(name, Class)`, `optional(arg)`, `getOrDefault(arg, fallback)`, `has(arg)`, `label()` (alias actually typed), `input()` |
| answer | `reply(String \| Component)`, `reply(lang, Messages::x, resolvers...)`, `replyLines(lang, Messages::lines, ...)`, `actionBar(...)` |
| fail | `throw ctx.fail("<red>...")` or `throw ctx.fail(lang, Messages::x, resolvers...)` |
| cooldown | `cooldownLeft()`, `resetCooldown()`, `skipCooldown()` |
| threads | `isAsync()`, `runOnSender(Runnable)`, `lib()` |

`<label>` and `<sender>` are built into every reply. `throw ctx.fail(...)` sends the message, does not start the cooldown
and makes Brigadier return 0; `CommandFailure` is unchecked. Any other exception is logged with `input()` and the sender
gets `CommandMessages.error`.

```java snippet=command-fail
private void run(final CommandCall ctx) {
    final String value = ctx.get(id);
    try {
        airdrops.create(value);
    } catch (final IllegalArgumentException invalid) {
        throw ctx.fail(lang, Messages::airdropInvalidId, Placeholder.unparsed("id", value));
    } catch (final IllegalStateException exists) {
        throw ctx.fail(lang, Messages::airdropExists, Placeholder.unparsed("id", value));
    }
    ctx.reply(lang, Messages::airdropCreated, Placeholder.unparsed("id", value));
}
```

## Async handlers and the thread

```java snippet=command-async
@Override
public SubSpec spec() {
    return SubSpec.literal("ban").argument(target).optional(time, Duration.ofDays(1))
            .usage("<player> [time]").description("Ban a player")
            .executesAsync(this::run);
}

private void run(final CommandCall ctx) {
    final Player player = ctx.get(target);
    final Duration length = ctx.getOrDefault(time, Duration.ofDays(1));
    storeBan(player, length);                                     // blocking is fine here
    ctx.runOnSender(() -> ctx.reply(lang, Messages::reloaded, Placeholder.component("player", player.name())));
}
```

```java snippet=command-callback
private void run(final CommandCall ctx) {
    final String value = ctx.get(id);
    if (!airdrops.ids().contains(value)) {
        throw ctx.fail(lang, Messages::airdropNotFound, Placeholder.unparsed("id", value));
    }
    final UUID player = ctx.player().getUniqueId();
    claims.claim(value, player).whenComplete((count, failure) -> ctx.runOnSender(() -> {
        if (failure != null) {
            ctx.lib().plugin().getLogger().warning("claim failed: " + failure);
            ctx.reply(lang, Messages::dbFailed);
        } else {
            ctx.reply(lang, Messages::airdropClaimed,
                    Placeholder.unparsed("id", value), Placeholder.unparsed("count", String.valueOf(count)));
        }
    }));
}
```

- `executes` runs on the thread Paper executes the command on: the player's region thread on Folia, the main thread on
  Paper, the global thread for the console. Touch the player there; other entities and blocks via `lib.scheduler()`.
- `executesAsync` runs on `lib.scheduler().async()` with the arguments already parsed (blocking IO is fine). Use
  `ctx.runOnSender(...)` to come back (entity scheduler for a player, global otherwise). It returns success to Brigadier
  as soon as the handler is queued, and its cooldown starts when the handler returns normally.
- **One async run per player and node at a time**: a second `/cmd` while the first runs gets `CommandMessages.busy`.
  Quitting does not drop the reservation; only the handler finishing does.
- `reply` and `fail` are safe from any thread.

## Permissions, player-only, cooldowns

- `permission(p)` hides the node from tab-complete for players without it (the tree is sent on join / `updateCommands()`).
- `playerOnly()` keeps the node visible to the console but answers `CommandMessages.playerOnly`; `allowConsole()` overrides
  an inherited `playerOnly`.
- `cooldown(Duration)` / `cooldown(Duration, bypassPermission)`: a fixed length. `cooldown(sender -> Duration)` /
  `cooldown(fn, bypassPermission)`: the length is **asked at every successful run**, so a value read from a reloadable
  config follows the reload; `Duration.ZERO` means no cooldown this time. Per player **per executable node**, in memory,
  cleared on quit and on close. The console and command blocks have no cooldown. A restart clears cooldowns; persist them
  yourself if needed.
- **`throw ctx.fail(...)` never starts the cooldown** (nor does any exception): a refused run costs the player nothing.
  Use `skipCooldown()` for a run that succeeds but should not cost, `resetCooldown()` to refund the sender.

```java snippet=command-cooldown-reload
SubSpec kit(final ConfigHandle<KitSettings> settings) {
    return SubSpec.literal("kit")
            .cooldown(sender -> settings.get().kitWait(), "kit.bypass")   // asked at every successful run
            .executes(ctx -> ctx.reply("<green>Here is your kit"));
}
```

Reading or clearing another player's cooldown (an admin command): `commandService.cooldowns()` returns
`CommandCooldowns` with `remaining(Player | UUID, commandPath)`, `reset(UUID, commandPath)` and `reset(UUID)` (every
command). `commandPath` is the command **as written in code**, space separated from the root name (`"kit"`,
`"kit reset"`); an alias typed by the player is never part of it. Thread-safe.

```java snippet=command-reset-cooldown
final class GiveResetCommand implements SubCommand {

    private final CommandCooldowns cooldowns;
    private final LocaleHandle<Messages> lang;
    private final Arg<Player> target = Arg.of("player", Args.player());

    GiveResetCommand(final CommandCooldowns cooldowns, final LocaleHandle<Messages> lang) {
        this.cooldowns = cooldowns;
        this.lang = lang;
    }

    @Override
    public SubSpec spec() {
        return SubSpec.literal("give-reset")
                .permission("example.admin")
                .allowConsole()
                .argument(target)
                .usage("<player>").description("Let a player use /example give again")
                .executes(this::run);
    }

    private void run(final CommandCall ctx) {
        final Player player = ctx.get(target);
        cooldowns.reset(player.getUniqueId(), "example give");        // the path as written in code, not an alias
        ctx.reply(lang, Messages::giveReset, Placeholder.unparsed("player", player.getName()));
    }
}
```

`<time>` in the cooldown message is written by `TimeFormat` (config module) with the unit words of
`CommandMessages.timeUnits` (`timeUnits: {d: d, h: h, m: m, s: s}` in the lang file): `59s`, `1m 12s`, `1h 0m`, `2d 3h`.
Use `TimeFormat.of(units).format(duration)` for your own messages (an instance, not static).

Register it as a listener, call `tryUse` in the handler and `throw ctx.fail(...)` with the seconds left. Planned (task
P2-35): reloadable length, per-player reset and a time formatter in the module.

## Messages

Put `CommandMessages` as a record component of your `Messages` and pass it with `.messages(lang, Messages::commands)`:

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

`CommandMessages` fields: `playerOnly`, `cooldown` (`<time>`), `busy`, `playerNotFound` (`<input>`), `invalidValue`
(`<input>`, `<reason>`, no `<label>`), `error`, `helpHeader` (`<command>`), `helpLine` (`<command>`, `<usage>`,
`<description>`), `disabled` (empty = silent). `CommandMessages.defaults()` is English. Without `.messages(...)` the
English defaults are used for the module's own messages. Your own handler messages use `ctx.reply(lang, ...)`.

## Reloading a command

`/yourcommand reload` calls `handle.reload()` / `collection.reload()` / `lang.reload()`; see the `config-reload` snippet in
`config.md`. Commands themselves are not re-registered at runtime (Paper has no runtime registration).

## API table

| Type | For | Since |
|---|---|---|
| `CommandService` | `create(lib)`, `register(CommandSpec...)`, `messages(lang, fn)`, `isClosed()`, `close()` | 0.3.0 |
| `CommandSpec` / `CommandSpec.Builder` | root: `literal`, `aliases`, `description`, `build` | 0.3.0 |
| `SubSpec` | `literal(name)` subcommand builder | 0.3.0 |
| `SpecBuilder<B>` | shared builder methods (see above); `helpSub` since 0.3.0 | 0.3.0 |
| `SubCommand` | `SubSpec spec()`, one class per subcommand | 0.3.0 |
| `Arg<T>` | `of(name, type)`, `suggests`, `name`, `type` | 0.3.0 |
| `ArgType<T>` / `Args` | typed arguments (`nativeType()` + `read(...)` for your own types) | 0.3.0 |
| `CommandCall` | execution context | 0.3.0 |
| `CommandHandler` | `void handle(CommandCall)`, what `executes`/`executesAsync` take | 0.3.0 |
| `CommandFailure` | thrown by `ctx.fail(...)`, `message()` | 0.3.0 |
| `CommandMessages` | the module's own messages as a record | 0.3.0 |

There is no `CommandService.refresh(player)`; to re-send the tree after a permission change use Paper's
`player.updateCommands()`.

## Pitfalls

- Wrong: a shared field written by a handler (counter, "current target") -> right: per-call data lives in `ctx`, shared state
  in a thread-safe service (rule `command-no-shared-state`).
- Wrong: `"/" + spec.name()` in a message -> right: the `<label>` placeholder, which is the alias actually typed (rule
  `command-report-alias-used`).
- Wrong: `sender.sendMessage("<red>No money")` -> right: `throw ctx.fail(lang, Messages::notEnoughMoney)` (rule
  `command-messages-through-locale`).
- Wrong: parsing `args[0]` yourself or `ctx.get("amount", Long.class)` everywhere -> right: an `Arg` field with a bounded
  `Args` type (rule `command-use-brigadier`).
- Wrong: DB or HTTP inside `executes` -> right: `executesAsync` + `ctx.runOnSender` (rule `threads-async-then-back`).
- Wrong: `register(...)` after startup, or from an event handler -> right: in `onEnable` (rule
  `command-register-in-onenable`).
- Wrong: using `ctx.player()` in a node that allows the console -> right: `playerIfPresent()` or leave `playerOnly()` on.

## Limits

- Register only before the first `COMMANDS` event; no runtime (un)registration.
- Cooldowns are in memory only. Console and command blocks have no cooldown.
- `greedy()` only as the last argument; Minecraft does not allow spaces in `word()`.

Design: `docs/design/command.md`.
