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

/** {@code /example airdrop create <id>}. */
final class AirdropCreateCommand implements SubCommand {

    private final ConfigCollection<Airdrop> airdrops;
    private final LocaleHandle<Messages> lang;
    private final Arg<String> id = Arg.of("id", Args.word());

    AirdropCreateCommand(final ConfigCollection<Airdrop> airdrops, final LocaleHandle<Messages> lang) {
        this.airdrops = airdrops;
        this.lang = lang;
    }

    @Override
    public SubSpec spec() {
        return SubSpec.literal("create").argument(id).usage("<id>").description("Create an airdrop")
                .executes(this::run);
    }

    // @snippet:command-fail
    private void run(final CommandCall ctx) {
        final String value = ctx.get(id);
        try {
            airdrops.create(value);
        } catch (final IllegalArgumentException invalid) {
            throw ctx.fail(lang, Messages::airdropInvalidId, Placeholder.unparsed("id", value));
        } catch (final IllegalStateException exists) {
            throw ctx.fail(lang, Messages::airdropExists, Placeholder.unparsed("id", value));
        }
        ctx.reply(lang, Messages::airdropCreated, Placeholder.unparsed("id", value));
    }
    // @end
}
