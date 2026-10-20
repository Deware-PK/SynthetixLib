package com.github.dewarepk.synthetixlib.database;

/** Shutdown on real SQLite in a temp folder. */
class SqliteShutdownTest extends DatabaseShutdownContract {

    @Override
    DatabaseSettings settings() {
        return DatabaseSettings.sqlite("shutdown.db").withTablePrefix("s_");
    }
}
