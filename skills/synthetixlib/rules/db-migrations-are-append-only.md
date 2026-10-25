---
title: Shipped migration files are never edited
impact: HIGH
tags: database, migrations
---

## Shipped migration files are never edited

Applied versions are stored with a checksum in `{prefix}schema_history`. Editing `V1__init.sql` after release makes `migrate()` refuse to run on every server that already applied it. Add `V2__...sql` (and a `.mysql.sql` override only for dialect-specific SQL), or a code migration for data moves.

**❌ Incorrect**

```text
V1__init.sql   (edited: added a column)
```

**✅ Correct**

```java snippet=db-migrations
final Migrations migrations = Migrations.fromResources("db/migrations")      // V1__init.sql, V2__mail.sql, ...
        .withCode(3, "import-v1", tx -> tx.update("UPDATE {prefix}lots SET price = price * 100"));
db.migrate(migrations).thenAccept(applied -> lib.plugin().getLogger().info("migrated"));
```
