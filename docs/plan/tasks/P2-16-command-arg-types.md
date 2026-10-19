# P2-16 Command argument types (Args)
Status: done · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-command · Depends on: P2-15a · Design: command.md §4

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/command.md and docs/plan/tasks/P2-16-command-arg-types.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- Every row of the table in command.md §4 not done in P2-15: `player()`, `players()` (selectors resolved against the
  source; none → `CommandMessages.playerNotFound` with `<input>`), `offlinePlayer()` (cached only, online-name
  suggestions, never blocking), `world()`, `blockPosition()` / `position()` → `Location` (relative coords resolved),
  `itemStack()`, `registry(RegistryKey)`, `material()`, `enumOf(Class)` (same matching as config enums, suggestions in
  lower kebab case), `duration()` (reuse the config Duration parser: expose an internal helper in config rather than
  copying), `uuid()`, `key()`, `oneOf(Supplier<Collection<String>>)` (checked again at execution), `custom(native,
  parse, suggest)` via Paper `CustomArgumentType.Converted`.
- Converter errors → `CommandSyntaxException` with a locale message (`CommandMessages.invalidValue`, `<input>`,
  `<reason>`) through `MessageComponentSerializer`; vanilla errors untouched.
- `Arg.suggests(fn)`: filtered by the typed prefix, case-insensitive; runs on Paper's suggestion thread (document).
- No deprecated API (list warnings in Result).

## Tests
Unit: converters (duration, enumOf, oneOf, offlinePlayer cache miss, custom), suggestions filtering, error message
text. Self-tests `command.args.registry`, `.material`, `.world`, `.position` (console dispatch with absolute
coordinates), `.player-not-found`.

## Acceptance criteria
- [x] all types of §4 available and tested
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
Ran `gradlew.bat check smokeTest`: BUILD SUCCESSFUL. 14 new unit tests (`CommandArgsTest`; the module has 75 now);
smoke 60/60 on Paper 1.21.11 (Java 21) and 26.3 (Java 25) with the new `command.args.registry`, `.material`, `.world`,
`.position`, `.player-not-found`, `.text`. ArchUnit rules untouched. Nothing committed. Compiler warnings: none
(`ItemType#asMaterial` is deprecated, so `material()` goes through `Material.matchMaterial(key)` like the config codec).

**Added**: `player()`, `players()`, `offlinePlayer()`, `world()`, `blockPosition()`, `position()`, `itemStack()`,
`registry(RegistryKey)`, `material()`, `enumOf(Class)`, `duration()`, `uuid()`, `key()`, `oneOf(Supplier)`,
`custom(native, parse[, suggest])`. `Arg.suggests` was already there (filtered, case-insensitive, Javadoc says it runs
on Paper's suggestion thread); now covered by a test with an overridden type.

**Decisions / deviations**
- Config: `config.codec.Durations` is now `public` + `@ApiStatus.Internal` (`parse`, `format`) instead of a copy; this
  is the only change outside the command module (and the Javadoc/comment of `invalidValue`).
- Text converters (`duration`, `enumOf`, `oneOf`, `custom`) are a Paper `CustomArgumentType.Converted` and reject while
  Brigadier parses: `CommandSyntaxException` whose message is `CommandMessages.invalidValue` rendered for the sender
  (Paper passes the `CommandSourceStack` to `convert(value, source)`) through `MessageComponentSerializer`.
  The alias is not known at that point, so `invalidValue` has `<sender> <input> <reason>` but **no `<label>`**
  (comment and Javadoc of `CommandMessages` say so). The `<reason>` strings (`expected one of: ...`, the Durations text)
  are English text built in the module, not locale keys.
- `player()`, `players()` and `offlinePlayer()` need the source, so they resolve when the handler calls `ctx.get` and
  answer `playerNotFound` (`<label>`, `<sender>`, `<input>`) as a `CommandFailure`. Paper's player resolver does not
  return an empty list for "nobody": it throws vanilla's `argument.entity.notfound.player`; only that key is turned into
  `playerNotFound`, every other vanilla error is left alone. Found by the self-test, not documented by Paper.
- `offlinePlayer()` asks `Server#getOfflinePlayerIfCached` of the sender's server (no static `Bukkit`), suggests the
  names of online players.
- `position()` uses `ArgumentTypes.finePosition(true)`: whole numbers are centred in the block like vanilla (`10` is
  `10.5`); `blockPosition()` is the block corner. The world is the source's world.
- `oneOf` accepts `Supplier<? extends Collection<String>>`, matches ignoring case and returns the set's spelling.
- `enumOf` copies the 8 lines of matching of the config enum codec (that class is in the reflection package and
  package-private); it uses `EnumSet.allOf`, no reflection.
- `custom` has the native type + `Function<N,T>` (throw `IllegalArgumentException` to reject; its message is the
  reason) + optional suggestions; there is no `Class<T>` parameter.
- Internals: `ArgTypeImpl` (public `ArgType` + package-private host-aware methods used by `TreeCompiler` and
  `CommandCallImpl`), `ArgErrors`; `CommandHost` gained `toMessage` / `fromMessage` so unit tests need no server.
  Paper's `ArgumentTypes` need a server, so the Paper-backed types are created lazily and are only exercised by the
  self-tests, not by unit tests.
- Bug found and fixed on the way: the typed text for `<input>` was taken from the first node with the argument's name,
  which was a literal when both share a name (`sxtest player <player>`); only argument nodes count now.
