package com.github.dewarepk.synthetixlib.database;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/** The migrations on real SQLite in a temp folder. */
class SqliteMigrationTest extends MigrationContract {

    @Override
    DatabaseSettings settings() {
        return DatabaseSettings.sqlite("migrate.db").withTablePrefix("m_");
    }

    /** SQLite runs DDL inside the transaction, so a failing version leaves no table behind (MySQL cannot promise this). */
    @Test
    void aFailingVersionUndoesItsDdlOnSqlite() {
        final DatabaseImpl db = open();

        migrationFailure(db.migrate(migrations(files("V1__bad.sql",
                "CREATE TABLE {prefix}d (id INTEGER);\nINSERT INTO {prefix}nosuchtable VALUES (1);\n"))));

        assertFalse(tableExists(db, "d"), "the CREATE TABLE must be rolled back");
    }
}
