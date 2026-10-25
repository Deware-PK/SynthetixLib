# Troubleshooting

Message or symptom -> cause -> fix, taken from what the library itself throws or logs. Sorted by module. `<...>` is a value
that differs. Config, menu and dialog file problems are **logged and survived** (a safe fallback is used); the
`IllegalArgumentException` / `IllegalStateException` rows are thrown at the call that caused them.

## core

| Message / symptom | Cause | Fix |
|---|---|---|
| `SynthetixLib is closed` (`IllegalStateException`) | `lib.attach(...)` after `lib.close()` | create services in `onEnable`, before `close()` |
| `SynthetixLib is closed, cannot schedule a <kind> task` | scheduling after `close()` (a late callback, a database or HTTP future) | check `lib.isClosed()` in callbacks that outlive the plugin, or ignore the failure in `onDisable` |
| `delay must be >= 0 ticks`, `period must be >= 1 tick`, `delay must not be negative`, `period must be >= 1 ms` | a negative delay or a zero period | pass `0` or more for delays, `1`+ for periods |
| `location has no world` | `scheduler.region(location)` with an unloaded world | use `region(world, chunkX, chunkZ)` or check `location.getWorld()` |
| `Invalid PDC key name '<x>', allowed: [a-z0-9/._-]+` | upper case or spaces in a `PdcKey` name | lower case and `_` |
| `empty item` | `PdcKey.set/remove` on `AIR` / amount 0 | check `stack.isEmpty()` first |
| `amount <n> outside 1..<max>` | `ItemBuilder.amount(...)` above the material's max stack size | clamp, or split the stack |
| `Exception in <kind> task (scheduled at ...)` in the log | your task threw; the timer keeps running | fix the exception; the origin is the line that scheduled it |
| Tasks keep running after a plugin reload | `lib.close()` missing in `onDisable` | call it (rule `core-close-in-ondisable`) |

## config

| Message / symptom | Cause | Fix |
|---|---|---|
| `Configs is closed` | a file/collection/locale created after `lib.close()` | create in `onEnable` |
| `<type> must be a record` | `file(...)`/`locale(...)`/`collection(...)` with a class | use a `public record` |
| `Record <type> must be public so it can be mapped ...` | package-private or nested private record | make it `public` |
| `No codec for <type>. Use a record, an enum, a sealed interface ...` | a component type nobody can read (arrays, raw generics, your own class) | use a supported type or `configs.codecs().register(Type.class, Codec.string(...))` |
| `Raw <x> is not supported, declare its type arguments`, `Unsupported type ... (arrays are not supported ...)` | `List` without `<T>`, or `String[]` | declare `List<String>` |
| `Only Map<String, T> is supported` | a map with a non-String key | use `Map<String, T>` |
| `@Range on <where> needs an int, long, float or double`, `@NotEmpty on <where> needs a String, List, Set or Map` | annotation on the wrong component type | move or drop it |
| `Record <x> has two components with the key '<k>'` | two components map to the same kebab-case key / `@Key` clash | rename one |
| `Subtypes <a> and <b> ...` / `<x> has no permitted subtypes` / `@TypeName ... must not be blank` | sealed interface problems | give each record a distinct name or `@TypeName`; seal with `permits` |
| `[<file>] <path>: must be >= <n> (was <v>) ...` / `...: unknown value '<x>', valid values: ...` | an edited value is invalid | the file is **kept**, the field uses its default; fix the value and reload |
| `unknown type '<t>', valid types: ...` / `missing (one of: ...)` | a `type:` of a sealed list is wrong | use one of the listed types |
| `<path>: invalid YAML, the file is left as it is: ...` | not valid YAML (tabs, quoting) | fix the syntax; the previous snapshot stays |
| `<version> is newer than the <n> ...` (warning) | the file was written by a newer plugin | update the plugin or restore an older file |
| `migration from version <n> failed, the file is left as it is: ...` | an exception in your `migrate(...)` action | fix the migration; a backup `<file>.v<old>.bak` exists |
| `version must be at least 1, got <n>`; `A migration must go to a higher version ...`; `Two migrations start at version <n>` | wrong `.version(n)` / `.migrate(from, to, ...)` | versions start at 1 and rise |
| `Invalid path "<p>" (empty segment)`; `newName must be a single key without dots` | `ConfigTree` misuse in a migration | use dotted paths without empty parts; `rename` takes one key |
| `"<id>" is not a valid id: use a-z, 0-9, _ and -, ...` | collection id with upper case, spaces or too long | validate player input; reply through the locale |
| `"<id>" already exists in <name>` / `a file named <x> already exists ...` | `create` or `rename` onto an existing id (also differing only in letter case) | check `ids()` first |
| `no "<id>" in <name>` (`NoSuchElementException`) | `update`/`rename` of a missing id | check `get(id)` |
| `[<name>] cannot update <file> ...`, `not written before shutdown: ...` | disk error or the queue could not drain on stop | check permissions/disk; the in-memory snapshot is still right |
| `invalid duration '<x>'` / `duration '<x>' is too large` | `30 sec`-style input | `30s`, `5m`, `1h30m`, `250ms` |
| `unknown world "<w>" (loaded: ...)` (Location) | the world is not loaded when the file is read | load the world first, or reload later; the text stays in the file |
| `<x> is not in the <noun> registry` / `Legacy material <x> cannot be saved` | wrong `Material` / `Sound` key | use the namespaced key from the registry |
| `Containers cannot be registered; register their element type` | `codecs.register(List.class, ...)` | register the element type |
| `"<path>" is not a <what> inside the plugin's data folder` | a path with `..` or absolute | relative path inside the plugin folder |

