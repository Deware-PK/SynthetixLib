# P2-25 Database queries: binding, Row, transactions, batch, upsert, {prefix}
Status: done · Approved: yes · Model: sonnet · Effort: high
Module: synthetixlib-database · Depends on: P2-24 · Design: database.md §3, §4, §7

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/database.md and docs/plan/tasks/P2-25-database-queries.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- `query(sql, mapper, params...)` → `CompletableFuture<List<T>>`, `queryOne` → `CompletableFuture<Optional<T>>`,
  `update` → `CompletableFuture<Integer>`, `batch(sql, List<Object[]>)` → `CompletableFuture<int[]>` (rewrite-batched on
  MySQL), `transaction(fn)` (one connection, commit on return, rollback on any exception, nested `tx.query/update/batch`).
  Everything runs on the DB worker; futures complete there.
- Binding table of §3 (`String int long double boolean byte[] UUID(CHAR 36) Instant(epoch ms) Duration(ms) Enum(name)
  ItemStack(serializeAsBytes) null`); unknown type → IAE at the call (before queueing).
- `Row` getters + `...OrNull` of §3; wrong type / missing column → clear exception naming the column.
- `{prefix}` replaced in every statement. `upsert(table, keyColumns, values)` per dialect (§4).
- Errors: exception completes the future, logged once with the SQL, **never the parameters**.
- `join()`/`get()` on a server thread outside onEnable/onDisable → warning with stack: return futures that override
  `join/get` (a small `DbFuture extends CompletableFuture`) and check `Bukkit.isPrimaryThread()` /
  `lib.scheduler()` ownership. Allowed while the plugin is enabling/disabling.

- MySQL: every database unit test of this task also runs against MySQL when the Gradle properties `mysqlUrl`,
  `mysqlUser`, `mysqlPassword` exist (owner's user gradle.properties, Docker MySQL); skipped without them. Random table
  prefix per run, tables dropped afterwards. Never print the password. Say in Result whether the MySQL run happened.

- Carry-over from P2-24 review: `DatabaseSettings.mysql(...)` and the bundled default use **empty** `properties`
  (driver default `sslMode=PREFERRED` connects to a fresh MySQL 8.x/MariaDB). The YAML comment shows how to add
  `useSSL: 'false'` + `allowPublicKeyRetrieval: 'true'` for servers without TLS. Test: default settings connect to the
  Docker MySQL without extra properties (the test helper must not add any for this case).

## Tests
Unit (SQLite): every type round-trip incl. nulls, Row errors, transaction rollback keeps nothing, upsert insert+update,
batch 10 000 rows, `{prefix}`, params never in logs (capture handler), concurrent 8 threads × 100 updates all applied,
join warning (fake "server thread" predicate).
Self-test `database.query` (SQLite with Paper's driver: insert + query + transaction).

## Acceptance criteria
- [x] API matches database.md §3-§4 (deviations in Result)
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
Ran `gradlew.bat check smokeTest`: green (BUILD SUCCESSFUL; smoke 72/72 on Paper 1.21.11 and 26.3). Database unit tests: 79, all
passed, none skipped. **MySQL ran for real** (Docker MySQL, `mysqlUrl/User/Password` from the user gradle.properties): `MysqlQueryTest`
19 + `MysqlDatabaseTest` 5 + `MysqlExtensionTest` 3. `SqliteQueryTest` 19 run the same `DatabaseQueryContract`. Password never printed.

**Built** (all in `synthetixlib-database`)
- `Database`: `query`, `queryOne`, `update`, `batch`, `transaction`, `upsert` (all `CompletableFuture`, completed on the worker).
  Public helpers: `Row` (all getters + `...OrNull`), `RowMapper`, `Transaction` (+ `TransactionBody`), `DatabaseException`.
- `Params` converts the §3 types at the call (bad type -> `IllegalArgumentException` naming position + class, never the value,
  before queueing; an `ItemStack` is serialized on the caller's thread). `Row` errors: missing column / wrong type ->
  `IllegalArgumentException` naming the column; unexpected NULL -> `IllegalStateException` pointing to the `...OrNull` getter.
- `transaction`: one connection, commit on return, rollback on any `Throwable`; `tx` is dead after the body and bound to its thread.
  `batch` runs in one transaction (all rows or none). `{prefix}` replaced everywhere (also in `upsert`).
- `upsert`: `ON CONFLICT ... DO UPDATE` (SQLite) / `ON DUPLICATE KEY UPDATE` (MySQL); key-only map leaves an existing row alone.
  Table/column names must be plain identifiers (they are pasted into the SQL, backtick-quoted).
- `DbFuture` + `ServerThreadGuard`: `join()`/`get()`/`get(timeout)` (also on derived futures) on a server thread log a WARNING with the
  stack, unless waiting is allowed: until the first global tick after `Database.open/create`, after `lib.close()`, or once the plugin
  is disabled. Real predicate = `Bukkit.isPrimaryThread()`; unit tests inject a fake one.
- Default MySQL `properties` are now empty (carry-over), the YAML comment shows `{ useSSL: 'false', allowPublicKeyRetrieval: 'true' }`.
  `MysqlExtension` no longer adds any property: the default settings connect to the Docker MySQL (tested).
- Self-tests `database.query` (SQLite with Paper's driver: create, insert with ItemStack/UUID/Instant/Enum, queryOne, upsert, commit,
  rollback) and `database.join-guard` (real `Bukkit.isPrimaryThread()`: the warning fires on the global thread). Both servers pass.
  Their expected failures show up in the smoke log as one SEVERE stack ("roll me back") and one WARNING (guard); that is the test.

**Deviations / things to know**
- New public `DatabaseException extends RuntimeException` (unchecked wrapper of `SQLException`, `sql()` accessor), and public
  `Transaction` also has `upsert` (not in the spec list). Futures fail with `DatabaseException` for driver errors, with the original
  exception for mapper / body failures.
- **Logging finding (real leak, fixed):** MySQL's driver text quotes data (`Duplicate entry 'x'`, and syntax errors even echo
  `... near 'WHERE name = 'x''` with the bound value). So the logged/exception message contains the driver's text **only for
  SQLite**; for MySQL it is `SQLState`, vendor code and the SQL. The `SQLException` is still the cause for the caller. A failed call
  is logged once, at SEVERE, in `DatabaseImpl.logFailure` (test `parametersNeverReachTheLog...` runs on both dialects).
- A closed database completes new futures with `IllegalStateException("The database is closed")`; P2-27 replaces it with
  `DatabaseClosedException`.
- Waiting for a `Database` future inside a callback or `TransactionBody` deadlocks on SQLite (one worker); documented in the
  Javadoc of `Database` and `Transaction`, not guarded in code.
- `ItemStack` binding/reading needs a running server, so it is covered by the self-test, not the unit tests.
- 10 000-row batch: 0.05 s SQLite, 0.4 s MySQL (rewritten batch); `executeBatch` counts are returned as the driver reports them.


## Review (planner, 2026-10-20)
Approved. Smoke 72/72; MySQL ran for real (27 tests); one contract suite for both dialects. Good catch on MySQL error
texts quoting bound values: failures are logged with SQLState/vendor code/SQL only, the driver exception is not passed to
the logger. Accepted additions: public `DatabaseException`, `Transaction#upsert`.
Carried into P2-26: the documented deadlock (waiting on a future from the DB worker itself) must fail fast in code.
