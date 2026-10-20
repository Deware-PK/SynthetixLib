package com.github.dewarepk.synthetixexample.demo.command;

import com.github.dewarepk.synthetixexample.demo.Airdrop;
import com.github.dewarepk.synthetixexample.demo.ClaimStore;
import com.github.dewarepk.synthetixexample.demo.Messages;
import com.github.dewarepk.synthetixlib.command.Arg;
import com.github.dewarepk.synthetixlib.command.Args;
import com.github.dewarepk.synthetixlib.command.CommandCall;
import com.github.dewarepk.synthetixlib.command.SubCommand;
import com.github.dewarepk.synthetixlib.command.SubSpec;
import com.github.dewarepk.synthetixlib.config.ConfigCollection;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** {@code /example airdrop claim <id>}: writes a row; the answer is sent back on the player's thread. */
final class AirdropClaimCommand implements SubCommand {

    private final ConfigCollection<Airdrop> airdrops;
    private final ClaimStore claims;
    private final LocaleHandle<Messages> lang;
    private final Arg<String> id;

    AirdropClaimCommand(final ConfigCollection<Airdrop> airdrops, final ClaimStore claims,
                        final LocaleHandle<Messages> lang) {
        this.airdrops = airdrops;
        this.claims = claims;
        this.lang = lang;
        this.id = Arg.of("id", Args.word()).suggests(source -> airdrops.ids());
    }

    @Override
    public SubSpec spec() {
        return SubSpec.literal("claim").argument(id).usage("<id>").description("Claim an airdrop")
                .executes(this::run);
    }

    private void run(final CommandCall ctx) {
        final String value = ctx.get(id);
        if (!airdrops.ids().contains(value)) {
            throw ctx.fail(lang, Messages::airdropNotFound, Placeholder.unparsed("id", value));
        }
        final UUID player = ctx.player().getUniqueId();
        claims.claim(value, player).whenComplete((count, failure) -> ctx.runOnSender(() -> {
            if (failure != null) {
                ctx.lib().plugin().getLogger().warning("claim failed: " + failure);
                ctx.reply(lang, Messages::dbFailed);
            } else {
                ctx.reply(lang, Messages::airdropClaimed,
                        Placeholder.unparsed("id", value), Placeholder.unparsed("count", String.valueOf(count)));
            }
        }));
    }
}
