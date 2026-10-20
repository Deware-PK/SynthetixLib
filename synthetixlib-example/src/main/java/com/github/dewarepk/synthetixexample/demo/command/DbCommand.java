package com.github.dewarepk.synthetixexample.demo.command;

import com.github.dewarepk.synthetixexample.demo.Messages;
import com.github.dewarepk.synthetixexample.demo.Settings;
import com.github.dewarepk.synthetixlib.command.SubCommand;
import com.github.dewarepk.synthetixlib.command.SubSpec;
import com.github.dewarepk.synthetixlib.config.ConfigHandle;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.database.Database;

/** {@code /example db}: a group; its child copies the data to the MySQL of the settings. */
final class DbCommand implements SubCommand {

    private final Database database;
    private final ConfigHandle<Settings> settings;
    private final LocaleHandle<Messages> lang;

    DbCommand(final Database database, final ConfigHandle<Settings> settings, final LocaleHandle<Messages> lang) {
        this.database = database;
        this.settings = settings;
        this.lang = lang;
    }

    @Override
    public SubSpec spec() {
        return SubSpec.literal("db")
                .permission("example.admin")
                .allowConsole()
                .description("Database tools")
                .sub(new DbCopyCommand(database, settings, lang));
    }
}
