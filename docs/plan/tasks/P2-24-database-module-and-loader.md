# P2-24 Database module, HikariCP loader helper, settings, Database.create (+ class-loader proof)
Status: done · Approved: yes · Model: sonnet · Effort: high
Module: synthetixlib-database · Depends on: P2-23a · Design: database.md §1, §2, §7, §10 (smoke)

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/database.md and docs/plan/tasks/P2-24-database-module-and-loader.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.
**Do the class-loader proof first** (see Spec, first bullet) and stop with a report in Result if it fails — the rest of
the design depends on it.

## Spec
- **Proof first:** in the example plugin (`paper-plugin.yml` already) add a `PluginLoader` that adds HikariCP via
  `MavenLibraryResolver` (Paper mirror constant). Self-test `database.driver`: a HikariCP pool on
  `jdbc:sqlite:<plugin folder>/selftest.db` using **Paper's** sqlite-jdbc (no driver dependency of ours), `SELECT 1`,
  close. Must pass on 1.21.11 and 26.3. Record in Result which class loader the driver came from and whether
  `DriverManager.getDriver(url)` sees it from plugin code. If it does not: try `HikariConfig#setDriverClassName`
  only if that needs no reflection in our code; else stop and report options.
- New module `synthetixlib-database` (AGENTS.md checklist): `api(project(":synthetixlib-config"))`, HikariCP
  `compileOnly` + `testImplementation`, `sqlite-jdbc` `testImplementation` only; versions in the catalog (latest stable
  HikariCP on Maven Central for Java 21). ArchUnit: HikariCP allowed only in this module (ADR 0014).
- `SynthetixLibraries.database(PluginClasspathBuilder)` (public, in this module): adds HikariCP at the catalog version
  (generate the version into a constant at build time or a resource; no hard-coded duplicate).
- `DatabaseSettings` record + nested records exactly as database.md §2 (comments, durations), `sqlite(file)` /
  `mysql(...)` factories, codec registered so products use it inside their config records.
