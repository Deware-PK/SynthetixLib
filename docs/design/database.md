# synthetixlib-database — API design

Status: **Implemented (0.5.0)** — approved 2026-10-20 (all §11 defaults accepted); deviations in §12 · Task: P1-04 · Implementation: P2-24..P2-28 · Date: 2026-10-20
Package root: `com.github.dewarepk.synthetixlib.database` · Depends on: core, config, paper-api, HikariCP (`compileOnly`,
loaded at runtime by the consumer's `PluginLoader`, ADR 0014). JDBC drivers: the ones Paper ships (sqlite-jdbc, mysql-connector-j).

Async SQL for SQLite and MySQL behind one small API: settings from config, a HikariCP pool, typed parameters and rows,
versioned migrations per dialect, and a shutdown that never loses a write (SnapPlug bug #1). Built for
InteractiveAuction 2.0 (incl. importing 1.0 data from SQLite or MySQL) and Farm Workers.

---

## 1. Loading HikariCP (consumer's `PluginLoader`)

```java
// src/main/java/.../AuctionLoader.java — referenced from paper-plugin.yml: loader: com.example.auction.AuctionLoader
public final class AuctionLoader implements PluginLoader {
    @Override public void classloader(final PluginClasspathBuilder classpath) {
        SynthetixLibraries.database(classpath);   // adds com.zaxxer:HikariCP:<catalog version> via Paper's Maven mirror
    }
}
```
- Uses `MavenLibraryResolver` with Paper's mirror constant (ADR 0009). Drivers are **not** fetched (ADR 0014 amendment).
- `Database.create` fails fast with a clear message if HikariCP is missing ("add SynthetixLibraries.database(...) to your
  PluginLoader") or if no driver accepts the URL (`DriverManager.getDriver`), naming the jar to add.

## 2. Settings (part of the consumer's config, editable by the buyer)

```java
record Settings(@Comment("Database for auctions") DatabaseSettings database, ...) {
    static Settings defaults() { return new Settings(DatabaseSettings.sqlite("auction.db"), ...); }
}
```
```yaml
database:
  type: sqlite              # sqlite | mysql
  table-prefix: 'ia_'
  sqlite:
    file: auction.db        # relative to the plugin folder
  mysql:
    host: localhost
    port: 3306
    database: minecraft
    username: root
    password: ''
    properties: {}          # extra JDBC URL parameters, e.g. { useSSL: 'false', allowPublicKeyRetrieval: 'true' }
  pool:
    size: 10                # ignored for sqlite (always 1, see §7)
    connection-timeout: 10s
    max-lifetime: 30m
  shutdown-timeout: 10s     # how long close() waits for queued writes (§6)
```
`DatabaseSettings` is a record from this module with a config codec, so every product gets the same keys and comments.

## 3. Opening and using

```java
Database db = Database.create(lib, settings.get().database());    // pool + worker, attached to lib (closed with it)
db.migrate(Migrations.fromResources("db/migrations")).join();     // §5 — blocking is fine in onEnable

// read: runs on the database worker, never on a server thread
CompletableFuture<List<Lot>> lots = db.query(
        "SELECT id, seller, price, ends_at FROM {prefix}lots WHERE ends_at > ?",
        row -> new Lot(row.uuid("id"), row.uuid("seller"), row.longValue("price"), row.instant("ends_at")),
        Instant.now());

Optional<Lot> one = db.queryOne("SELECT ... WHERE id = ?", Lot::fromRow, id).join();   // only off-thread or in onEnable

// write
db.update("UPDATE {prefix}lots SET price = ? WHERE id = ?", 1500L, lotId);             // CompletableFuture<Integer>

// several statements, one transaction (rolled back on any exception)
db.transaction(tx -> {
    tx.update("UPDATE {prefix}balances SET money = money - ? WHERE player = ?", price, buyer);
    tx.update("INSERT INTO {prefix}mail (player, item) VALUES (?, ?)", seller, itemBytes);
    return null;
});

// back on the player's thread
lots.thenAccept(list -> lib.scheduler().entity(player).run(() -> gui.open(player, list)));
```
- `{prefix}` is replaced by `table-prefix` in every statement and migration.
- Parameters are positional `?`, bound by type: `String, int, long, double, boolean, byte[], UUID, Instant, Duration,
  Enum, null`, plus `ItemStack` (bytes via `serializeAsBytes`). Unknown type → `IllegalArgumentException` at the call.
- `Row`: `string uuid intValue longValue doubleValue bool bytes instant duration enumOf(Class) itemStack` + `...OrNull`.
  UUIDs stored as `CHAR(36)` text on both dialects (readable, same on SQLite/MySQL); Instants as epoch millis `BIGINT`.
- No string concatenation API, no ORM. SQL stays visible.
- Futures complete on the database worker; a mapper/handler exception completes the future exceptionally and is logged
  once with the SQL (never the parameters — they can hold player data).
- `join()`/`get()` on a server thread outside onEnable/onDisable → warning with stack (helps catch lag).

## 4. Dialects

`db.dialect()` → `SQLITE | MYSQL` for the rare differences, plus helpers that hide the common ones:
```java
db.upsert("{prefix}balances", List.of("player"), Map.of("player", uuid, "money", 100L));  // ON CONFLICT / ON DUPLICATE KEY
```
Types to use in migrations so one file works on both: `BIGINT, INTEGER, VARCHAR(n), TEXT, BLOB, CHAR(36)`,
`PRIMARY KEY`, `REFERENCES`. Anything dialect-specific goes in an override file (§5).

## 5. Migrations

```
src/main/resources/db/migrations/
  V1__init.sql
  V2__mail.sql
  V2__mail.mysql.sql     # optional: used instead of V2__mail.sql on MySQL
```
- Table `{prefix}schema_history (version, name, checksum, applied_at)`. On `migrate()` every missing version runs in its
  own transaction, in order. A changed checksum of an applied version → error, nothing runs (do not edit shipped files).
- A newer database than the plugin knows (downgrade) → error, plugin should disable.
- Statements split on `;` at line end; `--` comments allowed.
- Code migrations for data moves: `Migrations.fromResources(...).withCode(3, "import-v1", tx -> ...)`.

## 6. Shutdown — never lose a write (bug #1)

`close()` (called by `lib.close()` in onDisable):
1. Stop accepting: new calls fail at once with `DatabaseClosedException` (futures, no throw at the call site).
2. Wait for every queued and running statement up to `shutdown-timeout` (`awaitTermination`).
3. If time runs out: log how many statements did not finish (SQL only), then interrupt.
4. Close the HikariCP pool **after** the worker is done. Idempotent.
Test: queue 1000 writes, close immediately, reopen → 1000 rows.

## 7. Threads and pools

- One `Database` = one HikariCP pool + one worker `ExecutorService` owned by it (named threads
  `<plugin>-db-<n>`, daemon=false, size = pool size). It does not use `lib.scheduler().async()`: shutdown must wait for
  exactly the DB's own work (§6), and Paper's async pool must not be blocked by slow SQL.
- **SQLite:** pool size 1 and worker size 1 (SQLite has one writer; this removes "database is locked"). Connection init:
  `PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000; PRAGMA foreign_keys=ON; PRAGMA synchronous=NORMAL`.
- **MySQL:** pool `pool.size`, worker same size; `utf8mb4`, `cachePrepStmts=true`, `useServerPrepStmts=true`,
  `rewriteBatchedStatements=true` + user `properties`.
- `db.batch(sql, rows)` for bulk inserts (imports, saves of many objects).

## 8. Copy between databases (switching backends, importing 1.0)

```java
try (Database old = Database.open(lib, legacySettings)) {          // not attached, read and write allowed
    Copy.tables(old, db, List.of("lots", "mail")).batchSize(500).run().join();   // same schema, any dialect pair
}
```
- `Copy` reads in pages and writes with `batch` in one transaction per page; reports rows copied per table.
- Importing InteractiveAuction 1.0 is product code: open the 1.0 database with `Database.open` (its own settings,
  SQLite or MySQL), read the old tables with `query`, write the new schema with `batch`. The library supplies the tools,
  not the 1.0 mapping.

## 9. Not in this module
ORM / query builder; connection to other databases (Postgres, H2, Mongo, Redis) — can be added as dialects later;
caching (products keep their own in-memory state and write through).

## 10. Testing
- Unit: real SQLite in a temp folder (`sqlite-jdbc` + `HikariCP` as **test-only** dependencies, ADR 0012 allows it):
  binding/reading every type, transaction rollback, upsert on SQLite, migrations (order, checksum change, downgrade,
  override file), shutdown 1000-writes test, closed → futures fail, `{prefix}`.
- MySQL: the same suite runs when the Gradle properties `mysqlUrl`, `mysqlUser`, `mysqlPassword` are set, skipped
  otherwise (CI). The owner keeps a throw-away MySQL in Docker and sets them in `%USERPROFILE%\.gradle\gradle.properties`
  (never in the repo), so Claude Code runs MySQL tests on every `check`. Each test run uses its own random table prefix
  (`t<random>_`) and drops its tables at the end; tests never touch other tables.
- Smoke (both servers): the example plugin's `PluginLoader` fetches HikariCP, opens SQLite with **Paper's** driver,
  migrates, writes, closes. This is the real proof that §1 works (risk: driver visibility across class loaders — first
  task checks it before anything else).

## 11. Decisions for owner (defaults chosen, change if you disagree)
1. **Async API = `CompletableFuture`**, results on the DB worker; hop back with `lib.scheduler()` (chosen) vs callbacks
   that run on the caller's thread automatically.
2. **Own worker per Database** (§7, chosen) vs `lib.scheduler().async()`.
3. **SQLite pool size 1 + WAL** (chosen).
4. **UUID as `CHAR(36)` text, Instant as epoch millis** (chosen; readable, same on both) vs `BINARY(16)`.
5. **SQL migrations in resources + optional `.mysql.sql` / `.sqlite.sql` overrides + code migrations** (chosen).
6. **`DatabaseSettings` record shared by all products** with the YAML shape of §2 (chosen).
7. **`Copy` helper + `Database.open`** for backend switch and 1.0 import; 1.0 mapping stays in the product (chosen).
8. **MySQL tests via Gradle properties** from the owner's user `gradle.properties` + a Docker MySQL kept running (chosen
   2026-10-20, owner) vs Testcontainers (extra test dependency).

## 13. 0.9.0 additions
- `Database.standalone(name, directory, logger, settings)`: a database tied to no plugin or `SynthetixLib`, for unit tests
  and command-line tools (P4-03). Same pool, worker, migrations and shutdown as `open`; the server-thread wait guard is off.
  Missing HikariCP or driver: the error says to add them as `testImplementation`.
- `ItemStack` parameters and columns throw a clear `IllegalStateException` when there is no server (`Bukkit.getServer() == null`).
- ArchUnit: library main code must not call `Database.standalone`.

## 12. Deviations from the design (as built, 0.5.0)
- `Database.open` / `create` pick HikariCP up through one package-private class (`HikariConnectionSource`); a missing HikariCP is
  reported with the `PluginLoader` hint. SQLite PRAGMAs are driver connection properties (sqlite-jdbc runs only the first
  statement of a multi-statement `connectionInitSql`).
- `DatabaseSettings.register(codecs)` makes the type read/written as `sqlite` / `mysql`; MySQL `properties` default to empty.
- Public additions: `Migrations` (+ `CodeMigration`, `MigrationException`), `Database.migrate`, `Copy.Report`, `DatabaseClosedException`,
  `DatabaseException` (carries the SQL, never the parameters).
- Server-thread wait guard: waiting on a result is allowed until the first global tick after `open`, and while the plugin is disabling.
  Waiting on a database's own worker throws at once (deadlock guard).
- `Copy` takes bare table names (`{prefix}` optional), pages by primary key and inserts with plain `INSERT`; it needs a primary key on MySQL.
- Demo (`synthetixlib-example`): `ClaimStore` keeps the claim counts in memory (written through) for the menu icons;
  `/example airdrop claim|stats <id>`, `/example db copy-to-mysql` (async handler, `Database.open` on the `database.mysql` section).
- Not covered by self-tests: MySQL in the smoke servers (the MySQL suite runs in `check` when the Gradle properties exist).
