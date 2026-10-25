package com.github.dewarepk.synthetixexample.demo.command;

import com.github.dewarepk.synthetixexample.demo.Airdrop;
import com.github.dewarepk.synthetixexample.demo.ClaimStore;
import com.github.dewarepk.synthetixexample.demo.Messages;
import com.github.dewarepk.synthetixlib.command.SubCommand;
import com.github.dewarepk.synthetixlib.command.SubSpec;
import com.github.dewarepk.synthetixlib.config.ConfigCollection;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;

/** {@code /example airdrop}: a group; its children are one class each. */
final class AirdropCommand implements SubCommand {

    private final ConfigCollection<Airdrop> airdrops;
    private final ClaimStore claims;
    private final LocaleHandle<Messages> lang;

    AirdropCommand(final ConfigCollection<Airdrop> airdrops, final ClaimStore claims,
                   final LocaleHandle<Messages> lang) {
        this.airdrops = airdrops;
        this.claims = claims;
        this.lang = lang;
    }

    // @snippet:command-group
    @Override
    public SubSpec spec() {
        return SubSpec.literal("airdrop")
                .permission("example.admin")
                .allowConsole()
                .description("Manage airdrops")
                .sub(new AirdropCreateCommand(airdrops, lang))
                .sub(new AirdropListCommand(airdrops, lang))
                .sub(new AirdropDeleteCommand(airdrops, lang))
                .sub(new AirdropClaimCommand(airdrops, claims, lang))
                .sub(new AirdropStatsCommand(airdrops, claims, lang));
    }
    // @end
}
