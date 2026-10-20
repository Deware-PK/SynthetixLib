package com.github.dewarepk.synthetixlib.database;

/** The query API on real SQLite in a temp folder. */
class SqliteQueryTest extends DatabaseQueryContract {

    @Override
    DatabaseSettings settings() {
        return DatabaseSettings.sqlite("query.db").withTablePrefix("q_");
    }
}
