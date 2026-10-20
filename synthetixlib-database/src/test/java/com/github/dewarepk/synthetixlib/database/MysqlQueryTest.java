package com.github.dewarepk.synthetixlib.database;

import com.github.dewarepk.synthetixlib.database.MysqlExtension.Mysql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

/** The same query API on the owner's MySQL, with the library's default settings; skipped without the Gradle properties. */
@ExtendWith(MysqlExtension.class)
class MysqlQueryTest extends DatabaseQueryContract {

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