- `Database.create(lib, settings)` / `Database.open(lib, settings)` (open = not attached): builds the HikariCP config
  (SQLite: pool 1 + the 4 PRAGMAs as connection init; MySQL: §7 properties + user `properties`), owns a worker
  `ExecutorService` (named `<plugin>-db-<n>`, size per §7). Fail fast with clear messages: HikariCP missing (no
  reflection: catch `NoClassDefFoundError` at the first Hikari touch), no driver for the URL, cannot connect.
  `close()` for now: shut the worker down then the pool (full bug #1 logic is P2-27).
- `db.dialect()`.
- MySQL test support (used from here on): a JUnit extension / helper in the module's tests that reads the Gradle
  properties `mysqlUrl`, `mysqlUser`, `mysqlPassword` (passed to the test JVM as system properties by the build),
  skips MySQL cases when absent, gives each run a random table prefix and drops its tables at the end. `mysql-connector-j`
  as `testImplementation`. First MySQL test: `Database.create` connects and `SELECT 1` works. Never log the password.

## Tests
Unit (real SQLite in a temp dir): settings defaults + YAML round-trip, SQLite PRAGMAs applied (`PRAGMA journal_mode`
returns `wal`), pool size forced to 1 for SQLite, MySQL URL built from settings (no connection), missing driver message.
Self-test `database.driver` (above) and `database.create` (Database.create + close via lib).

## Acceptance criteria
- [x] class-loader proof passes on 1.21.11 and 26.3 (finding written in Result)
- [x] no reflection / Class.forName in our code; ArchUnit green, rules only tightened
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
Ran `gradlew.bat check smokeTest`: green (BUILD SUCCESSFUL; smoke 70/70 on Paper 1.21.11 and 26.3). Database unit tests: 35
(ConnectionPlan 8, DatabaseSettings 7, SQLite 12, MySQL 5, MysqlExtension 3), all passed, none skipped.

**Class-loader proof (done first): passes on 1.21.11 and 26.3.**
- `DriverManager.getDriver("jdbc:sqlite:...")` called from plugin code returns `org.sqlite.JDBC` (Paper's 3.49.1.0),
  loaded by Paper's server library `java.net.URLClassLoader` (not the plugin loader, not the system loader).
- HikariCP 7.1.0 comes from the `PluginLoader` library loader (`BytecodeModifyingURLClassLoader` on 1.21.11, a plain
  `URLClassLoader` on 26.3), the parent of `PaperPluginClassLoader`; the jar is `libraries/com/zaxxer/HikariCP/7.1.0/` in the run dir.
- Hikari with only `setJdbcUrl` (no driver class name) opened the pool and `SELECT 1` worked. So no `setDriverClassName`
  and nothing to add for the drivers; `DriverManager.getDriver(url)` is a valid up-front check from plugin/library code.
- Caveat found by the "HikariCP hidden" test: `DriverManager.getDriver` only returns a driver whose class the *caller's*
  loader sees as the same class, so the check must run from code on the plugin loader (it does).

**Built**
- Module `synthetixlib-database` (settings, archtest, example wired). Catalog: `hikaricp 7.1.0` (latest on Central; compileOnly
  + test), `sqlite-jdbc 3.49.1.0` and `mysql-connector 9.2.0` (test only, equal to the versions Paper ships).
- `SynthetixLibraries.database(classpath)`; the version comes from a `LibraryVersions` class generated from the catalog
  (`generateLibraryVersions`), no hard-coded copy. The example's `ExampleLoader` uses it.
- `DatabaseSettings` + `Sqlite`/`Mysql`/`Pool` records, `sqlite(file)`, `mysql(...)`, `withTablePrefix`. `Dialect` enum.
- `Database.create` / `open` / `dialect()` / `close()`; `DatabaseImpl` owns pool + worker (`<plugin>-db-<n>`, non-daemon).
  Fail-fast messages: HikariCP missing (catch `NoClassDefFoundError` at the first Hikari touch, tested in a class loader that
  hides `com.zaxxer`), no driver (names the jar), cannot connect (URL + root cause, never the password).
- ArchUnit: new rule "HikariCP only in `database.HikariConnectionSource`" + a fixture proving it fires. No rule weakened.
- MySQL test support: `MysqlExtension` (skips without the properties, random `t<random>_` prefix, drops its tables after the class;
  checked: `SHOW TABLES` empty afterwards). The build passes the properties through an argument provider (password is no task
  input); with them set the test task is never up to date / cached. MySQL ran here (5 tests incl. `SELECT 1`, utf8mb4, wrong password).
- Self-tests `database.driver` and `database.create` (second `SynthetixLib` instance: create, 1 worker thread, `close()` of the lib ends it).

**Deviations / things to know**
- SQLite PRAGMAs are passed as sqlite-jdbc connection properties (`journal_mode`, `busy_timeout`, `foreign_keys`, `synchronous`)
  instead of `connectionInitSql`: Hikari allows only one init statement and sqlite-jdbc runs only the first of several. Tested with
  `PRAGMA ...` on the live connection (wal / 5000 / 1 / 1).
- No codec had to be registered for the records: the config module maps public records automatically. `DatabaseSettings.register(codecs)`
  only makes the type read/written as `sqlite` (design) instead of `SQLITE`; reading accepts both cases either way.
- `Database` is a public interface with static `create`/`open` (impl package-private). Failures are plain `IllegalArgumentException` /
  `IllegalStateException` (no new exception type before P2-27's `DatabaseClosedException`).
- Table prefix (`[A-Za-z0-9_]{0,32}`), host, schema and port are validated in `ConnectionPlan`, because they end up in SQL or the URL.
- MySQL 8.4 (the Docker one) with `useSSL=false` needs `allowPublicKeyRetrieval=true`, otherwise "Public Key Retrieval is not
  allowed". Not a library default (security trade-off); the test helper sets it and the connect error now says what to set.
- `close()` is the P2-24 minimum (shutdown, `awaitTermination(shutdownTimeout)`, `shutdownNow`, then the pool); bug #1 proper is P2-27.
- ADR 0014 untouched ("keep versions in sync" is now automatic through the generated class; the sentence could be updated by you).


## Review (planner, 2026-10-20)
Approved. Class-loader proof passed on both servers (Paper's sqlite-jdbc seen from plugin code, HikariCP 7.1.0 via
PluginLoader, no setDriverClassName). Smoke 70/70; MySQL ran for real (5 tests). Checked: password never a task input
(argument provider), hidden in `DatabaseSettings`/`ConnectionPlan.toString()`, nothing in the repo. Accepted: PRAGMAs as
driver properties, no codec registration needed, IAE/ISE instead of new exception types, validation in ConnectionPlan.
Carried into P2-25: the MySQL default `properties: {useSSL: 'false'}` breaks a fresh MySQL 8.x (needs
allowPublicKeyRetrieval) — default must be empty so the driver's own `sslMode=PREFERRED` works out of the box.
