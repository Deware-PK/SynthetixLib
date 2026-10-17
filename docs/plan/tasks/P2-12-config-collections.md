# P2-12 Config collections (one file per object)
Status: done · Approved: yes · Model: sonnet · Effort: high
Module: synthetixlib-config · Depends on: P2-11a · Design: config.md §4b

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/config.md and docs/plan/tasks/P2-12-config-collections.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.
Concurrency matters: think through create/update/delete racing with reload and with each other.

## Spec
- `configs.collection(String dir, Class<T>, Function<String,T> defaults)` → builder `.version`, `.migrate`,
  `.template(String fileName)` → `.load()` → `ConfigCollection<T>`.
- API exactly as config.md §4b: `create(id)`, `create(id, T)`, `update(id, UnaryOperator<T>)`, `get`, `all`,
  `ids`, `rename`, `delete` (moves to `<dir>/.deleted/<id>-<timestamp>.yml`), `reload()` → per-file results,
  `reloadAsync()`, `onChange(Consumer<Change<T>>)` with sealed `Change` = `Added | Updated | Removed`.
- Ids `[a-z0-9_-]{1,64}`, input lower-cased; files starting with `_` or `.` ignored.
- Template: if `<dir>/<template>` exists, a new object = template file decoded (missing keys from
  `defaults(id)`); otherwise `defaults(id)`.
- In-memory state: immutable map snapshot swapped atomically. Mutations serialized (one lock or a single-writer
  executor per collection); file writes on `lib.scheduler().async()` in submission order, atomic (P2-11 helper);
  `update` writes only changed keys into the existing YAML so comments/hand edits survive.
- Change listeners run on the global region thread, after the snapshot changed.
- `lib.close()` flushes pending writes (wait up to 5 s, then log what was not written).

## Tests
Unit (@TempDir, fake executor): create/update/delete/rename files + snapshot; template used; ignored files;
broken file skipped with error while others load; concurrent updates from N threads end with all updates applied
(no lost update); reload after hand edit emits Updated; delete moves file; close flushes.
Self-test `config.collection.basic` on a real server (create → update → reload → delete, events observed).

## Acceptance criteria
- [x] API matches config.md §4b
- [x] no lost updates under concurrency (test), atomic writes
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
Ran `gradlew.bat check smokeTest`: green (Paper 1.21.11 and 26.3, 49/49 self-tests incl. `config.collection.basic`).
- New public API in `config`: `Configs#collection` -> `ConfigCollectionBuilder` (`version`, `migrate`, `template`, `load`) ->
  `ConfigCollection` (`create` x2, `update`, `get`, `all`, `ids`, `rename`, `delete`, `reload`, `reloadAsync`, `onChange`),
  sealed `Change` (`Added | Updated | Removed`, nested records), `FilesReloadResult` (per-file `ReloadResult` + removed ids;
  shared with locale).
- Design points beyond the spec text:
  - Two locks: `lock` (snapshot swap, short, no IO) and `ioLock` (whoever touches files: async drain, reload, close;
    always taken before `lock`). Writes are a FIFO queue drained by one async task at a time; `update` runs under `lock`,
    so concurrent updates cannot be lost.
  - `reload()` first drains the queue (so files are never behind the snapshot) and remembers ids mutated while it runs,
    which it does not touch (no mutation is reverted by a reload). Per-file `FileConfig.load` is reused, so missing keys
    are added and a broken file is skipped and keeps its old value.
  - `update` writes only keys that differ between `encode(old)` and `encode(new)` (`YamlTrees.applyChanges`), so comments,
    hand edits and unknown keys survive; a file that is no longer valid YAML is not touched (logged, change stays in memory).
  - Template: `create` copies the template (its comments too) and fills missing keys from `defaults(id)`; the template file
    is never written. Needed `FileConfig.compute()` (= `load()` without the write) and `FileConfig.render(value)`.
  - `delete` -> `.deleted/<id>-<yyyyMMdd-HHmmss>[-n].yml`; `rename` = file move (Removed old, Added new). A pending-ops count
    per id lets an id be re-created right after its delete although the file is still on disk.
  - Errors: invalid id -> `IllegalArgumentException`; `create` of an existing id (or of an id whose file exists but is
    broken) -> `IllegalStateException`; `update`/`rename` of a missing id -> `NoSuchElementException`; `delete` returns
    `false` if absent; `get` of an invalid id is just empty.
  - `Configs.close()` flushes every collection (waits up to 5 s for the writer, then logs the writes that were lost).
  - Known limit: every `update` is one file write (a burst of N updates = N writes; ~3 ms each on Windows).
- Tests: `ConfigCollectionTest` (30: files + snapshot, template, ignored/broken files, invalid values, hand edit reload,
  8 threads x 60 updates with no lost update, concurrent creates, mutations racing reloads, delete/rename/re-create, close).

