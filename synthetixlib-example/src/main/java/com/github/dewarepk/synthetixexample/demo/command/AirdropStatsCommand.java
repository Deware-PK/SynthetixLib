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
import org.bukkit.Bukkit;

/** {@code /example airdrop stats <id>}: reads the claim count and the last claimers. */
final class AirdropStatsCommand implements SubCommand {

    private static final int LAST = 5;

    private final ConfigCollection<Airdrop> airdrops;
    private final ClaimStore claims;
    private final LocaleHandle<Messages> lang;
    private final Arg<String> id;

    AirdropStatsCommand(final ConfigCollection<Airdrop> airdrops, final ClaimStore claims,
                        final LocaleHandle<Messages> lang) {
        this.airdrops = airdrops;
        this.claims = claims;
        this.lang = lang;
        this.id = Arg.of("id", Args.word()).suggests(source -> airdrops.ids());
    }

    @Override
    public SubSpec spec() {
        return SubSpec.literal("stats").argument(id).usage("<id>").description("Show who claimed an airdrop")
                .executes(this::run);
    }

    private void run(final CommandCall ctx) {
        final String value = ctx.get(id);
        if (!airdrops.ids().contains(value)) {
            throw ctx.fail(lang, Messages::airdropNotFound, Placeholder.unparsed("id", value));
        }
        claims.stats(value, LAST).whenComplete((stats, failure) -> ctx.runOnSender(() -> {
            if (failure != null) {
                ctx.lib().plugin().getLogger().warning("stats failed: " + failure);
                ctx.reply(lang, Messages::dbFailed);
                return;
            }
            final String names = stats.lastClaimers().stream().map(AirdropStatsCommand::name)
                    .reduce((a, b) -> a + ", " + b).orElse("-");
            ctx.reply(lang, Messages::airdropStats, Placeholder.unparsed("id", value),
                    Placeholder.unparsed("count", String.valueOf(stats.count())),
                    Placeholder.unparsed("players", names));
        }));
    }

    private static String name(final UUID player) {
        final String name = Bukkit.getOfflinePlayer(player).getName();
        return name != null ? name : player.toString();
    }
}
