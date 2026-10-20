package com.github.dewarepk.synthetixlib.database;

import com.github.dewarepk.synthetixlib.database.MysqlExtension.Mysql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

/** The same shutdown tests on the owner's MySQL; skipped without the Gradle properties. */
@ExtendWith(MysqlExtension.class)
class MysqlShutdownTest extends DatabaseShutdownContract {

    private Mysql mysql;

    @BeforeEach
    void connect(final Mysql mysql) {
        this.mysql = mysql;
    }

    @Override
    DatabaseSettings settings() {
        return mysql.settings();
    }
}
