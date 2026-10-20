# P2-27 Database shutdown (bug #1), Database.open, Copy
Status: done · Approved: yes · Model: sonnet · Effort: high
Module: synthetixlib-database · Depends on: P2-26 · Design: database.md §6, §8

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/database.md and docs/plan/tasks/P2-27-database-shutdown-and-copy.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- `close()` exactly as §6: stop accepting (new calls → future failed with `DatabaseClosedException`, no throw), await
  queued + running work up to `shutdown-timeout`, then log the count of unfinished statements (SQL only) and interrupt,
  then close the pool. Idempotent, safe from any thread, called by `lib.close()` for `create`, by the owner for `open`
  (`Database` is `AutoCloseable`).
- Race: a call that passed the "open" check while close starts must either run fully or fail its future — never be
  dropped silently. Test it.
- `Copy.tables(from, to, tables).batchSize(n).run()` → future of a report (rows per table): pages by primary key /
  rowid, one transaction per page on the target, any dialect pair, `{prefix}` of each side.

- MySQL: every database unit test of this task also runs against MySQL when the Gradle properties `mysqlUrl`,
  `mysqlUser`, `mysqlPassword` exist (owner's user gradle.properties, Docker MySQL); skipped without them. Random table
  prefix per run, tables dropped afterwards. Never print the password. Say in Result whether the MySQL run happened.

## Tests
Unit (SQLite): 1000 queued writes + immediate close → reopen has 1000 rows; close during a long transaction waits;
timeout path logs and interrupts; calls after close fail; race test (16 threads calling while close runs: every future
completes, ok or `DatabaseClosedException`); Copy SQLite → SQLite with 10 000 rows and two tables, report counts.
Self-test `database.shutdown` (writes queued, `lib.close()` order: database closes before scheduler, rows present after).

## Acceptance criteria
- [x] bug #1 test green (1000/1000)
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
`gradlew.bat check smokeTest`: BUILD SUCCESSFUL; smoke 75/75 PASS on Paper 1.21.11 and 26.3. **MySQL run happened**
(Docker MySQL via the user's `gradle.properties`; the new shutdown and copy tests ran there, 0 skipped).
Database module: 182 tests green; the database tests were repeated 4x in a row without a failure.

- **Shutdown** (`DatabaseImpl.close`, design §6): new calls get a future failed with the new public
  `DatabaseClosedException` (extends `IllegalStateException`, so the old "closed" check still holds); accepted calls run
  for up to `shutdown-timeout`; on timeout it logs the count and the SQL (never parameters), interrupts, fails the
  never-started futures, gives interrupted statements 2 s, then closes the pool. Idempotent; a second/other-thread
  caller waits until the first has finished; closing from the database's own worker does not deadlock. Race-free by
  construction: the executor either rejects a call (closed future) or has queued it (runs, or is failed on abandon).
- **Copy**: `Copy.tables(from, to, names).batchSize(n).run()` -> `CompletableFuture<Copy.Report>` (`rows()`, `rows(t)`,
  `total()`), `PagedTable` does the paging (public API added: `Copy`, `Copy.Report`, `DatabaseClosedException`).
  Names are bare (`{prefix}` optional), each side adds its own prefix; keyset paging by primary key (composite ok), SQLite
  without PK uses `rowid`, MySQL without PK is refused; plain `INSERT`, one transaction per page.
- **Tests**: `DatabaseShutdownContract` (SQLite + MySQL): 1000 writes + immediate close -> reopen 1000, long
  transaction, concurrent closers, close from the worker, all call kinds after close, timeout path (log count, interrupt,
  no parameters in log), race of 16 threads (every future ok or `DatabaseClosedException`, stored rows == ok futures).
  `CopyContract` for SQLite->SQLite, SQLite->MySQL, MySQL->SQLite, MySQL->MySQL: 10 000 + 2 500 rows with nulls/blobs/
  unicode, composite key with page size 7, empty table, no-PK table, duplicate row, missing table, closed target, bad args.
- **Self-tests** in the example: `database.shutdown` (300 queued writes, `lib.close()`, all done, the scheduler still
  accepts callbacks during the drain = database closes before the scheduler, second instance finds 300 rows) and
  `database.copy` (extra, Paper's driver for the primary-key metadata).
- **Also changed**: two older tests in `DatabaseQueryContract` (`futuresCompleteOnTheDatabaseWorker`,
  `joinAndGetOnTheDatabasesOwnWorker...`) were flaky (a callback added after the future finished runs on the caller);
  the mapper now waits until the callback is registered. MySQL needs `` `t`.* `` after other select items (found by the
  MySQL run). Module `AGENTS.md` updated. Nothing committed.


## Review (planner, 2026-10-20)
Approved. Bug #1 fixed and proven on both dialects (1000/1000 after immediate close; 16-thread race: stored rows ==
successful futures). Smoke 75/75. Accepted: `DatabaseClosedException extends IllegalStateException`, Copy refuses MySQL
tables without a primary key, the extra `database.copy` self-test, the two flaky-test fixes. The SEVERE "roll me back"
line in smoke logs is the intended rollback test (`DatabaseSelfTests`, confirmed).
