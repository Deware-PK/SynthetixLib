package com.github.dewarepk.synthetixexample.demo.command;

import com.github.dewarepk.synthetixexample.demo.Airdrop;
import com.github.dewarepk.synthetixexample.demo.ClaimStore;
import com.github.dewarepk.synthetixexample.demo.DemoDialogs;
import com.github.dewarepk.synthetixexample.demo.DemoMenus;
import com.github.dewarepk.synthetixexample.demo.Messages;
import com.github.dewarepk.synthetixexample.demo.Settings;
import com.github.dewarepk.synthetixlib.command.CommandCooldowns;
import com.github.dewarepk.synthetixlib.command.CommandSpec;
import com.github.dewarepk.synthetixlib.config.ConfigCollection;
import com.github.dewarepk.synthetixlib.config.ConfigHandle;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.database.Database;

/** The demo command {@code /example} (alias {@code /ex}); every subcommand is its own class. */
public final class ExampleCommand {

    private ExampleCommand() {
    }

    /** @return the spec to hand to {@code CommandService.register} */
    public static CommandSpec build(final ConfigHandle<Settings> settings, final ConfigCollection<Airdrop> airdrops,
                                    final LocaleHandle<Messages> lang, final DemoMenus menus, final DemoDialogs dialogs,
                                    final Database database, final ClaimStore claims,
                                    final CommandCooldowns cooldowns) {
        // @snippet:command-root
        return CommandSpec.literal("example")
                .aliases("ex")
                .description("SynthetixLib example plugin")
                .sub(new AirdropCommand(airdrops, claims, lang))
                .sub(new DialogCommand(dialogs))
                .sub(new DbCommand(database, settings, lang))
                .sub(new GiveCommand(settings, lang))
                .sub(new GiveResetCommand(cooldowns, lang))
                .sub(new MenuCommand(menus))
                .sub(new ReloadCommand(settings, airdrops, lang))
                .helpSub()
                .build();
        // @end
    }
}
