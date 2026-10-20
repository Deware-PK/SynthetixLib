package com.github.dewarepk.synthetixlib.database;

/**
 * A migration written in Java, for data moves that SQL cannot express (see {@link Migrations#withCode}). It runs in the
 * transaction of its version: when it throws, nothing it did is kept and the version is not recorded.
 */
@FunctionalInterface
public interface CodeMigration {

    /**
     * @param tx the migration's transaction; use it only inside this call
     * @throws Exception anything; the migration fails with a {@link MigrationException}
     */
    void run(Transaction tx) throws Exception;
}
