# P4-03 database: `Database.standalone` for unit tests and tools
Status: review · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-database · Depends on: — · Design: database.md §3, §10
Requested by: InteractiveAuction 2.0 (IA2-03 store tests), lib gap L-5 part 1

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/database.md (§3, §7, §10, §12) and docs/plan/tasks/P4-03-database-standalone.md, then
implement the task. Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Goal
A consumer can test its own SQL (stores, transactions, migrations) in plain JUnit without a Paper server. Today the only
way to get a `Database` is `create/open(lib, settings)`, which needs a `JavaPlugin`; the lib's own tests use the
package-private `DatabaseImpl.open(name, dir, logger, settings)`.

## Spec
- `public static Database standalone(String name, Path directory, Logger logger, DatabaseSettings settings)` on
  `Database`: same pool, worker, migrations, shutdown as `open`, not attached to anything (caller closes it; it is
  `AutoCloseable` already — check). `name` is used for the worker thread names (`<name>-db-<n>`); `directory` is where a
  relative SQLite file goes. Javadoc: "for unit tests and command-line tools; in a plugin use `create`".
- HikariCP and the driver must be on the classpath (the plugin's `PluginLoader` is not there in a test): a missing class →
  `DatabaseException` with "add com.zaxxer:HikariCP and org.xerial:sqlite-jdbc (or mysql-connector-j) as
  testImplementation, versions in references/database.md". The reference lists the exact versions from the catalog.
- **ItemStack parameters / `row.itemStack`** need a running server (item serialization). Without one (`Bukkit.getServer()
  == null`) → `IllegalStateException("ItemStack columns need a running Paper server: test them in a self-test")` at the
  call, never an NPE deep inside Paper. Same for reading.
- `join()` warnings: a standalone database has no server thread; the "join on a server thread" check must not fire or
  throw in plain JUnit.
- ArchUnit: `standalone` must not be used by the library's own main code (only tests/example) — add a rule if easy,
  else note in Result.

## Tests
- Unit: the existing `SqliteDatabaseTest` cases that matter also run through `Database.standalone` (open, migrate,
  query, transaction, close waits for writes); missing driver message (use a class loader without sqlite-jdbc, like
  `HiddenHikariProbe`); ItemStack parameter without a server → the clear `IllegalStateException`.
- Template: add `src/test/java/.../StoreTestExample.java` **only in the docs snippet**, not in the template project (the
  template has no database); a recipe in `references/recipes.md` "Recipe 4: unit-test your store" with the Gradle lines
  (`testImplementation` JUnit 5, HikariCP, sqlite-jdbc) and a test using `@TempDir` + `standalone` + `migrate`.

## Docs (same task)
- reference `database.md`: `standalone` in the API table (since 0.9.0), "Testing" section, the ItemStack limit.
- `docs/design/database.md`: §13 "0.9.0 additions". CHANGELOG "0.9.0".

## Acceptance criteria
- [x] a JUnit test with only `@TempDir` + `Database.standalone` migrates and queries SQLite (unit)
- [x] clear errors for a missing driver and for ItemStack without a server (unit)
- [x] recipe 4 compiles from its snippet source; `checkSnippets` green
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
`Database.standalone` added (via `DatabaseImpl.openStandalone`; own missing-HikariCP / missing-driver messages naming the testImplementation deps).
`Params.requireServer()` guards ItemStack parameters and `row.itemStack` (`Bukkit.getServer() == null`). The guard is already off
(`ServerThreadGuard.disabled()`). ArchUnit rule `libraryCodeDoesNotUseStandaloneDatabases` added. Tests: `StandaloneDatabaseTest`,
`SqliteDatabaseTest#standaloneWithoutHikariCp...`. An `ItemStack` cannot be constructed without a server, so the parameter check is
tested through `Params.requireServer()`; the read side through a `x'00'` blob. Recipe 4 snippet comes from
`synthetixlib-example/src/test/.../StoreTestExample` (example got test deps HikariCP + sqlite-jdbc). Docs: reference, design §13, CHANGELOG.
