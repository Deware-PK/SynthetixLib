# P2-26 Database migrations
Status: done · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-database · Depends on: P2-25 · Design: database.md §5

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/database.md and docs/plan/tasks/P2-26-database-migrations.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- `Migrations.fromResources(path)` reads `V<n>__<name>.sql` (+ `.mysql.sql` / `.sqlite.sql` overrides) from the
  plugin jar (no classpath scanning by reflection: read the jar entries via the plugin's resource API / a generated
  index file at build time — pick one, explain in Result). `.withCode(n, name, tx -> ...)`.
- `db.migrate(migrations)` → future: creates `{prefix}schema_history`, runs missing versions in order, each in its own
  transaction; checksum (SHA-256 of the chosen file, normalised line endings) mismatch → fail, nothing runs; DB version
  higher than known → fail; gaps / duplicate versions → fail at load. Statement splitting on `;` at line end, `--` comments.
- MySQL note: DDL auto-commits; document that a failed MySQL migration may leave partial DDL (write small migrations).

- Carry-over from P2-25 review: `DbFuture.join()/get()` called **on a worker thread of the same Database** (a callback or
  a transaction body waiting for another call) → `IllegalStateException("waiting on the database from its own worker
  would deadlock; chain with thenCompose/thenAccept")` at once, both dialects (MySQL can deadlock too when the pool is
  exhausted). Mark worker threads in the thread factory (instance-owned, no static state). Unit test for both dialects,
  incl. `get(timeout)`. `DatabaseException` Javadoc: logging its cause prints the driver text, which may quote data.
- MySQL: every database unit test of this task also runs against MySQL when the Gradle properties `mysqlUrl`,
  `mysqlUser`, `mysqlPassword` exist (owner's user gradle.properties, Docker MySQL); skipped without them. Random table
  prefix per run, tables dropped afterwards. Never print the password. Say in Result whether the MySQL run happened.

## Tests
Unit (SQLite): order, re-run does nothing, checksum change, downgrade, override picked per dialect, code migration,
splitting/comments, CRLF vs LF same checksum, failure rolls back on SQLite.
Self-test `database.migrate` (bundled `db/migrations` in the example).

## Acceptance criteria
- [x] every rule of §5 has a test
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result

Ran `gradlew.bat check smokeTest`: green (Paper 1.21.11 and 26.3, self-tests 73/73 incl. `database.migrate`).
**MySQL run happened:** `MysqlMigrationTest` 17 tests and `MysqlQueryTest` 20 tests, 0 skipped, random prefix per class, tables dropped after.

**Added**
- `Migrations` (`fromResources(path)`, `fromResources(loader, path)`, `withCode(n, name, code)`; immutable), `CodeMigration`,
  `MigrationException` (new public types), `Database.migrate(Migrations)` -> `CompletableFuture<Integer>` (versions applied).
  Package-private: `Migrator` (runs on the worker), `SqlScript` (line endings, SHA-256, statement splitting).
- **Finding the files: the plugin's class loader, no build-time index.** `fromResources(path)` takes the class loader of the
  calling class (`StackWalker`, not reflection, no static state), asks it for the folder URL and lists that one folder:
  `jar:` -> entries of that jar (own `JarFile`, closed again), `file:` -> directory (dev runs). Chosen over a generated
  index because consumers need no extra Gradle step and a forgotten index cannot ship a stale list. Needs the folder entry in
  the jar (Gradle `jar`/`shadowJar` write it); the error says so. Proven in the shaded example jar by `database.migrate`.
- Rules: `V<n>__<name>[.mysql|.sqlite].sql`; a bad `.sql` name, duplicate version, or override with another name fails at
  load (`MigrationException`); gaps and "no script for this dialect" fail in `migrate` before anything runs (checked there, not at load,
  because `withCode` may fill a gap). Checksum = SHA-256 of the chosen file, BOM and CRLF/CR normalised to LF, `{prefix}` not
  expanded; a code migration's checksum is that of `code:<n>:<name>` (so renaming it counts as a change). Checksum mismatch or
  a DB version above the known ones: fail, nothing runs. Each missing version runs in its own transaction together with its
  `schema_history` row; a failure stops the run, earlier versions stay. Failures are logged once (message only, no stack with the driver text).
- Carry-over: `DbFuture.join()/get()/get(timeout)` on a worker thread of the same `Database` throw `IllegalStateException`
  ("waiting on the database from its own worker would deadlock; chain with thenCompose/thenAccept"). Worker threads are a private
  `WorkerThread` carrying an instance-owned owner token, so a worker of another `Database` may still wait. `DatabaseException`
  Javadoc warns that logging the cause prints the driver text.
- Example: `db/migrations` (V1, V2 + `V2__mail.mysql.sql`) and self-test `database.migrate`.

**Tests**
- `MigrationContract` -> `SqliteMigrationTest` + `MysqlMigrationTest`: order, history, re-run/incremental, checksum change,
  downgrade, override per dialect + checksum of chosen file, only-other-dialect file, code migration (order, rollback, rename),
  splitting with comments/quotes, CRLF vs LF, gap, failure rollback (DML on both, DDL rollback SQLite only), logged once, closed DB.
- `MigrationsLoadingTest` (jar and folder listing, caller's loader, bad names, duplicates, `withCode` conflicts, gap), `SqlScriptTest`.
- `DatabaseQueryContract.joinAndGetOnTheDatabasesOwnWorkerFailAtOnce...` on both dialects: join/get/get(timeout), in a callback and in a transaction body.

**Notes**
- MySQL: DDL auto-commits, documented in the `Migrations` Javadoc (failed migration may leave earlier DDL; keep them small).
- Trigger bodies with `;` at a line end cannot be split (documented); `/* */` comments and backslash escapes in strings are not handled.


## Review (planner, 2026-10-20)
Approved. Smoke 73/73; MySQL ran (17 migration + 20 query tests). Accepted: `MigrationException`, `Future<Integer>`
(versions applied), caller class loader via `StackWalker` + explicit-loader overload, gap check at migrate time (withCode
can fill gaps), code-migration checksum = version + name, deadlock guard on own worker for join/get/get(timeout).
Known limits (documented): no `;` inside trigger bodies, no `/* */`, no backslash escapes; MySQL DDL is not transactional.
