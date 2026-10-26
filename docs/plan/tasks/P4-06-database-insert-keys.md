# P4-06 database: `insert` returns the generated key
Status: review · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-database · Depends on: — · Design: database.md §3, §4 (+ this task, §14 "0.9.1 additions")
Requested by: InteractiveAuction 2.0 (every lot, bid, payment and mail insert needs its new id), lib gap L-6

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/database.md (§3, §4, §7, §10, §12, §13), skills/synthetixlib/references/database.md and
docs/plan/tasks/P4-06-database-insert-keys.md, then implement the task. Run `gradlew.bat check smokeTest` (MySQL tests
run when my user gradle.properties has mysqlUrl/mysqlUser/mysqlPassword: make sure they ran, not skipped), tick the boxes,
fill in "Result", set Status: review. Do not commit.

## Goal
Today `update` returns only the row count, so a consumer that inserts a row and needs its id must run
`SELECT last_insert_rowid()` / `SELECT LAST_INSERT_ID()` itself and switch on the dialect (IA does this in
`store/Sql.lastInsertId`). The lib should hand back the generated key, the same way on SQLite and MySQL.

## Spec
### API
```java
// Transaction
/** Runs one INSERT that creates exactly one row and returns its generated key (an auto-increment / INTEGER PRIMARY KEY id). */
long insert(String sql, Object... params);

// Database
/** Like Transaction#insert, as its own auto-commit statement. */
CompletableFuture<Long> insert(String sql, Object... params);
```
- Same parameter binding, `{prefix}` replacement, logging and failure rules as `update`.
- Implementation: `prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)` + `getGeneratedKeys()`. If the driver returns
  no key (check Paper's bundled sqlite-jdbc on both 1.21.11 and 26.3, and mysql-connector-j), fall back to the dialect query
  (`last_insert_rowid()` / `LAST_INSERT_ID()`) on the **same connection** inside the same call; document which path each
  driver takes in the Result.
- Errors: the statement changed ≠ 1 row, or no key can be found → `DatabaseException` with the SQL (never the params)
  and the message "insert must create exactly one row with a generated key".
- Not for `upsert` or `batch` (no keys there; say so in the Javadoc and the reference).

### Not in this task
Generated keys for `batch`, composite / non-numeric keys, `RETURNING` clauses.

## Tests
- Contract tests (`DatabaseQueryContract`, run by `SqliteQueryTest` and `MysqlQueryTest`): two inserts in one transaction
  return two different, increasing ids that match `SELECT id`; `Database.insert` outside a transaction; an INSERT … SELECT
  that creates 0 rows → `DatabaseException`; 2 rows → `DatabaseException`; a rolled-back transaction leaves no row.
- Self-test `database.insert-key` (both versions): the same through the server's bundled sqlite-jdbc.

## Docs (same task)
- `skills/synthetixlib/references/database.md`: `insert` in the API table ("since 0.9.1"), a short example in Queries,
  and a pitfall line "do not run `last_insert_rowid()` yourself; use `insert`".
- `docs/design/database.md`: §14 "0.9.1 additions". CHANGELOG "0.9.1". Example: `ClaimStore` uses `insert` if it needs an id.

## Acceptance criteria
- [x] `insert` returns the right key on SQLite and MySQL (contract tests ran on both, none skipped)
- [x] 0 / 2 rows → clear `DatabaseException`, nothing committed
- [x] self-test `database.insert-key` PASS on 1.21.11 and 26.3
- [x] reference, design §14, CHANGELOG updated; `checkSnippets` green

## Verify
gradlew.bat check smokeTest

## Result

`Database.insert` / `Transaction.insert` added (`Statements.insert`, `DatabaseException.of(sql, reason)` for the library's own check).
- Driver paths: sqlite-jdbc (the version in the catalog, same as Paper's on 1.21.11 and 26.3) and mysql-connector-j both
  return the key from `getGeneratedKeys()`; the `last_insert_rowid()` / `LAST_INSERT_ID()` fallback on the same connection
  exists but is not taken by either (checked with a throw-away probe, removed).
- `Database.insert` runs inside its own transaction, so an INSERT ... SELECT that creates 0 or 2+ rows commits nothing.
- Tests: 3 new contract tests (keys in tx and outside, 0 / 2 rows incl. inside a tx, rollback) in `DatabaseQueryContract`;
  `MysqlQueryTest` 23 tests, 0 skipped, `SqliteQueryTest` 23 tests; all other `Mysql*Test` ran, 0 skipped.
  Self-test `database.insert-key` PASS on 1.21.11 and 26.3.
- Docs: reference (API table, text + snippet `db-insert-key` from `DocExamples`, pitfall), design §14, CHANGELOG 0.9.1.
  `ClaimStore` left as is (it needs no id).
- Ran: `gradlew.bat check smokeTest` green (includes `checkSnippets`). Not committed.
