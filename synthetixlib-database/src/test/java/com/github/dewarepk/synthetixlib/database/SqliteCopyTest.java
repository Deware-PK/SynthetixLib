package com.github.dewarepk.synthetixlib.database;

/** Copy from one SQLite file to another, each with its own prefix. */
class SqliteCopyTest extends CopyContract {

    @Override
    DatabaseSettings source() {
        return DatabaseSettings.sqlite("old.db").withTablePrefix("old_");
    }

    @Override
    DatabaseSettings target() {
        return DatabaseSettings.sqlite("new.db").withTablePrefix("new_");
    }
}
