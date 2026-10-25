package com.github.dewarepk.synthetixexample.demo.command;

import com.github.dewarepk.synthetixexample.demo.Messages;
import com.github.dewarepk.synthetixlib.command.Arg;
import com.github.dewarepk.synthetixlib.command.Args;
import com.github.dewarepk.synthetixlib.command.CommandCall;
import com.github.dewarepk.synthetixlib.command.SubCommand;
import com.github.dewarepk.synthetixlib.command.SubSpec;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import java.time.Duration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** {@code /example give <player> <material> [amount]}, with a cooldown. */
// @snippet:command-give
final class GiveCommand implements SubCommand {

    private final LocaleHandle<Messages> lang;
    private final Arg<Player> target = Arg.of("player", Args.player());
    private final Arg<Material> material = Arg.of("material", Args.material());
    private final Arg<Integer> amount = Arg.of("amount", Args.integer(1, 64));

    GiveCommand(final LocaleHandle<Messages> lang) {
        this.lang = lang;
    }

    @Override
    public SubSpec spec() {
        return SubSpec.literal("give")
                .permission("example.give")
                .allowConsole()
                .argument(target).argument(material).optional(amount, 1)
                .cooldown(Duration.ofSeconds(10), "example.admin")
                .usage("<player> <material> [amount]").description("Give items to a player")
                .executes(this::run);
    }

    private void run(final CommandCall ctx) {
        final Player player = ctx.get(target);
        final Material type = ctx.get(material);
        final int count = ctx.get(amount);
        player.getInventory().addItem(new ItemStack(type, count));
        ctx.reply(lang, Messages::gave,
                Placeholder.unparsed("amount", String.valueOf(count)),
                Placeholder.unparsed("material", type.key().value()),
                Placeholder.unparsed("player", player.getName()));
    }
}
// @end
