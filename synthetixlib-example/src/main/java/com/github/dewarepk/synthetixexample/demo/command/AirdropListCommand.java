package com.github.dewarepk.synthetixexample.demo.command;

import com.github.dewarepk.synthetixexample.demo.Airdrop;
import com.github.dewarepk.synthetixexample.demo.Messages;
import com.github.dewarepk.synthetixlib.command.CommandCall;
import com.github.dewarepk.synthetixlib.command.SubCommand;
import com.github.dewarepk.synthetixlib.command.SubSpec;
import com.github.dewarepk.synthetixlib.config.ConfigCollection;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import java.util.Set;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** {@code /example airdrop list}. */
final class AirdropListCommand implements SubCommand {

    private final ConfigCollection<Airdrop> airdrops;
    private final LocaleHandle<Messages> lang;

    AirdropListCommand(final ConfigCollection<Airdrop> airdrops, final LocaleHandle<Messages> lang) {
        this.airdrops = airdrops;
        this.lang = lang;
    }

    @Override
    public SubSpec spec() {
        return SubSpec.literal("list").description("List the airdrops").executes(this::run);
    }

    private void run(final CommandCall ctx) {
        final Set<String> ids = airdrops.ids();
        ctx.reply(lang, Messages::airdropList,
                Placeholder.unparsed("count", String.valueOf(ids.size())),
                Placeholder.unparsed("ids", String.join(", ", ids)));
    }
}
