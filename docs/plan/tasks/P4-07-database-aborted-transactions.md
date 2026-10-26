# P4-07 database: abort a transaction on purpose, without a SEVERE log
Status: todo · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-database · Depends on: — (can run in parallel with P4-06, same module: run them one after the other) · Design: database.md §3, §6 (+ §14 "0.9.1 additions")
Requested by: InteractiveAuction 2.0 (stale writes: lot no longer RUNNING, bid no longer HELD), lib gap L-7

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/database.md (§3, §6, §7, §12–§14), skills/synthetixlib/references/database.md and
docs/plan/tasks/P4-07-database-aborted-transactions.md, then implement the task. Run `gradlew.bat check smokeTest` (MySQL
tests must run, not be skipped), tick the boxes, fill in "Result", set Status: review. Do not commit.

## Goal
A transaction body that finds the data is not in the state it expected (another player bid first, the lot just ended)
must roll back and tell its caller. That is a normal outcome, not a bug. Today the only way is to throw, and
`DatabaseImpl.logFailure` logs every non-driver exception as SEVERE **with a stack trace** ("Database call failed in a
transaction"). `DatabaseException` cannot be created by a consumer (private constructor). Result: a busy auction prints
red stack traces in the console for every lost race, and server owners report it as a crash.

## Spec
### API
1. **Roll back and return a value** (no exception at all):
   ```java
   // Transaction
   /** Marks this transaction to roll back when the body returns; the future still completes with the body's result. */
   void rollbackOnly();
   /** @return true after rollbackOnly() was called */
   boolean isRollbackOnly();
   ```
   The body returns normally (e.g. a sealed `Outcome.Stale(...)`), the lib rolls back instead of committing and completes
   the future normally with that value. Nothing is logged.
2. **Roll back and fail the future** (for callers that prefer an exception):
   ```java
   /** Thrown by a transaction body to roll back on purpose. Logged at FINE, never SEVERE; the future fails with it. */
   public class TransactionAbortedException extends RuntimeException {
       public TransactionAbortedException(String message) { … }
       public TransactionAbortedException(String message, @Nullable Throwable cause) { … }
   }
   ```
   Not final: a product may subclass it (IA's `StoreException` will). No stack trace is filled for the plain constructor
   (`writableStackTrace = false`) — it is control flow, keep it cheap.
- `logFailure`: `TransactionAbortedException` (and subclasses) → `Level.FINE` one line "transaction aborted: <message>";
  everything else unchanged (driver failures SEVERE without the driver text, other exceptions SEVERE with stack).
- Calling `rollbackOnly()` and then throwing: the exception wins (normal failure path). `rollbackOnly()` outside the body
  (after it returned) → `IllegalStateException`.
- Works the same in `Database.standalone`.

### Not in this task
Savepoints / partial rollback, retry helpers.

## Tests
- Contract tests on SQLite and MySQL: `rollbackOnly()` → future completes with the value, no row written; throwing
  `TransactionAbortedException` (and a subclass) → future fails with it, no row written, the log has no SEVERE record
  (capture the logger), one FINE record; a plain `RuntimeException` still logs SEVERE with stack (unchanged).
- Self-test `database.rollback-only` (both versions).

## Docs (same task)
- reference `database.md`: "Stale data: roll back on purpose" section with the two styles and when to use which
  (prefer `rollbackOnly()` + a result type for expected races); API table "since 0.9.1"; pitfall "do not throw
  RuntimeException for an expected race: it logs SEVERE".
- New rule `skills/synthetixlib/rules/database-expected-race-no-severe.md` (MEDIUM) with a ❌/✅ pair.
- design §14, CHANGELOG "0.9.1".

## Acceptance criteria
- [ ] both styles roll back on SQLite and MySQL (contract tests ran on both)
- [ ] aborted transactions never log SEVERE; other failures still do
- [ ] self-test PASS on 1.21.11 and 26.3
- [ ] reference, rule, design §14, CHANGELOG updated; `checkSnippets` green

## Verify
gradlew.bat check smokeTest

## Result
