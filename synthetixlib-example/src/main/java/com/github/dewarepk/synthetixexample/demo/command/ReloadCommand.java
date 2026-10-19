package com.github.dewarepk.synthetixexample.demo.command;

import com.github.dewarepk.synthetixexample.demo.Airdrop;
import com.github.dewarepk.synthetixexample.demo.Messages;
import com.github.dewarepk.synthetixexample.demo.Settings;
import com.github.dewarepk.synthetixlib.command.CommandCall;
import com.github.dewarepk.synthetixlib.command.SubCommand;
import com.github.dewarepk.synthetixlib.command.SubSpec;
import com.github.dewarepk.synthetixlib.config.ConfigCollection;
import com.github.dewarepk.synthetixlib.config.ConfigHandle;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** {@code /example reload}: console allowed. */
final class ReloadCommand implements SubCommand {

    private final ConfigHandle<Settings> settings;
    private final ConfigCollection<Airdrop> airdrops;
    private final LocaleHandle<Messages> lang;

    ReloadCommand(final ConfigHandle<Settings> settings, final ConfigCollection<Airdrop> airdrops,
                  final LocaleHandle<Messages> lang) {
        this.settings = settings;
        this.airdrops = airdrops;
        this.lang = lang;
    }

    @Override
    public SubSpec spec() {
        return SubSpec.literal("reload").permission("example.admin").allowConsole()
                .description("Reload settings, airdrops and messages").executes(this::run);
    }

    private void run(final CommandCall ctx) {
        final List<String> errors = new ArrayList<>(settings.reload().errors());
        airdrops.reload().files().forEach((file, result) -> errors.addAll(result.errors()));
        lang.reload().files().forEach((file, result) -> errors.addAll(result.errors()));
        if (errors.isEmpty()) {
            ctx.reply(lang, Messages::reloaded);
        } else {
            ctx.reply(lang, Messages::reloadFailed, Placeholder.unparsed("errors", String.join("; ", errors)));
        }
    }
}
