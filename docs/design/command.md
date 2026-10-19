# synthetixlib-command — API design

Status: **Implemented (0.3.0)** — approved 2026-10-17 (all §9 defaults accepted, 3 and 10 chosen by owner) · Task: P1-02 · Date: 2026-10-17
Package root: `com.github.dewarepk.synthetixlib.command` · Depends on: core, config (locale), paper-api only

Native Brigadier commands (ADR 0006) behind one fluent API: typed arguments with client-side validation and
suggestions, permissions, player-only, cooldowns, locale-driven messages. Fixes SnapPlug bug #7
(shared mutable state, wrong alias in messages, hard-coded messages).

---

## 1. Registering

```java
public final class AuctionPlugin extends JavaPlugin {
    @Override public void onEnable() {
        SynthetixLib lib = SynthetixLib.init(this);
        Configs configs = Configs.create(lib);
        LocaleHandle<Messages> lang = configs.locale("lang", Messages.class, Messages::defaults).load();

        CommandService commands = CommandService.create(lib)      // attaches to lib (closed with it)
                .messages(lang, Messages::commands);              // §6: built-in messages come from your lang files
        commands.register(AuctionCommand.build(auctions, gui, lang));   // §2
    }
}
```

- `CommandService.create(lib)` registers **one** handler for Paper's `LifecycleEvents.COMMANDS` on the plugin's
  lifecycle manager. `register(CommandSpec...)` only stores the (immutable) specs; the handler turns them into
  Brigadier nodes every time Paper fires the event (server start **and** `/minecraft:reload`), so registration is
  idempotent.
- `register` after the first COMMANDS event → `IllegalStateException` ("register commands in onEnable"). Paper has
  no runtime registration; we do not fake one.
- No `plugin.yml` commands, no `BasicCommand`, no `CommandMap` access.
- After `lib.close()` (plugin disabled, commands still in the tree until the next reload) every execution replies
  nothing and returns 0; suggestions return empty.

## 2. Defining a command — one class per subcommand

A command is a small root spec that **composes** subcommand classes. Each subcommand is one file: its arguments,
modifiers and handler live together, so a product with 15 subcommands has 15 short classes, not one deep chain.

```java
// AuctionCommand.java — the root only lists its parts
final class AuctionCommand {
    static CommandSpec build(Auctions auctions, AuctionGui gui, LocaleHandle<Messages> lang) {
        return CommandSpec.literal("auction")
                .aliases("ah", "auc")
                .description("Auction house")
                .permission("auction.use")
                .playerOnly()
                .executes(ctx -> gui.open(ctx.player()))                 // /auction
                .sub(new BidCommand(auctions, lang))
                .sub(new SellCommand(auctions, lang))
                .sub(new AdminCommand(auctions, lang))
                .build();
    }
}

// BidCommand.java — /auction bid <amount>
final class BidCommand implements SubCommand {
    private final Auctions auctions;
    private final LocaleHandle<Messages> lang;
    private final Arg<Long> amount = Arg.of("amount", Args.longRange(1, 1_000_000_000L));

    BidCommand(final Auctions auctions, final LocaleHandle<Messages> lang) {
        this.auctions = auctions;
        this.lang = lang;
    }

    @Override public SubSpec spec() {
        return SubSpec.literal("bid")
                .argument(amount)
                .cooldown(Duration.ofSeconds(2))
                .usage("<amount>").description("Bid on the current lot")
                .executes(this::run);
    }

    private void run(final CommandCall ctx) {
        final long value = ctx.get(amount);
        if (!auctions.canAfford(ctx.player(), value)) {
            throw ctx.fail(lang, Messages::notEnoughMoney, Placeholder.unparsed("amount", Long.toString(value)));
        }
        auctions.bid(ctx.player(), value);
        ctx.reply(lang, Messages::bidPlaced, Placeholder.unparsed("amount", Long.toString(value)));
    }
}

// AdminCommand.java — a group is just a SubCommand with children
final class AdminCommand implements SubCommand {
    ...
    @Override public SubSpec spec() {
        return SubSpec.literal("admin")
                .permission("auction.admin")
                .allowConsole()                                    // overrides the root's playerOnly
                .sub(new CancelCommand(auctions, lang))            // /auction admin cancel <lot>
                .sub(new BanCommand(auctions, lang))               // /auction admin ban <player> [time]
                .sub(new ReloadCommand(config, lang));             // /auction admin reload
    }
}

// CancelCommand.java — live suggestions, optional argument in BanCommand
final class CancelCommand implements SubCommand {
    private final Arg<String> lot = Arg.of("lot", Args.word()).suggests(ctx -> auctions.openLotIds());
    @Override public SubSpec spec() {
        return SubSpec.literal("cancel").argument(lot).executes(ctx -> auctions.cancel(ctx.get(lot), ctx.sender()));
    }
}
// BanCommand: .argument(target).optional(time, Duration.ofDays(1)).executesAsync(this::run)
```

