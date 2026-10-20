package com.github.dewarepk.synthetixexample.demo.command;

import com.github.dewarepk.synthetixexample.demo.Messages;
import com.github.dewarepk.synthetixexample.demo.Settings;
import com.github.dewarepk.synthetixlib.command.CommandCall;
import com.github.dewarepk.synthetixlib.command.SubCommand;
import com.github.dewarepk.synthetixlib.command.SubSpec;
import com.github.dewarepk.synthetixlib.config.ConfigHandle;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.database.Copy;
import com.github.dewarepk.synthetixlib.database.Database;
import com.github.dewarepk.synthetixlib.database.DatabaseSettings;
import com.github.dewarepk.synthetixlib.database.Dialect;
import com.github.dewarepk.synthetixlib.database.Migrations;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** {@code /example db copy-to-mysql}: copies the claims to the {@code database.mysql} section of settings.yml. */
final class DbCopyCommand implements SubCommand {

    private final Database database;
    private final ConfigHandle<Settings> settings;
    private final LocaleHandle<Messages> lang;

    DbCopyCommand(final Database database, final ConfigHandle<Settings> settings, final LocaleHandle<Messages> lang) {
        this.database = database;
        this.settings = settings;
        this.lang = lang;
    }

    @Override
    public SubSpec spec() {
        return SubSpec.literal("copy-to-mysql").description("Copy the claims to the MySQL of settings.yml")
                .executesAsync(this::run);
    }

    /** Blocks (migrate, copy), which is fine: the handler runs off the server threads. */
    private void run(final CommandCall ctx) {
        final DatabaseSettings current = settings.get().database();
        if (current.type() == Dialect.MYSQL) {
            throw ctx.fail(lang, Messages::dbAlreadyMysql);
        }
        final DatabaseSettings target = new DatabaseSettings(Dialect.MYSQL, current.tablePrefix(), current.sqlite(),
                current.mysql(), current.pool(), current.shutdownTimeout());
        try (Database mysql = Database.open(ctx.lib(), target)) {
            mysql.migrate(Migrations.fromResources("db/demo")).join();
            final long rows = Copy.tables(database, mysql, List.of("claims")).run().join().rows("claims");
            ctx.reply(lang, Messages::dbCopied, Placeholder.unparsed("rows", String.valueOf(rows)));
        } catch (final RuntimeException e) {
            ctx.lib().plugin().getLogger().warning("copy-to-mysql failed: " + e);
            throw ctx.fail(lang, Messages::dbCopyFailed, Placeholder.unparsed("reason", String.valueOf(e.getMessage())));
        }
    }
}
