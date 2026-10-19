package com.github.dewarepk.synthetixexample.demo.command;

import com.github.dewarepk.synthetixexample.demo.Airdrop;
import com.github.dewarepk.synthetixexample.demo.Messages;
import com.github.dewarepk.synthetixlib.command.Arg;
import com.github.dewarepk.synthetixlib.command.Args;
import com.github.dewarepk.synthetixlib.command.CommandCall;
import com.github.dewarepk.synthetixlib.command.SubCommand;
import com.github.dewarepk.synthetixlib.command.SubSpec;
import com.github.dewarepk.synthetixlib.config.ConfigCollection;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** {@code /example airdrop delete <id>}; the ids are suggested from the collection. */
final class AirdropDeleteCommand implements SubCommand {

    private final ConfigCollection<Airdrop> airdrops;
    private final LocaleHandle<Messages> lang;
    private final Arg<String> id;

    AirdropDeleteCommand(final ConfigCollection<Airdrop> airdrops, final LocaleHandle<Messages> lang) {
        this.airdrops = airdrops;
        this.lang = lang;
        this.id = Arg.of("id", Args.word()).suggests(source -> airdrops.ids());
    }

    @Override
    public SubSpec spec() {
        return SubSpec.literal("delete").argument(id).usage("<id>").description("Delete an airdrop")
                .executes(this::run);
    }

    private void run(final CommandCall ctx) {
        final String value = ctx.get(id);
        if (!airdrops.delete(value)) {
            throw ctx.fail(lang, Messages::airdropNotFound, Placeholder.unparsed("id", value));
        }
        ctx.reply(lang, Messages::airdropDeleted, Placeholder.unparsed("id", value));
    }
}
