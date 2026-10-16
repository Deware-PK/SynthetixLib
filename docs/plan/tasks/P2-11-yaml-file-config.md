# P2-11 YAML file configs (ConfigHandle): load algorithm, comments, reload, migrations
Status: done · Approved: yes · Model: sonnet · Effort: high
Module: synthetixlib-config · Depends on: P2-09a · Design: config.md §1, §2, §4

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/config.md and docs/plan/tasks/P2-11-yaml-file-config.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.
Follow the load algorithm in config.md §2 step by step; it fixes SnapPlug bug #6.

## Spec
- `configs.file(String path, Class<T>, Supplier<T> defaults)` → builder with `.version(int)`,
  `.migrate(int from, int to, Consumer<ConfigTree>)` → `.load()` returns `ConfigHandle<T>`.
- `ConfigHandle<T>`: `get()` (volatile snapshot), `reload()` → `ReloadResult` (errors, warnings, changed?),
  `reloadAsync()` → CompletableFuture (IO on `lib.scheduler().async()`, listeners on global thread),
  `onReload(Consumer<T>)`, `path()`.
- YAML via Bukkit `YamlConfiguration` with `options().parseComments(true)`; header comment from type-level
  `@Comment`; component comments via `setComments`. `config-version` written as first key.
- Load algorithm exactly as config.md §2 (defaults fill-in with comments, invalid → default + error and file value
  kept, unknown keys kept + warning, migrations with `<file>.v<old>.bak` backup, **save only when needed**).
- Per-field fallback (needed for step 4, not in P2-09): add an internal decode mode in `config.mapping`
  (e.g. `RecordCodec` decode with a default instance) that, for every record level, uses the default's component
  when a key is **missing** (report as "missing" so the YAML layer can add it with its comment) or **invalid**
  (report as error `<path>: <message> -> using <default>`), recursing into nested records/maps; strict
  `Codecs.decode` stays all-or-nothing. Keep the public `Problem` API unchanged unless you list why in Result.
- Small carry-over from P2-09a review: `RecordCodec.sealedName` is only set when the sealed parent codec gets
  linked, so `Codecs.create().decode(treeWithType, ItemReward.class)` on a fresh instance still warns
  "type: unknown key". Set it in `RecordCodec.link` from `SealedCodec.nameOf(raw)` whenever the record has a
  sealed ancestor; add a test using a fresh `Codecs` that decodes the leaf without touching the interface first.
- `ConfigTree` (`config.migration`): rename/move/remove/set/get/transform with dotted paths, `importFrom(Path)`.
- Atomic writes: temp file in same dir + `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` (fallback without ATOMIC_MOVE
  if unsupported). Shared helper also used by P2-12.
- Startup: `load()` is synchronous; never throws for bad user values (only for programming errors such as a
  non-record class).

## Tests (unit with @TempDir)
fresh file written with comments + version; missing key added with its comment; invalid value → default used, error
path reported, file bytes keep the user's value; unknown key kept; **unchanged reload does not modify the file**
(compare bytes + mtime); user-added comments survive a save; migration 1→2 renames key and writes backup; multiple
migrations chain; reload listener called with new snapshot; atomic write leaves no temp files.

## Acceptance criteria
- [x] behaviour matches config.md §2 exactly; SnapPlug bug #6 covered by tests
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
Ran `gradlew.bat check smokeTest`: green (163 unit tests in config, smokeTest 47/47 on 1.21.11 and 26.3).
Public API (all new, per design §2/§4): `Configs.file(...)` -> `ConfigFileBuilder` (`version`, `migrate`, `load`) ->
`ConfigHandle` (`get`, `reload`, `reloadAsync`, `onReload`, `path`), `ReloadResult(errors, warnings, changed)` +
`ok()`, `config.migration.ConfigTree`. Internal (`@Internal`): `yaml.FileConfig` (the load algorithm),
`yaml.AtomicFiles` (shared with P2-12), `yaml.YamlTrees`, `migration.Migration`, `mapping.Schema`/`LenientResult`,
`Codecs.decodeLenient`. `Problem` is unchanged. Package-private `ConfigHost` lets `ConfigHandle` be tested without a server.
- Per-field fallback: `RecordCodec`/`MapCodec` implement an internal `LenientCodec`. Missing key -> default, reported
  with its path and encoded default so the YAML layer adds it with the comment; invalid -> default, error text
  `<path>: <message> -> using <default>`. Map entries: invalid entry with a default under the same key falls back to it,
  one without is left out (`-> entry ignored`, stays in the file); a missing map entry is never added back.
- Carry-over: `RecordCodec.sealedName` is set in `link` from `SealedCodec.nameOf` (test on a fresh `Codecs`);
  `markSealedLeaf` removed.
- Decisions to review:
  - `reload()` calls listeners only when the snapshot changed (`equals`), on the calling thread; `reloadAsync()` does IO on
    `async()` and runs listeners + completes the future on the global thread. A listener that throws is logged.
  - YAML that cannot be parsed (or read): `value == null` -> a reload keeps the current snapshot and reports the error,
    first `load()` uses the defaults in memory; the file is never touched.
  - A file with a newer `config-version` than the plugin: warning, file never written. A failing migration: error,
    file untouched (backup stays), values decoded from the original data.
  - Bukkit's path separator is replaced by a control char (`YamlTrees`), so map keys with dots (`vip.gold`) stay one
    key. Consequence: `ConfigTree` paths cannot address keys that contain a dot.
  - `ConfigTree.rename(path, newName)` renames the last segment (use `move` for another place); `move`/`rename`
    overwrite an existing target and carry the key's comments; `importFrom(Path)` merges the other file into the tree
    (its values win) and returns false if the file does not exist (design §4 left the semantics open).
  - A file without `config-version` gets it added as the first key (one write); header comment = type-level `@Comment`.
  - Writes re-serialize through `YamlConfiguration`: user comments are kept, but blank lines between entries are
    not (Bukkit limitation seen in a probe). Only happens when a write is needed.
- Tests: `FileConfigTest`, `AtomicFilesTest`, `ConfigTreeTest`, `ConfigHandleTest`, sealed-leaf case in `CodecsTest`;
  self-tests `config.file.bukkit-types`, `config.file.migrate`, `config.file.reload-async`.