## command

| Message / symptom | Cause | Fix |
|---|---|---|
| `Commands can only be registered before the server registers them: register in onEnable, not later` | `register` after the first `COMMANDS` event | register in `onEnable` |
| `/<name> is already taken by another plugin; it is available as /<plugin>:<name>` (warning) | another plugin owns the label | rename, or tell the owner to use the namespaced form |
| `CommandService is closed` | register after `lib.close()` | register in `onEnable` |
| `"<x>" is not a valid <what> (allowed: a-z 0-9 _ -)` | upper case or spaces in a literal / argument name | lower case, `-` or `_` |
| `/<path> has two subcommands named "<x>"`, `... two arguments named "<x>"`, `alias "<x>" is used twice` | duplicate names in one spec | rename |
| `/<path> has arguments but no handler`, `has neither a handler nor subcommands` | incomplete spec | add `executes(...)` or `sub(...)` |
| `/<name>: a required argument cannot follow an optional one` | `optional(...)` before `argument(...)` | optional arguments go last |
| `/<path>: greedy argument "<x>" ...` | `Args.greedy()` not last | make it the last argument |
| `/<name> already has a handler` | `executes` twice | one handler per node |
| `/<path> has helpSub() and a subcommand named "help"` | name clash | rename your subcommand or drop `helpSub()` |
| `/<name>: cooldown must not be negative`, `bypass permission must not be blank`, `permission must not be blank` | bad modifier values | fix |
| `<arg> is not an argument of /<input>` / `was not typed in /<input>` | `ctx.get(arg)` for an argument of another node, or an optional one that was not typed | read arguments of this node only; use `getOrDefault` / `optional` |
| `/<input> was not run by a player but by <x>` | `ctx.player()` on a node that allows the console | `playerIfPresent()` or `playerOnly()` |
| Red `<input> is not valid: <reason>` in the client | an `enumOf`/`duration`/`oneOf`/`custom` converter rejected the input | the reason names the allowed values (`expected one of: ...`) |
| `Something went wrong, see the console.` + `input` in the log | your handler threw | read the stack trace; throw `ctx.fail(...)` for expected failures |
| `Please wait, /<label> is still running.` | a second run of an `executesAsync` node by the same player | expected; the first run releases it when it finishes |
| Command replies nothing after the plugin was disabled | the nodes stay in Paper's tree until the next reload | restart or `/minecraft:reload` |

## gui

