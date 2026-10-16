# P2-12 Config collections (one file per object)
Status: todo · Approved: yes · Model: sonnet · Effort: high
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
- [ ] API matches config.md §4b
- [ ] no lost updates under concurrency (test), atomic writes
- [ ] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
