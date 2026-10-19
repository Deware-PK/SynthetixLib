package com.github.dewarepk.synthetixexample.demo.command;

import com.github.dewarepk.synthetixexample.demo.Airdrop;
import com.github.dewarepk.synthetixexample.demo.Messages;
import com.github.dewarepk.synthetixlib.command.SubCommand;
import com.github.dewarepk.synthetixlib.command.SubSpec;
import com.github.dewarepk.synthetixlib.config.ConfigCollection;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;

/** {@code /example airdrop}: a group; its children are one class each. */
final class AirdropCommand implements SubCommand {

    private final ConfigCollection<Airdrop> airdrops;
    private final LocaleHandle<Messages> lang;

    AirdropCommand(final ConfigCollection<Airdrop> airdrops, final LocaleHandle<Messages> lang) {
        this.airdrops = airdrops;
        this.lang = lang;
    }

    @Override
    public SubSpec spec() {
        return SubSpec.literal("airdrop")
                .permission("example.admin")
                .allowConsole()
                .description("Manage airdrops")
                .sub(new AirdropCreateCommand(airdrops, lang))
                .sub(new AirdropListCommand(airdrops, lang))
                .sub(new AirdropDeleteCommand(airdrops, lang));
    }
}
