package com.github.dewarepk.synthetixlib.database;

/** The SQL dialects a {@link Database} can talk to. */
public enum Dialect {
    /** SQLite through the driver that Paper ships; one pool connection, WAL. */
    SQLITE,
    /** MySQL (and MariaDB) through the driver that Paper ships. */
    MYSQL
}