Shape
- `SubCommand` = `interface SubCommand { SubSpec spec(); }` — the documented way. `spec()` is called once at
  `register`; the class keeps its dependencies (services, lang) as fields, `Arg` handles as instance fields.
- `CommandSpec.literal(name)` (root) and `SubSpec.literal(name)` share the same builder methods: `.argument(Arg)`,
  `.optional(Arg, default)` (Brigadier has no optional args: the node is built with and without it),
  `.sub(SubCommand)`, `.executes(...)` / `.executesAsync(...)`, modifiers. Only the root has `aliases` /
  `description` for Paper; `usage` / `description` on subs feed the help (§7).
- Tiny one-liners may be inlined with `.sub(SubSpec.literal("reload").executes(...))`; anything with arguments or
  logic should be its own class (style rule in the docs, not enforced).
- Modifiers are **inherited by children** unless overridden: `permission`, `playerOnly` / `allowConsole`,
  `cooldown`.
- Names: literals and argument names `[a-z0-9_-]+`; duplicate child names → `IllegalArgumentException` at `build()`.
- The built `CommandSpec` is immutable and holds no per-call state (bug #7): one registration, executed on any
  region thread at the same time. Per-call data lives only in `CommandCall`.

## 3. Execution context — `CommandCall` (interface)

Every handler receives a `CommandCall`. It is an **interface**, so products can mock it in their own unit tests;
the module creates one implementation per execution and never shares it.

```java
public interface CommandCall {

    // --- who ran it -----------------------------------------------------------------------------------------
    CommandSender sender();              // console, player, command block, ...
    boolean isPlayer();                  // sender is a Player
    boolean isConsole();                 // sender is the server console
    Player player();                     // the player; IllegalStateException if !isPlayer() (never on playerOnly nodes)
    Optional<Player> playerIfPresent();  // for nodes that allow console
    boolean hasPermission(String permission);
    Locale locale();                     // sender's language (player locale, else the lang default)

    // --- where (vanilla /execute aware) ---------------------------------------------------------------------
    CommandSourceStack source();         // Paper source, for advanced use
    @Nullable Entity executor();         // /execute as <entity> ...; usually the player
    Location location();                 // /execute at / positioned ...; else the sender's location or world spawn

    // --- what was typed -------------------------------------------------------------------------------------
    <T> T get(Arg<T> arg);               // required arg, never null
    <T> T get(String name, Class<T> type);
    <T> Optional<T> optional(Arg<T> arg);
    <T> T getOrDefault(Arg<T> arg, T fallback);
    boolean has(Arg<?> arg);
    String label();                      // alias actually typed: "ah" for /ah bid 10 (bug #7)
    String input();                      // full input without '/', e.g. "ah bid 10"

    // --- answering ------------------------------------------------------------------------------------------
    void reply(String miniMessage, TagResolver... resolvers);                  // via lib.text(), <label>/<sender> built in
    void reply(Component message);
    <M> void reply(LocaleHandle<M> lang, Function<M, String> message, TagResolver... resolvers);
    <M> void replyLines(LocaleHandle<M> lang, Function<M, List<String>> lines, TagResolver... resolvers);
    void actionBar(String miniMessage, TagResolver... resolvers);
    CommandFailure fail(String miniMessage, TagResolver... resolvers);        // use as: throw ctx.fail(...)
    <M> CommandFailure fail(LocaleHandle<M> lang, Function<M, String> message, TagResolver... resolvers);

    // --- cooldown of this node ------------------------------------------------------------------------------
    Duration cooldownLeft();             // Duration.ZERO if none / not a player
    void resetCooldown();                // e.g. refund after an external failure
    void skipCooldown();                 // this run succeeds but does not start the cooldown

    // --- threads --------------------------------------------------------------------------------------------
    boolean isAsync();                   // true inside executesAsync
    void runOnSender(Runnable task);     // back to the sender's thread: entity scheduler for a player, global otherwise
    SynthetixLib lib();                  // scheduler(), text(), ... for anything else
}
```

Example
```java
private void run(final CommandCall ctx) {
    if (!ctx.isPlayer()) {
        throw ctx.fail("<red>Use /<label> give <player> from the console");
    }
    final Player player = ctx.player();
    final ItemStack hand = player.getInventory().getItemInMainHand();
    if (hand.isEmpty()) {
        throw ctx.fail(lang, Messages::holdAnItem);                       // message sent, cooldown not used
    }
    final long price = ctx.get(price);
    ctx.reply(lang, Messages::listed, Placeholder.unparsed("price", Long.toString(price)));
}

// async: DB first, then back to the player
private void ban(final CommandCall ctx) {                                 // registered with executesAsync
    final Player target = ctx.get(this.target);
    final Duration time = ctx.getOrDefault(this.time, Duration.ofDays(1));
    database.ban(target.getUniqueId(), time);                             // blocking is fine here
    ctx.runOnSender(() -> ctx.reply(lang, Messages::banned, Placeholder.component("player", target.name())));
}
```

Rules
- `throw ctx.fail(...)` stops the handler: the message is sent, the cooldown is not started, Brigadier gets 0.
  `CommandFailure` is unchecked (no checked exceptions in fluent APIs).
- Any other exception is caught and logged with `input()`; the sender gets `CommandMessages.error`.
- `executesAsync` returns success (1) to Brigadier as soon as the handler is queued, not when it finishes; failures
  and exceptions inside it are reported to the sender like in `executes`.
- `reply` / `fail` are safe from any thread. `player()` and world access follow the thread rules below.
- `<label>` (alias used) and `<sender>` (sender name) are available in every reply and locale message.
- The implementation is per execution and immutable apart from the cooldown flags — no shared state (bug #7).

Threads (Folia)
- `executes` runs on the thread Paper executes the command on: the player's region thread on Folia, the main
  thread on Paper, the global thread for console. Touching the player is safe there; other entities/blocks must go
  through `lib.scheduler()`.
- `executesAsync` runs the handler on `lib.scheduler().async()` with the arguments already parsed (DB, HTTP);
  use `runOnSender` to come back. Its cooldown starts when the handler returns normally.
- Suggestions run on Paper's suggestion thread (may be async): suggestion functions must only read thread-safe
  data (e.g. `ConfigCollection.ids()` snapshot).

## 4. Argument types — `Args`

Every type maps to a **native** Brigadier/Paper argument type, so the client validates and colours input.

| `Args.` | Java type | Native type | Notes |
|---|---|---|---|
| `word()`, `string()`, `greedy()` | String | `StringArgumentType` | `greedy` only as last argument |
| `integer(min,max)`, `longRange`, `decimal(min,max)` | int / long / double | Brigadier numbers | bounds checked client-side |
| `bool()` | boolean | `BoolArgumentType` | |
| `player()`, `players()` | Player / List<Player> | `ArgumentTypes.player()/players()` | selectors resolved for you; none found → `CommandMessages.playerNotFound` |
| `offlinePlayer()` | OfflinePlayer | word + online-name suggestions | only cached players (`getOfflinePlayerIfCached`), never a blocking lookup |
| `world()` | World | `ArgumentTypes.world()` | |
| `blockPosition()`, `position()` | Location (block / fine) | `ArgumentTypes.blockPosition()/finePosition()` | relative `~` resolved against the source |
| `itemStack()` | ItemStack | `ArgumentTypes.itemStack()` | |
| `registry(RegistryKey.X)` | e.g. Enchantment, Sound | `ArgumentTypes.resource(...)` | |
| `material()` | Material | `ArgumentTypes.resource(ITEM)` → Material | |
| `enumOf(Class<E>)` | E | word + suggestions | kebab/lower-case input, like config enums |
| `duration()` | Duration | word | same syntax as config (`30s`, `1h30m`) — shared parser |
| `uuid()`, `key()` | UUID / NamespacedKey | `ArgumentTypes.uuid()/namespacedKey()` | |
| `oneOf(Supplier<Collection<String>>)` | String | word + suggestions | value must be in the set at execution time |
| `custom(native, parse, suggest)` | T | Paper `CustomArgumentType.Converted` | for product types (e.g. `Lot`) |

- `Arg.suggests(fn)` adds/overrides suggestions for any word/string arg (filtered by what was typed, case-insensitive).
- Parse failures from our converters become `CommandSyntaxException` with a locale message
  (`MessageComponentSerializer`), so the client shows them red under the input. Vanilla errors (number too low,
  unknown player selector) stay vanilla: the client translates them itself.

## 5. Permissions, player-only, cooldowns

- `permission(p)` → Brigadier `requires`: the node is hidden from tab-complete and unknown to the client without it.
  Paper only re-sends the tree on join / `player.updateCommands()`; `CommandService.refresh(player)` wraps that for
  plugins that change permissions at runtime.
- `playerOnly()` keeps the node visible to console but replies `CommandMessages.playerOnly` (hiding it would make
  console see "unknown command", which confuses admins).
- `cooldown(Duration)` / `cooldown(Duration, String bypassPermission)`: per player **per executable node**
  (`/auction bid` and `/auction sell` are separate), stored in the `CommandService` instance
  (`ConcurrentHashMap<UUID, …>`), never static. Consumed only when the handler returns normally (`ctx.fail` and
  exceptions do not consume it). Removed on `PlayerQuitEvent` (bug #4) and when the service closes. In memory only:
  a restart clears cooldowns (persistent cooldowns are the product's job, e.g. via the database module).
  Console and command blocks have no cooldown. Reply: `CommandMessages.cooldown` with `<time>` (formatted like config
  durations, rounded up to seconds).
- **One async run at a time:** every `executesAsync` node, with or without a cooldown, runs at most once per player
  at a time (a double `/auction bid` must not hit the database twice). The `(player, node)` pair is reserved atomically
  after the cooldown check and before the arguments are read; a second run meanwhile gets `CommandMessages.busy`
  (`<label>`, `<sender>`) and returns 0. The reservation is released on every path (normal return, `fail`, exception,
  argument error, scheduler refusing the task) and *after* the cooldown has started, so there is no gap in which a
  second run could slip in (both present for a moment is harmless: the cooldown is checked first).
  Quitting does **not** drop it: a rejoin while the handler still runs must not start a second run; only the
  handler's own `finally` releases it (a token keeps a stale release from dropping a newer run). Console and command blocks (no UUID) and sync nodes (one region thread) are not guarded.

## 6. Messages (bug #7: all through locale)

```java
public record CommandMessages(String playerOnly, String cooldown, String busy, String playerNotFound,
                              String invalidValue, String error, String disabled) {
    public static CommandMessages defaults() {
        return new CommandMessages(
                "<red>Only players can use this.",
                "<red>Wait <time> before using /<label> again.",
                "<red>Please wait, /<label> is still running.",
                "<red>No player found: <input>",
                "<red><input> is not valid: <reason>",
                "<red>Something went wrong, see the console.",
                "");                                   // empty = silent (same rule as locale)
    }
}

// in your lang record:
public record Messages(CommandMessages commands, String bidPlaced, String notEnoughMoney, String reloaded) {...}
```

- `messages(LocaleHandle<M>, Function<M, CommandMessages>)` → the service picks the sender's language through the
  handle. Without it, `CommandMessages.defaults()` (English) is used. Because it is a normal nested record, the keys
  appear in `lang/<tag>.yml` under `commands:` with comments, and missing keys are filled like every config.
- `ctx.fail(LocaleHandle<M>, Function<M,String>, ...) (plus `ctx.fail(String miniMessage)`)` / `ctx.reply(lang, ...)` use the product's own locale handle the same way.
- No string literals for messages inside the module apart from `defaults()`.

## 7. Help / usage (minimal)

- `CommandSpec...helpSub()` (opt-in) adds `/<cmd> help` listing the subcommands the sender may use, one line each
  from `usage` + `description` given per node (`.sub("bid", b -> b.usage("<amount>").description("Bid on the lot"))`),
  rendered through `CommandMessages`-style keys `helpHeader`, `helpLine`. Not generated for every command: products
  usually want their own GUI or message.
- Running a non-executable literal (e.g. `/auction admin`) → the same help lines for that branch if `helpSub()` is
  on, else Brigadier's normal "incomplete command".

## 8. Testing

- Unit (no server): Brigadier's `CommandDispatcher` + a fake `CommandSourceStack` (Proxy) — tree shape from a spec
  (literals, aliases, optional expansion, inheritance of permission/playerOnly/cooldown), argument parsing for our
  converters (duration, enumOf, oneOf, offlinePlayer), cooldown consumed only on success, concurrent executions of
  one spec from N threads (no shared state), `ctx.label()` for root and alias, `register` after the event → ISE.
- Self-tests (`CommandSelfTests`): register a test command, run it via `Bukkit.dispatchCommand(console, …)` —
  console path, `allowConsole`, player-only reply, registry/material args, `/minecraft:reload`-style re-registration
  is idempotent (handler can be invoked twice).
- Player paths (tab-complete, per-player cooldown, permission hiding) need a real client → go-mcbots E2E later
  (same harness as gui).

## 9. Decisions for owner (defaults chosen, change if you disagree)
1. **Name `CommandService`** (chosen) vs `Commands` — avoids clashing with Paper's
   `io.papermc.paper.command.brigadier.Commands`, which users import for raw Brigadier.
2. **command depends on config** for locale messages (`CommandMessages` record inside the product's lang file,
   chosen) vs a tiny message interface in command with a config adapter (one more type, no module dependency).
3. **One class per subcommand (`SubCommand`) + typed `Arg<T>` handles** (chosen by owner 2026-10-17) vs one
   nested lambda chain / annotations (reflection) / flat string paths. Plain `ctx.get("amount", Long.class)` still works for
   inline args.
4. **Register in `onEnable` only** (chosen); no `PluginBootstrap` support until a module needs it (dialog).
5. **Sync `executes` on Paper's thread + opt-in `executesAsync`** (chosen) vs always async.
6. **Cooldowns per player per executable node, in memory, consumed on success, cleared on quit** (chosen).
   `executesAsync` nodes also allow only one run per player at a time (`CommandMessages.busy`; owner, 2026-10-17).
7. **Failures via `throw ctx.fail(...)`** (unchecked, chosen) vs returning a result value from every handler.
8. **`playerOnly` nodes stay visible to console with a message** (chosen) vs hidden.
9. **Help is opt-in** (`helpSub()`, chosen) vs auto help for every command.
10. **`CommandCall` is an interface** with sender/arg/reply/cooldown/thread helpers (chosen by owner 2026-10-17).
    Naming follows the rest of the lib (`player()`, `isPlayer()`, like `lib.scheduler()` / `config.get()`), not
    Bukkit's `getPlayer()`.

## 10. Deviations from the design (as built, 0.3.0)
- `helpSub()` is on the shared builder (`SpecBuilder`), so any node can have it, not only the root. A group without a
  handler below such a node (e.g. `/auction admin`) shows the help of its branch; help never starts a cooldown.
  A subcommand named `help` next to `helpSub()` is rejected at `build()`.
- `CommandMessages` gained `helpHeader` (`<command>`) and `helpLine` (`<command>`, `<usage>`, `<description>`);
  `<command>` is the path with the alias actually typed, e.g. `ex airdrop`.
- Cooldown/in-flight order and quit behaviour changed as described in §5 (P2-18 review fixes).
- `invalidValue` has no `<label>` (the alias is not known while Brigadier parses), see `CommandMessages`.
