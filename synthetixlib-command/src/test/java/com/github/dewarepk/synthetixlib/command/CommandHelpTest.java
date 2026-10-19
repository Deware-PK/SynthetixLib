package com.github.dewarepk.synthetixlib.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.dewarepk.synthetixlib.command.CommandFixtures.Inbox;
import com.github.dewarepk.synthetixlib.command.CommandFixtures.TestHost;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** {@code helpSub()} (P2-18). */
class CommandHelpTest {

    private static final Arg<Long> AMOUNT = Arg.of("amount", Args.longRange(1, 1_000));

    private final TestHost host = new TestHost();
    private final Inbox inbox = new Inbox();

    private CommandDispatcher<CommandSourceStack> shop() {
        final CommandSpec spec = CommandSpec.literal("shop").aliases("s").cooldown(Duration.ofMinutes(1))
                .sub(SubSpec.literal("bid").argument(AMOUNT).usage("<amount>").description("Bid on the lot")
                        .executes(ctx -> { }))
                .sub(SubSpec.literal("list").description("List lots").executes(ctx -> { }))
                .sub(SubSpec.literal("admin").permission("shop.admin")
                        .sub(SubSpec.literal("ban").usage("<player>").description("Ban a player")
                                .executes(ctx -> { }))
                        .sub(SubSpec.literal("reload").description("Reload").executes(ctx -> { })))
                .helpSub()
                .build();
        return CommandFixtures.dispatcher(host, spec);
    }

    private int run(final CommandDispatcher<CommandSourceStack> dispatcher, final CommandSender sender,
                    final String input) {
        try {
            return dispatcher.execute(input, CommandFixtures.source(sender));
        } catch (final CommandSyntaxException e) {
            throw new AssertionError("\"" + input + "\" did not parse: " + e.getMessage(), e);
        }
    }

    @Test
    void listsOnlyWhatTheSenderMayUseWithTheAliasTyped() {
        final Player steve = CommandFixtures.player("Steve", Set.of(), inbox);
        assertEquals(1, run(shop(), steve, "s help"));
        assertEquals(List.of(
                "Commands of /s:",
                "/s bid <amount> - Bid on the lot",
                "/s list  - List lots"), inbox.copy());
    }

    @Test
    void adminSeesTheGroupAndItsBranchHelpWithoutTheRootCooldown() {
        final Player alex = CommandFixtures.player("Alex", Set.of("shop.admin"), inbox);
        final var dispatcher = shop();
        assertEquals(1, run(dispatcher, alex, "shop help"));
        assertEquals(4, inbox.copy().size(), "header, bid, list, admin");
        assertEquals("/shop admin  - ", inbox.copy().get(3));

        assertEquals(1, run(dispatcher, alex, "shop admin"));
        assertEquals(1, run(dispatcher, alex, "shop admin"), "help does not start the inherited cooldown");
        assertEquals(List.of("Commands of /shop admin:", "/shop admin ban <player> - Ban a player",
                "/shop admin reload  - Reload"), inbox.copy().subList(4, 7));
    }

    @Test
    void aSubcommandNamedHelpIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> CommandSpec.literal("cmd")
                .sub(SubSpec.literal("help").executes(ctx -> { })).helpSub().build());
    }

    @Test
    void withoutHelpSubNothingIsAdded() {
        final CommandSpec spec = CommandSpec.literal("cmd")
                .sub(SubSpec.literal("a").executes(ctx -> { })).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("cmd help",
                CommandFixtures.source(CommandFixtures.console(inbox))));
    }
}
