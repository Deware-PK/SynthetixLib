package com.github.dewarepk.synthetixlib.database;

import com.github.dewarepk.synthetixlib.database.MysqlExtension.Mysql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

/** Copy from the owner's MySQL to a SQLite file; skipped without the Gradle properties. */
@ExtendWith(MysqlExtension.class)
class MysqlToSqliteCopyTest extends CopyContract {

    private Mysql mysql;

    @BeforeEach
    void connect(final Mysql mysql) {
        this.mysql = mysql;
    }

    @Override
    DatabaseSettings source() {
        return mysql.settings().withTablePrefix(mysql.prefix() + "f_");
    }

    @Override
    DatabaseSettings target() {
        return DatabaseSettings.sqlite("new.db").withTablePrefix("new_");
    }
}
