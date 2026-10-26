# database

`synthetixlib-database` (since 0.5.0). Async SQL for SQLite and MySQL: HikariCP pool, typed parameters and rows,
versioned migrations per dialect, and a shutdown that never loses a write. Depends on core and config; HikariCP is loaded
by **your** `PluginLoader`, never shaded. Design: `docs/design/database.md` (read its section 12, "Deviations").

## When to use / when not

- Use it for anything that must survive a restart and is queried: purchases, claims, mail, logs.
- Not an ORM or query builder: SQL stays visible. Not for Postgres/H2/Mongo/Redis. Not a cache: keep your own in-memory
  state and write through (see `ClaimStore` in the example).
- Small tunables and per-object data belong in `config` YAML, not in a database.

## Setup

1. Add the module and `compileOnly("com.zaxxer:HikariCP:<version of the lib>")` (see `setup.md`).
2. Load HikariCP at runtime from the `PluginLoader` named by `loader:` in `paper-plugin.yml`:

```java snippet=template-loader
@Override
public void classloader(final PluginClasspathBuilder classpath) {
    // Database module: add HikariCP (needs the import com.github.dewarepk.synthetixlib.database.SynthetixLibraries)
    // SynthetixLibraries.database(classpath);
}
```

   For the database module the body of that method is `SynthetixLibraries.database(classpath);` (it adds HikariCP at the
   version the library was built with, via Paper's Maven mirror). Paper itself ships the SQLite and MySQL drivers; they are
   never fetched.
3. Put `DatabaseSettings` into your own settings record, so buyers edit it in your `config.yml`; register its codec:

```java snippet=config-settings-record
@Comment("SynthetixExample settings")
public record Settings(@Comment("Give every joining player a demo ticket") boolean welcomeTicket,
                       @Key("default-airdrop")
                       @Comment("New airdrops start from this one (the name is replaced by the id)")
                       Airdrop defaultAirdrop,
                       @Comment("Where the airdrop claims are stored (sqlite or mysql); a restart applies a change")
                       DatabaseSettings database,
                       @Comment("Dialogs: how long their buttons work; debug logs the thread of the first click at INFO")
                       DialogSettings dialog,
                       @Key("sell-min-price")
                       @Comment("The sell dialog: the lowest price a player may type")
                       long sellMinPrice,
                       @Key("sell-max-price")
                       @Comment("The sell dialog: the highest price a player may type")
                       long sellMaxPrice,
                       @Key("give-wait")
                       @Comment("/example give: how long a player waits between uses; a reload applies a change")
                       Duration giveWait) {

    public static Settings defaults() {
        return new Settings(true, Airdrop.standard(),
                DatabaseSettings.sqlite("example.db").withTablePrefix("ex_"),
                new DialogSettings(Duration.ofMinutes(5), true), 1, 1_000_000, Duration.ofSeconds(10));
    }
}
```

   `DatabaseSettings.register(configs.codecs())` before loading that file. `DatabaseSettings.sqlite("file.db")` and
   `DatabaseSettings.mysql(host, port, database, user, password)` build defaults; `withTablePrefix("ia_")`.

4. Create and migrate in `onEnable` (blocking is fine there):

```java snippet=db-create
final Database database = Database.create(lib, settings.get().database());
final ClaimStore claims = ClaimStore.open(database);
```

```java snippet=db-open-migrate
/** Creates the table if needed and loads the counts; blocks, so call it from {@code onEnable}. */
public static ClaimStore open(final Database db) {
    db.migrate(Migrations.fromResources("db/demo")).join();
    final ClaimStore store = new ClaimStore(db);
    db.query("SELECT airdrop, COUNT(*) AS n FROM {prefix}claims GROUP BY airdrop",
                    row -> Map.entry(row.string("airdrop"), row.longValue("n")))
            .join()
            .forEach(entry -> store.counts.put(entry.getKey(), entry.getValue()));
    return store;
}
```

`Database.create(lib, settings)` is attached to `lib`: `lib.close()` waits for queued writes, then closes the pool.
`Database.open(lib, settings)` is not attached (you close it): for a second database such as a legacy one.

## The settings YAML

Keys of `DatabaseSettings`: `type` (`sqlite` | `mysql`), `table-prefix` (`[A-Za-z0-9_]{0,32}`, replaces `{prefix}` in SQL),
`sqlite.file` (relative to the plugin folder), `mysql.host/port/database/username/password/properties`,
`pool.size` / `connection-timeout` / `max-lifetime`, `shutdown-timeout` (how long `close()` waits, default 10s).
MySQL 8 without TLS needs `properties: { useSSL: 'false', allowPublicKeyRetrieval: 'true' }` (the error message says so).
The password is never logged.

## Migrations

`src/main/resources/db/migrations/V1__init.sql`, `V2__mail.sql`, ... with an optional `V2__mail.mysql.sql` used instead on
MySQL. Use the common types (`BIGINT, INTEGER, VARCHAR(n), TEXT, BLOB, CHAR(36)`); `AUTO_INCREMENT` needs the `.mysql.sql`
override. `{prefix}` works everywhere. Applied versions are recorded in `{prefix}schema_history` with a checksum:
**never edit a shipped file** (a changed checksum, a gap, or a database newer than the plugin refuses to run).

```java snippet=db-migrations
final Migrations migrations = Migrations.fromResources("db/migrations")      // V1__init.sql, V2__mail.sql, ...
        .withCode(3, "import-v1", tx -> tx.update("UPDATE {prefix}lots SET price = price * 100"));
db.migrate(migrations).thenAccept(applied -> lib.plugin().getLogger().info("migrated"));
```

Each missing version runs in its own transaction. Statements split on `;` at line end, `--` comments are fine. On MySQL
DDL cannot be rolled back.

## Queries

```java snippet=db-statements
final CompletableFuture<List<UUID>> sellers =
        db.query("SELECT seller FROM {prefix}lots WHERE price > ?", row -> row.uuid("seller"), price);
final CompletableFuture<Optional<Long>> one =
        db.queryOne("SELECT price FROM {prefix}lots WHERE seller = ?", row -> row.longValue("price"), seller);
final CompletableFuture<Integer> changed =
        db.update("UPDATE {prefix}lots SET price = ? WHERE seller = ?", price, seller);

db.transaction(tx -> {                                                       // all or nothing
    tx.update("UPDATE {prefix}balances SET money = money - ? WHERE player = ?", price, player.getUniqueId());
    tx.update("INSERT INTO {prefix}mail (player, note) VALUES (?, ?)", seller, "sold");
    return null;
});

db.batch("INSERT INTO {prefix}lots (seller, price) VALUES (?, ?)",
        List.of(new Object[]{seller, 10L}, new Object[]{seller, 20L}));
db.upsert("{prefix}balances", List.of("player"), Map.of("player", seller, "money", 100L));

// back on the player's own thread before touching Bukkit
sellers.thenAccept(list -> lib.scheduler().entity(player).run(() -> player.sendMessage("sellers: " + list.size())));
```

```java snippet=db-update
/** Writes one claim; the future completes with the new count. */
public CompletableFuture<Long> claim(final String airdrop, final UUID player) {
    return db.update("INSERT INTO {prefix}claims (airdrop, player, claimed_at) VALUES (?, ?, ?)",
                    airdrop, player, Instant.now())
            .thenApply(rows -> counts.merge(airdrop, 1L, Long::sum));
}
```

```java snippet=db-query-chain
/** Reads the count and the last {@code limit} claimers, newest first. */
public CompletableFuture<Stats> stats(final String airdrop, final int limit) {
    return db.queryOne("SELECT COUNT(*) AS n FROM {prefix}claims WHERE airdrop = ?",
                    row -> row.longValue("n"), airdrop)
            .thenCompose(count -> db.query(
                            "SELECT player FROM {prefix}claims WHERE airdrop = ? ORDER BY id DESC LIMIT " + limit,
                            row -> row.uuid("player"), airdrop)
                    .thenApply(players -> new Stats(count.orElse(0L), players)));
}
```

- `insert` (since 0.9.1) runs one INSERT that creates exactly one row and returns its generated key (an auto-increment /
  `INTEGER PRIMARY KEY` id), the same on SQLite and MySQL. Another row count or no key: `DatabaseException`, nothing kept.
  Not for `upsert` or `batch` (no keys there).

```java snippet=db-insert-key
db.insert("INSERT INTO {prefix}lots (seller, price) VALUES (?, ?)", seller, 100L)
        .thenAccept(id -> lib.plugin().getLogger().info("lot " + id));

db.transaction(tx -> {
    final long lotId = tx.insert("INSERT INTO {prefix}lots (seller, price) VALUES (?, ?)", seller, 100L);
    return tx.insert("INSERT INTO {prefix}bids (lot, amount) VALUES (?, ?)", lotId, 150L);   // the bid's own id
});
```

- `?` parameters are bound by type: `String, int, long, double, boolean, byte[], UUID, Instant, Duration, Enum, ItemStack,
  null`. Anything else throws `IllegalArgumentException` **at the call**. UUIDs are stored as `CHAR(36)`, Instants as epoch
  millis `BIGINT`.
- Futures complete on the database's own worker, not on a server thread. Hop back with
  `lib.scheduler().entity(player).run(...)` before touching Bukkit.
- `Row`: `string`, `uuid`, `intValue`, `longValue`, `doubleValue`, `bool`, `bytes`, `instant`, `duration`,
  `enumOf(column, Class)`, `itemStack`, each with an `...OrNull` variant (a NULL read with the plain getter throws).

```java snippet=db-row-mapper
final RowMapper<String> mapper = row -> row.string("name") + " " + row.intValueOrNull("level")
        + " " + row.instant("created_at") + " " + row.enumOf("kind", Dialect.class);
```

- `transaction(tx -> ...)`: all or nothing; use `tx`, never the `Database`, inside (waiting for a `Database` future inside
  the body deadlocks on SQLite's single worker). `upsert(table, keyColumns, values)` hides `ON CONFLICT` /
  `ON DUPLICATE KEY`. `batch(sql, rows)` for bulk inserts.
- A mapper or handler exception completes the future exceptionally; one log line with the SQL, **never the parameters**.
  The exception is `DatabaseException` (unchecked, `sql()`).

## Threads and shutdown

- One `Database` = one pool + one worker owned by it (SQLite: pool 1, WAL; MySQL: `pool.size`). It does not use
  `lib.scheduler().async()`, so shutdown waits for exactly the database's own work.
- `join()` / `get()` on a server thread logs a warning with a stack, except in `onEnable` / `onDisable` (until the first
  global tick, and while the plugin disables). Waiting from the database's own worker throws at once.
- `close()`: new calls fail with `DatabaseClosedException` (in the future, not thrown), pending statements finish up to
  `shutdown-timeout`, what is left is logged (SQL only) and interrupted, **then** the pool closes. Idempotent. Without
  `lib.close()` in `onDisable` you lose writes.

## Copy between databases (switching backend, importing an old version)

```java snippet=db-copy
final Database old = Database.open(lib, legacy);                              // not attached to lib: close it yourself
Copy.tables(old, current, List.of("lots", "mail")).batchSize(500).run()
        .whenComplete((report, failure) -> {
            old.close();
            if (failure == null) {
                lib.plugin().getLogger().info("copied " + report.total() + " rows");
            }
        });
```

`Copy.tables(from, to, names)` takes bare table names (`{prefix}` optional), pages by primary key and inserts with plain
`INSERT` (existing rows fail the page; one transaction per page). A table without a primary key is refused on MySQL.
`Report` has `rows(table)` and `total()`. Mapping an old schema to a new one is product code: read with `query`, write with
`batch`.

## API table

| Type | For | Since |
|---|---|---|
| `Database` | `create`, `open`, `standalone` (0.9.0, tests and tools), `dialect`, `query`, `queryOne`, `update`, `insert` (0.9.1), `batch`, `transaction`, `upsert`, `migrate`, `close` | 0.5.0 |
| `DatabaseSettings` (+ `Sqlite`, `Mysql`, `Pool`) | the YAML shape; `sqlite(file)`, `mysql(...)`, `withTablePrefix`, `register(codecs)` | 0.5.0 |
| `Dialect` | `SQLITE`, `MYSQL` | 0.5.0 |
| `Row`, `RowMapper<T>` | typed getters; `T map(Row)` | 0.5.0 |
| `Transaction`, `TransactionBody<T>` | the same statements, synchronous, inside `transaction` | 0.5.0 |
| `Migrations`, `CodeMigration` | `fromResources(path)`, `fromResources(loader, path)`, `withCode(version, name, tx -> ...)` | 0.5.0 |
| `Copy`, `Copy.Report` | `tables`, `batchSize`, `run`; `rows`, `total` | 0.5.0 |
| `SynthetixLibraries` | `database(PluginClasspathBuilder)` for the `PluginLoader` | 0.5.0 |
| `DatabaseException`, `DatabaseClosedException`, `MigrationException` | unchecked failures | 0.5.0 |

## Testing

Test your store in plain JUnit, no server: `Database.standalone(name, directory, logger, settings)` (since 0.9.0) is a
database that belongs to nothing; you close it. `name` gives the worker threads (`<name>-db-<n>`), `directory` holds a
relative SQLite file (use `@TempDir`). Add the pool and a driver as test dependencies; a missing one fails with a message
saying so. Versions match what the library tests with: HikariCP `7.1.0`, sqlite-jdbc `3.49.1.0` (the catalog,
`gradle/libs.versions.toml`, is the source). `join()` on the test thread is fine. See recipe 4.

- `ItemStack` parameters and `row.itemStack` need a running server: without one they throw
  `IllegalStateException("ItemStack columns need a running Paper server: test them in a self-test")`.
- Library code never uses `standalone`; a plugin uses `create`.

## Pitfalls

- Wrong: shading HikariCP, or declaring it `implementation` -> right: `compileOnly` + `SynthetixLibraries.database(...)` in
  the `PluginLoader` (rule `db-hikari-via-loader`).
- Wrong: closing the pool yourself or skipping `lib.close()` -> right: attach with `Database.create` and close `lib`
  (rule `db-close-waits-for-writes`).
- Wrong: `db.query(...).join()` in a command or event -> right: `thenAccept` and a scheduler hop (rule
  `db-never-block-server-thread`).
- Wrong: `"... WHERE name = '" + name + "'"` -> right: `?` parameters (rule `db-parameters-not-concatenation`).
- Wrong: editing `V1__init.sql` after release -> right: a new `V2__...sql` (rule `db-migrations-are-append-only`).
- Wrong: running `last_insert_rowid()` / `LAST_INSERT_ID()` yourself -> right: `insert` returns the key.
- Wrong: a table prefix typed into the SQL -> right: `{prefix}`.
- Wrong: reading a nullable column with `longValue` -> right: `longValueOrNull`.

## Limits

- SQLite and MySQL 8+ only. Types in migrations must work on both, or use the `.mysql.sql` override.
- `Copy` needs a primary key on MySQL and uses plain `INSERT`.
- MySQL is not covered by the smoke servers; the library's MySQL suite runs in its own `check` when the owner's Gradle
  properties exist.

Design: `docs/design/database.md`.
