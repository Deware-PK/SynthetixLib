# synthetixlib-database

Package: `com.github.dewarepk.synthetixlib.database` (only this module may use it). Design: docs/design/database.md,
ADR 0014. Depends on core + config (`DatabaseSettings` lives in product config files).

Status: **complete, 0.5.0** (P2-24 module + loader + settings, P2-25 queries, P2-26 migrations, P2-27 shutdown + copy,
P2-28 demo + MySQL suite). Demo: `synthetixlib-example` (`ClaimStore`, `/example airdrop claim|stats`, `/example db copy-to-mysql`).

Types
- `Database` (public interface): `create(lib, settings)` (attached to `lib`), `open(lib, settings)` (not attached),
  `dialect()`, `close()`, and the statements `query`, `queryOne`, `update`, `batch`, `transaction`, `upsert` (all return
  `CompletableFuture`, completed on the worker). Implemented by package-private `DatabaseImpl`, which owns the pool and
  the worker (`ThreadPoolExecutor`, threads `<plugin>-db-<n>`, non-daemon, size = pool size).
- Public helpers of the statements: `Row` (typed getters + `...OrNull`), `RowMapper`, `Transaction` (same methods, run now
  on the transaction's connection), `TransactionBody`, `DatabaseException` (unchecked wrapper of `SQLException`, carries `sql()`).
- Package-private: `Params` (supported parameter types -> JDBC values, converted **at the call** so a bad type throws
  before queueing and an `ItemStack` is read on the caller's thread), `Statements` (runs statements on one connection),
  `ResultRow` (the `Row`), `Upsert` (per-dialect statement text, identifiers validated and backtick-quoted),
  `TransactionImpl`, `DbFuture` + `ServerThreadGuard` (warn when a server thread `join()`/`get()`s a result).
- `DatabaseSettings` (public record + `Sqlite`, `Mysql`, `Pool`): the YAML of design section 2. `register(codecs)` makes the type
  read/written as `sqlite` / `mysql`; without it `SQLITE` is written and either case is read. `Mysql#toString` hides the password.
- `SynthetixLibraries.database(classpath)`: for the product's `PluginLoader`; adds HikariCP via Paper's mirror at the
  version of `LibraryVersions.HIKARICP`, a class **generated** from `gradle/libs.versions.toml` by `generateLibraryVersions`
  (so the catalog is the only place; never type the version by hand).
- Package-private: `ConnectionPlan` (settings -> URL, account, pool size, driver properties; validates table prefix, host,
  schema, port; unit-tested without a connection), `ConnectionSource` + `HikariConnectionSource` (the **only** class that
  touches HikariCP, ArchUnit enforces it, so a missing HikariCP is one `NoClassDefFoundError` caught in `DatabaseImpl.open`).

Rules specific to database
- HikariCP is `compileOnly`; drivers are never fetched: Paper's sqlite-jdbc / mysql-connector-j are used. The test-only
  driver versions in the catalog must equal Paper's (`libraries/` of a Paper run directory).
- SQLite: pool 1 and worker 1; the four PRAGMAs are passed to the driver as connection properties (applied to every
  connection it opens; sqlite-jdbc runs only the first statement of a multi-statement `connectionInitSql`).
- MySQL 8+ without SSL needs `allowPublicKeyRetrieval: 'true'` under `mysql.properties` (the error message says so).
- Never log the password (settings `toString`, error messages) or statement parameters. A failed call is logged once, with
  its SQL, in `DatabaseImpl.logFailure`. The driver's own message is repeated only for SQLite: MySQL quotes data in it
  ("Duplicate entry 'x'", "syntax ... near '... name = 'x''"), so there only SQLState + vendor code are shown (the
  `SQLException` stays as the cause for the caller). `parametersNeverReachTheLog...` guards this on both dialects.
- Queries run on the worker; the SQLite worker is one thread, so waiting for a `Database` future inside a callback or a
  `TransactionBody` deadlocks. Inside a body use `tx`.
- Server-thread wait guard: `Database.open(lib, ...)` builds a `ServerThreadGuard` (`Bukkit.isPrimaryThread()`; waiting is
  allowed until the first global tick after the call, and once `lib` is closed or the plugin disabled). Unit tests pass a
  fake predicate through `DatabaseImpl.open(..., guard)`.
- MySQL default `properties` are empty (driver `sslMode=PREFERRED`); the test helper adds nothing.
- Query tests are written once in `DatabaseQueryContract` and run by `SqliteQueryTest` and `MysqlQueryTest`.
- `{prefix}` values are validated in `ConnectionPlan` (`[A-Za-z0-9_]{0,32}`) because they are pasted into SQL.
- MySQL tests: `@ExtendWith(MysqlExtension.class)` + a `Mysql` parameter. Skipped unless Gradle properties `mysqlUrl`,
  `mysqlUser`, `mysqlPassword` exist (the owner's `~/.gradle/gradle.properties`; handed to the test JVM by an argument
  provider so the password is no task input). With them the test task is never up to date or cached. Random table
  prefix `t<random>_` per class; its tables are dropped afterwards. Use only that prefix.
- Shutdown (design section 6, SnapPlug bug #1), `DatabaseImpl.close()`: sets `closed` (new calls: future failed with
  `DatabaseClosedException`, an `IllegalStateException`, never thrown at the call), `worker.shutdown()`, `awaitTermination
  (shutdownTimeout)`; on timeout `abandon()` logs the count + SQL of what is in `unfinished` (queued + running `Pending`
  calls, never parameters), `shutdownNow()` and fails the drained `Pending`s; then the pool closes. Race: a call is either
  rejected by the executor (-> closed future) or queued before it stopped accepting (-> runs, or is failed by `abandon`);
  never dropped. The first caller closes, later/other-thread callers wait on `terminated`; a statement closing its own
  database finishes on a thread `<plugin>-db-close`. A `Pending` leaves `unfinished` *before* its future completes.
  Tests: `DatabaseShutdownContract` (SQLite + MySQL): 1000 queued writes, long transaction, timeout, race of 16 callers.
- `Copy.tables(from, to, names).batchSize(n).run()` -> `Copy.Report` (rows per table, names as given). Names are bare
  (`{prefix}` optional); each side adds its own prefix. `PagedTable` pages by primary key (row value `(a, b) > (?, ?)`, so
  composite keys work; `rowid` on SQLite without PK; MySQL without PK is refused), reads/writes raw `getObject`/`setObject`
  values with plain `INSERT` (existing rows fail the page; one transaction per page on the target). The page chain is
  async (`thenCompose`), so two databases never block each other's worker. `DatabaseImpl.withConnection/withTransaction`
  are the package-private hooks it uses. Tests: `CopyContract` for SQLite->SQLite, SQLite<->MySQL, MySQL->MySQL.
- MySQL needs `` `table`.* `` (not a bare `*`) after other select items.
- Migrations (design section 5): `Migrations` (public, immutable; `fromResources` lists the folder through the caller's class loader:
  `jar:` entries or a `file:` directory, never a class-path scan), `CodeMigration`, `MigrationException`, `Database.migrate`.
  Package-private `Migrator` runs on the worker (validates all, then one transaction per missing version incl. its
  `{prefix}schema_history` row) and `SqlScript` splits/normalises/checksums. Gaps are checked in `migrate` (code can fill them),
  everything else about the files at load. Migration tests: `MigrationContract` (SQLite + MySQL), each test its own table prefix
  (`schema_history` is per prefix).
- Waiting for a `Database` future on that database's own worker throws at once (`DbFuture`, `WorkerThread` owner token).

MySQL test setup (owner's machine)
- A throw-away MySQL in Docker, kept running:
  `docker run -d --name synthetix-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=<root> -e MYSQL_DATABASE=synthetix_test -e MYSQL_USER=synthetix -e MYSQL_PASSWORD=<pw> mysql:8`
- Three properties in `%USERPROFILE%\.gradle\gradle.properties` (never in the repo):
  `mysqlUrl=jdbc:mysql://127.0.0.1:3306/synthetix_test`, `mysqlUser=synthetix`, `mysqlPassword=<pw>`.
  Without all three the `Mysql*Test` classes are skipped (CI). With them `check` runs every database test on SQLite and MySQL.
- Every database test is a `*Contract` run by a `Sqlite*Test` and a `Mysql*Test`; copy tests cover all four directions.
