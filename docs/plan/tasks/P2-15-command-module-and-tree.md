# P2-15 Command module, CommandService, spec tree, CommandCall basics
Status: todo · Approved: yes · Model: sonnet · Effort: high
Module: synthetixlib-command · Depends on: P2-14 · Design: command.md §1, §2, §3, §5 (permission / playerOnly only)

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/command.md and docs/plan/tasks/P2-15-command-module-and-tree.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.
Before coding, read Paper's `io.papermc.paper.command.brigadier` API (Commands, CommandSourceStack,
LifecycleEvents.COMMANDS) in the paper-api jar and design how specs turn into nodes; list any surprise in Result.

## Spec
- New module `synthetixlib-command` (checklist "Adding a library module" in AGENTS.md): `api(project(":synthetixlib-config"))`,
  package `com.github.dewarepk.synthetixlib.command`, module AGENTS.md + CLAUDE.md, archtest + example wiring,
  `CommandSelfTests` registered in `SelfTestTrigger`.
- `CommandService.create(lib)`: attaches to lib; registers one `LifecycleEvents.COMMANDS` handler on
  `lib.plugin().getLifecycleManager()`. `register(CommandSpec...)` stores specs; the handler builds Brigadier nodes
  from all specs on every event (idempotent). `register` after the first event → `IllegalStateException` with a
  message telling to register in onEnable. After close: executions do nothing and return 0, suggestions empty.
- `CommandSpec.literal(name)` (root: `aliases`, `description`) and `SubSpec.literal(name)` sharing one builder base:
  `argument(Arg)`, `optional(Arg, default)` (node with and without the arg), `sub(SubCommand)`, `sub(SubSpec)`,
  `executes(CommandHandler)`, `executesAsync(CommandHandler)` (may be a stub that throws
  UnsupportedOperationException until P2-17 - say so in Javadoc), `permission`, `playerOnly`, `allowConsole`,
  `usage`, `description`. `SubCommand { SubSpec spec(); }` - `spec()` called once at register.
  Inheritance of `permission` / `playerOnly` / `allowConsole` to children unless overridden. Names `[a-z0-9_-]+`,
  duplicate children → IAE at `build()`. Built specs immutable.
- `Arg<T>`: `Arg.of(name, ArgType<T>)`, `.suggests(fn)`. For this task only `Args.word()`, `string()`, `greedy()`,
  `integer(min,max)`, `longRange(min,max)`, `decimal(min,max)`, `bool()` (the rest is P2-16). `ArgType<T>` is the
  extension point P2-16 builds on: keep it a small interface (native type + optional converter + suggestions).
- `CommandCall` interface exactly as command.md §3; implement here: sender/isPlayer/isConsole/player/playerIfPresent/
  hasPermission/locale (locale: player locale, else default), source/executor/location, get/optional/getOrDefault/has/
  label/input, reply(String)/reply(Component)/reply(lang,...)/replyLines/actionBar, `fail(...)` returning unchecked
  `CommandFailure`. Cooldown methods and `runOnSender`/`isAsync` may return neutral values until P2-17 (document).
  `<label>` and `<sender>` placeholders in every reply.
- `permission` → Brigadier `requires`. `playerOnly` node stays visible; non-player gets
  `CommandMessages.playerOnly` (add `CommandMessages` record with `defaults()` from §6 now; `messages(lang, fn)`
  on the service picks the sender's language). Unexpected exceptions: logged with `input()`, sender gets `error`.
- `label()` = first token of the input without `/` and without `namespace:`.

## Tests
Unit (Brigadier `CommandDispatcher` + Proxy `CommandSourceStack`, no server): tree shape (literals, args, aliases,
optional expansion), inheritance and override, duplicate names → IAE, `requires` hides nodes, playerOnly reply for
console, `label()` for root/alias/namespaced, typed `get` / `optional` / `getOrDefault`, `fail` stops with message and
returns 0, unexpected exception → error message, one spec executed from 8 threads concurrently (no shared state),
`register` after the event → ISE, handler invoked twice builds the same tree.
Self-test `command.basic`: register a test command, `Bukkit.dispatchCommand(console, ...)` for an executable node,
a playerOnly node (reply checked via a capturing sender if possible, else log) and an alias.

## Acceptance criteria
- [ ] API matches command.md §1-§3 (deviations listed in Result)
- [ ] no static state, no reflection (ArchUnit green, rules untouched)
- [ ] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
