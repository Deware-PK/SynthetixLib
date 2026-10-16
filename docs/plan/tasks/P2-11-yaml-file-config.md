# P2-11 YAML file configs (ConfigHandle): load algorithm, comments, reload, migrations
Status: todo · Approved: yes · Model: sonnet · Effort: high
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
- [ ] behaviour matches config.md §2 exactly; SnapPlug bug #6 covered by tests
- [ ] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
