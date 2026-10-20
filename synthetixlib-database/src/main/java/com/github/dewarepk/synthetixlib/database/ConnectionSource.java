package com.github.dewarepk.synthetixlib.database;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Where connections come from. The only implementation is {@link HikariConnectionSource}; the interface keeps every
 * HikariCP class out of the rest of the module, so a missing HikariCP shows up as one clear error at one place.
 */
interface ConnectionSource extends AutoCloseable {

    /** @return a pooled connection; closing it returns it to the pool */
    Connection get() throws SQLException;

    @Override
    void close();
}