| Message / symptom | Cause | Fix |
|---|---|---|
| `[menus/<id>.yml] <path>: action '<n>' is not registered ..., the item is inert` | the file uses `action:<n>` / `list:` / `state:` your code did not register | register it on the `MenuBuilder`, or fix the name in the file |
| `... uses the prefix '<p>', which is not a built-in action and no module is linked to it, the item is inert` | `dialog:sell` without `gui.link("dialog", ...)` | link before building the menus (or `reload()`) |
| `placeholder <x> is not registered in code, it is shown as text` | a `<tag>` in a title/name that no `placeholder(...)` provides | register it or write plain text; known MiniMessage tags are never treated as placeholders |
| `Menu '<id>' has no file <file> and no bundled ...` | no `menus/<id>.yml` in the jar | add the file under `src/main/resources/menus/` |
| `A menu with the id '<id>' was built already` / `The builder ... was already used` | `gui.menu(id)` twice, or `build()` reused | build once per id |
| `withContext() must come before the first registration of menu '<id>'` | `withContext()` after `placeholder(...)` | call it first |
| `placeholder '<n>' is built in` (`page`, `pages`, `player`) | registered a reserved name | pick another |
| `onListClick("<l>") but no list("<l>") ...` | handler without a list | register the list |
| `a space is an empty slot, not an input slot` / `input change handler '<c>' is registered already` | `onInputChange` misuse | use a real character, once |
| `layout has <n> lines, <type> needs ...`, `layout[<i>] has <n> characters ...` | the layout does not match `rows` x 9 | fix the file (the layout is cut or padded) |
| `items.<c>: not used in layout`, `character '<c>' has no item in items` | typo between layout and items | align them |
| `material is required (use material: air for an invisible click area)` | an item without `material` | add it |
| `unknown action '<x>'`, `'<x>' needs ..., e.g. x:...`, `'<x>' takes no argument`, `invalid sound key '<k>'` | bad click step | see the step list in `gui.md`; the item has no clicks |
| `unknown key` (error with a path) | typo in the file | the key is ignored, the item is kept |
| `invalid YAML, the file is not used: ...` | broken YAML | first load = empty chest; on reload the last good menu stays |
| `'<id>' is not a valid menu id ...`, `'<p>' cannot be a link prefix ...`, `the prefix '<p>' is linked already` | naming | lower case letters, digits, `-`, `_`; link a prefix once |
| Items vanish / get stuck after a `COLLECT_TO_CURSOR`/shift-click | you handled `InventoryClickEvent` on a menu yourself | don't; use `input: true` slots |
| Mouse pointer jumps to the centre on every page | the title contains `<page>` or another changing value, so the inventory is re-opened | keep live values out of the title |

## dialog

| Message / symptom | Cause | Fix |
|---|---|---|
| `[dialogs/<id>.yml] ... action '<n>' is not registered` (button inert) | `action:<n>` without `action(...)` / `submit(...)` | register it |
| a `submit` record "does not fit the inputs" (warning, buttons inert) | record components differ from `inputs:` keys | rename to the camelCase of the kebab-case keys; types must decode (`double` for number, `String` for option/text, `boolean` for bool) |
| `Dialog '<id>' has no file <file> and no bundled ...` | no `dialogs/<id>.yml` in the jar | add it under `src/main/resources/dialogs/` |
| `A dialog with the id '<id>' was built already`, `The builder of dialog '<id>' was used already` | built twice | build once |
| `withContext() must come before ...`, `placeholder '<n>' is built in`, `action '<n>' is registered already` | builder misuse | see the message |
| `title: missing`, `type: missing, using notice`, `buttons: missing, ...`, `buttons.<k>: missing, using the default ...` | incomplete file | add them; defaults are used meanwhile |
| `an input key uses lower case letters, digits, - and _ (at most 64) ...`; `the client gets '-' as '_' ..., so this key is the same as '<k>'` | invalid or colliding input key | rename |
| `min must be less than max`, `step: must be greater than 0 ... ignored`, `initial: must be between ...`, `needs at least one option` | bad input values | fix; the input is dropped or clamped |
| `errors.<k>: there is no input with this key, never shown` | typo in `errors:` | match an input key |
| `input('<k>') is registered but the file has no input` | `input(...)` for a missing key | fix the key |
| `a button name uses lower case letters, digits, - and _`; `needs at least one button besides exit and columns`; `buttons.dialogs: needs at least one dialog id` | button/list problems | see `dialog.md` |
| `callback-lifetime must be between 1s and 1d`, `registry-click-interval must be between 0 and 1m` | `DialogSettings` out of range | fix the config |
| `bootstrapped() was called already` / `call bootstrapped(...) before registered(...)` / `no dialog was registered under the key '<k>'` | registry dialog order | `bootstrapped(registry)` once, then `registered(key)` |
| `the key '<k>' needs a namespace, e.g. myplugin:<k>`; `... is registered already`; `'<id>' is not a registered dialog file (register it first)` | `SynthetixDialogs` misuse in the bootstrap | fix |
| `dialog files can only link the prefix 'menu', not '<p>'` | `dialogs.link("shop", ...)` | only `menu` |
| a button does nothing after a reload | the file changed after the dialog was shown | the player reopens it (an unchanged reload keeps it working) |
| a button does nothing the second time | one-use callbacks by design | `reopen()` or `next(...)` in the handler |
| Nothing shows on an old / Bedrock client | the client cannot display dialogs | `fallback(cannotShow, handler)` |
| Escape closes the dialog and `onClose` never fires | the client does not report Escape for notices/confirmations | `can-close-with-escape: false` |

