package com.github.dewarepk.synthetixlib.database;

import com.github.dewarepk.synthetixlib.database.MysqlExtension.Mysql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

/** The same migrations on the owner's MySQL; skipped without the Gradle properties. */
@ExtendWith(MysqlExtension.class)
class MysqlMigrationTest extends MigrationContract {

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
