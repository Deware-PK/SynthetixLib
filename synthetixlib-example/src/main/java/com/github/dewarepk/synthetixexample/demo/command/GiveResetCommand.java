package com.github.dewarepk.synthetixexample.demo.command;

import com.github.dewarepk.synthetixexample.demo.Messages;
import com.github.dewarepk.synthetixlib.command.Arg;
import com.github.dewarepk.synthetixlib.command.Args;
import com.github.dewarepk.synthetixlib.command.CommandCall;
import com.github.dewarepk.synthetixlib.command.CommandCooldowns;
import com.github.dewarepk.synthetixlib.command.SubCommand;
import com.github.dewarepk.synthetixlib.command.SubSpec;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/** {@code /example give-reset <player>}: lets one player use {@code /example give} again. */
// @snippet:command-reset-cooldown
final class GiveResetCommand implements SubCommand {

    private final CommandCooldowns cooldowns;
    private final LocaleHandle<Messages> lang;
    private final Arg<Player> target = Arg.of("player", Args.player());

    GiveResetCommand(final CommandCooldowns cooldowns, final LocaleHandle<Messages> lang) {
        this.cooldowns = cooldowns;
        this.lang = lang;
    }

    @Override
    public SubSpec spec() {
        return SubSpec.literal("give-reset")
                .permission("example.admin")
                .allowConsole()
                .argument(target)
                .usage("<player>").description("Let a player use /example give again")
                .executes(this::run);
    }

    private void run(final CommandCall ctx) {
        final Player player = ctx.get(target);
        cooldowns.reset(player.getUniqueId(), "example give");        // the path as written in code, not an alias
        ctx.reply(lang, Messages::giveReset, Placeholder.unparsed("player", player.getName()));
    }
}
// @end