## database

| Message / symptom | Cause | Fix |
|---|---|---|
| `HikariCP is not on the plugin's class path: add SynthetixLibraries.database(classpath) to your PluginLoader ...` | no loader, or `loader:` missing in `paper-plugin.yml` | add the loader (see `database.md`) |
| `No JDBC driver accepts <url>. Paper ships it; if this server does not, add <jar> to its class path` | a server without the bundled driver | use Paper; or add the driver jar |
| `Cannot connect to the mysql database at <url>: ...` (+ hint about `allowPublicKeyRetrieval`) | wrong host/credentials, or MySQL 8 without TLS | fix the settings; add `properties: { useSSL: 'false', allowPublicKeyRetrieval: 'true' }` |
| `database.table-prefix "<p>" ...` / `<key> "<v>" is empty or has characters that do not ...` | prefix, schema or host with illegal characters | letters, digits, `_` |
| `database.sqlite.file is empty`, `database.mysql.port <n> is not between 1 and 65535` | bad settings | fix |
| `Cannot create the folder of the database file <f>: ...` | the plugin folder is not writable | permissions |
| `The database is closed` / `The database closed before this statement ran` (`DatabaseClosedException`, in the future) | a call after `close()` or one that did not finish within `shutdown-timeout` | stop issuing statements in `onDisable`; raise `shutdown-timeout` |
| `Database shutdown: <n> statement(s) did not finish within <t> ...` (warning, lists SQL) | long statements at stop | raise `shutdown-timeout`; shorten the statements |
| `A server thread is waiting for the database (join/get). The server is frozen ...` (warning with a stack) | `.join()` / `.get()` on the server thread | `thenAccept` + `lib.scheduler()` hop (waiting is fine only in `onEnable` / `onDisable`) |
| `waiting on the database from its own worker would deadlock; chain with thenCompose/thenAccept` | `.join()` inside a callback or a transaction body | chain; use `tx` in a transaction |
| `A transaction must be used on the thread that runs its body` / `This transaction is over; use it only inside the transaction body` | `tx` escaped its body | do all statements inside the body |
| `Parameter <n> is a <type>, ...` (`IllegalArgumentException` at the call) | an unsupported parameter type | convert to `String`, number, `UUID`, `Instant`, `byte[]`, enum, `ItemStack` |
| `Column "<c>" is NULL; read it with <x>OrNull`; `No column "<c>" in this result (columns: ...)`; `Column "<c>" holds a <type> ...` | row mapping | use the `...OrNull` getter; fix the column name / type |
| `Migration <n> '<name>' was changed after it was applied` | a shipped `V<n>__*.sql` was edited | restore it; add a new version |
| `The database is at migration <n> but this plugin only knows <m>` | a downgrade | update the plugin |
| `Migration <n> is missing: versions must be 1, 2, 3... without gaps`, `Version <n> is used twice`, `Migration file '<f>' has no valid version` | naming of files | `V<number>__<name>.sql`, no gaps or duplicates |
| `Migration <n> '<name>' has no script for <dialect>` | only a `.mysql.sql` exists | add the plain `.sql` (or the other dialect) |
| `Migration folder '<f>' not found in the plugin jar` | wrong path or not packaged | `src/main/resources/<f>`; check the shaded jar |
| `<label> failed and was rolled back (on MySQL, DDL already ran stays): ...` | an SQL error in a migration | fix and re-run; on MySQL clean the half-created table |
| `Table "<t>" is not a plain name`, `Key column "<k>" has no entry in values`, `keyColumns is empty` | `upsert` / `Copy` misuse | plain identifiers; every key in `values` |
| `Table <t> has no primary key, so Copy cannot page through it` | `Copy` on MySQL | add a primary key |
| `from and to are the same database`, `A table is listed twice` | `Copy` misuse | fix |
| `database.<x>` password in a log | never; the settings print `password=***` | report it as a bug |
| Writes lost after `/stop` | `lib.close()` not called, or the plugin was killed | call `lib.close()` in `onDisable` |
