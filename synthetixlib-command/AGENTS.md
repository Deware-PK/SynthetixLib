# synthetixlib-command

Package: `com.github.dewarepk.synthetixlib.command` (only this module may use it). Design: docs/design/command.md.
Depends on core + config (built-in messages live in the product's lang file as a `CommandMessages` record).

Types
- `CommandService` - one per `SynthetixLib`; registers ONE `LifecycleEvents.COMMANDS` handler and builds the
  Brigadier nodes from the stored specs on every event (server start and `/minecraft:reload`). `register` after the
  first event throws; register in `onEnable`.
- `CommandSpec` (root) / `SubSpec` (sub node) share `SpecBuilder`; `SubCommand` = one class per subcommand.
  Specs are turned into an immutable internal `Node` tree at `build()` (the tree is the only thing executions read).
- `Arg<T>` + `ArgType<T>` + `Args` - typed argument handles; every `ArgType` has a native Brigadier type.
- `CommandCall` - per-execution context (interface; `CommandCallImpl` is created per run, never shared).
- Internals (package-private): `Node`, `TreeCompiler`, `Executor`, `CommandRegistry`, `CommandHost` (seam for unit tests),
  `ArgTypeImpl` (+ `ArgErrors`) behind most of `Args`, `Cooldowns`, `SenderThreads` (where `executesAsync` /
  `runOnSender` run).

Rules specific to command
- No shared mutable state in specs or executors (SnapPlug bug #7): everything per call lives in `CommandCallImpl`.
  Cooldown state (P2-17) lives in the `CommandService` instance, never static.
- `label()` is taken from the typed input (alias actually used), never from the spec name.
- Every user-facing string comes from `CommandMessages` / the product's lang, except `CommandMessages.defaults()`.
- Permissions: Brigadier checks `requires` on every node of the path, so a child can add a permission but never
  loosen its parent's.
- Unit tests use a real Brigadier `CommandDispatcher` with Proxy senders (`CommandFixtures`); nothing needs a server.
  Paper's `ArgumentTypes.*` need one, so `Args.player()`, `world()`, `position()`, `registry()` ... are created lazily
  and covered by the `command.args.*` self-tests instead.
- Arguments: text converters (`duration`, `enumOf`, `oneOf`, `custom`) run while Brigadier parses, as a Paper
  `CustomArgumentType.Converted`, and reject with `CommandMessages.invalidValue` (`<input>`, `<reason>`; no `<label>`:
  the alias is not known yet). Selector/lookup types (`player(s)`, `offlinePlayer`) resolve when the handler reads them
  and answer `playerNotFound`. `TreeCompiler` / `CommandCallImpl` hand the `CommandHost` to `ArgTypeImpl`, which is how
  these messages reach the sender's language; a plain `ArgType` from outside the module never needs it.
  Paper reports "nobody matched" by throwing vanilla's `argument.entity.notfound.*`; only that one is replaced.
- Every typed argument is read once per run and cached in `CommandCallImpl`. `executesAsync` reads them all on the
  command thread before the handler is queued, so selectors are never resolved off-thread.
- Cooldowns: key = player + `Node.path()` (root name, not alias), one flat `ConcurrentHashMap` in `Cooldowns`. Started
  in `CommandCallImpl.finished()` only after a normal return, under a per-call lock shared with `skip/resetCooldown`.
  Cleared by the quit listener (a plain `EventExecutor`, no reflection), dropped when looked at after expiry, and swept
  every 5 minutes on `lib.scheduler().async()` (catches an async handler that finishes after its player quit).
  In-flight guard: every `executesAsync` node is reserved per (player, path) in `Cooldowns` (`reserve` returns a token,
  `release` needs it, so a stale release cannot drop a newer run) from `Executor.runAsync` after
  the cooldown check; released on every path after `finished()` has started the cooldown; the sweep never touches it.
  `CommandHost.runAsync` / `SenderThreads.runAsync` throw `IllegalStateException` when the task will not run.
- Help (`helpSub()`): `Help` is the handler of the synthetic `help` child and of handler-less groups below a `helpSub()`
  node; it lists the children the sender has every permission for, using `CommandMessages.helpHeader/helpLine`.
- Cooldown order: `Executor` starts the cooldown (`finished()`) and only then releases the reservation. A quit clears
  cooldowns only (`Cooldowns.clear`); reservations are ended solely by their own `finally`.
- Status: complete (0.3.0). Demo: `synthetixlib-example` `/example`.
