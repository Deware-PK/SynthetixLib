package com.github.dewarepk.synthetixlib.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.command.CommandFixtures.TestHost;
import com.mojang.brigadier.CommandDispatcher;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import org.junit.jupiter.api.Test;

/** How specs are validated at build and how they turn into Brigadier nodes. */
class CommandSpecTest {

    private static final CommandHandler NOOP = ctx -> {
    };
    private static final Arg<Long> AMOUNT = Arg.of("amount", Args.longRange(1, 1000));
    private static final Arg<String> LOT = Arg.of("lot", Args.word());
    private static final Arg<String> PLAYER = Arg.of("player", Args.word());
    private static final Arg<Integer> TIME = Arg.of("time", Args.integer(1, 60));

    private final TestHost host = new TestHost();

    private CommandDispatcher<CommandSourceStack> dispatcher(final CommandSpec... specs) {
        return CommandFixtures.dispatcher(host, specs);
    }

    @Test
    void treeShapeFollowsTheSpec() {
        final CommandSpec spec = CommandSpec.literal("auction")
                .aliases("ah", "auc")
                .executes(NOOP)
                .sub(SubSpec.literal("bid").argument(AMOUNT).executes(NOOP))
                .sub(SubSpec.literal("admin")
                        .sub(SubSpec.literal("cancel").argument(LOT).executes(NOOP))
                        .sub(SubSpec.literal("ban").argument(PLAYER).optional(TIME, 5).executes(NOOP)))
                .build();
        final CommandDispatcher<CommandSourceStack> dispatcher = dispatcher(spec);

        assertEquals("auction*(admin(ban(<player>*(<time>*)),cancel(<lot>*)),bid(<amount>*))",
                CommandFixtures.shape(dispatcher, "auction"));
        assertEquals("ah*->auction", CommandFixtures.shape(dispatcher, "ah"));
        assertEquals("auc*->auction", CommandFixtures.shape(dispatcher, "auc"));
        assertEquals("auction", spec.name());
        assertEquals(List.of("ah", "auc"), spec.aliases());
    }

    @Test
    void optionalArgumentsMakeTheNodeRunnableWithAndWithoutThem() {
        final Arg<String> a = Arg.of("a", Args.word());
        final Arg<String> b = Arg.of("b", Args.word());
        final Arg<String> c = Arg.of("c", Args.word());
        final CommandSpec spec = CommandSpec.literal("run").argument(a).optional(b, "x").optional(c, "y")
                .executes(NOOP).build();
        assertEquals("run(<a>*(<b>*(<c>*)))", CommandFixtures.shape(dispatcher(spec), "run"));

        final CommandSpec onlyOptional = CommandSpec.literal("opt").optional(b, "x").executes(NOOP).build();
        assertEquals("opt*(<b>*)", CommandFixtures.shape(dispatcher(onlyOptional), "opt"));
    }

    @Test
    void aGroupWithoutHandlerIsNotRunnable() {
        final CommandSpec spec = CommandSpec.literal("group").sub(SubSpec.literal("one").executes(NOOP)).build();
        assertEquals("group(one*)", CommandFixtures.shape(dispatcher(spec), "group"));
    }

    @Test
    void subCommandSpecIsCalledOnceAtBuild() {
        final int[] calls = {0};
        final SubCommand sub = () -> {
            calls[0]++;
            return SubSpec.literal("one").executes(NOOP);
        };
        final CommandSpec spec = CommandSpec.literal("group").sub(sub).build();
        assertEquals(1, calls[0]);
        dispatcher(spec);
        dispatcher(spec);
        assertEquals(1, calls[0], "compiling the built spec again does not ask the SubCommand again");
    }

    @Test
    void permissionAndPlayerOnlyAreInheritedUnlessOverridden() {
        final CommandSpec spec = CommandSpec.literal("cmd").permission("p.use").playerOnly()
                .sub(SubSpec.literal("inherits").executes(NOOP))
                .sub(SubSpec.literal("console").allowConsole().executes(NOOP))
                .sub(SubSpec.literal("admin").permission("p.admin")
                        .sub(SubSpec.literal("deep").executes(NOOP)))
                .build();
        final CommandDispatcher<CommandSourceStack> dispatcher = dispatcher(spec);
        final CommandSourceStack none = CommandFixtures.source(
                CommandFixtures.player("A", java.util.Set.of(), new CommandFixtures.Inbox()));
        final CommandSourceStack user = CommandFixtures.source(
                CommandFixtures.player("B", java.util.Set.of("p.use"), new CommandFixtures.Inbox()));
        final CommandSourceStack admin = CommandFixtures.source(
                CommandFixtures.player("C", java.util.Set.of("p.use", "p.admin"), new CommandFixtures.Inbox()));

        final var root = dispatcher.getRoot().getChild("cmd");
        assertTrue(!root.canUse(none) && root.canUse(user));
        assertTrue(!root.getChild("inherits").canUse(none), "inherits the root permission");
        assertTrue(root.getChild("inherits").canUse(user));
        assertTrue(!root.getChild("admin").canUse(user), "its own permission is added");
        assertTrue(root.getChild("admin").canUse(admin));
        assertTrue(!root.getChild("admin").getChild("deep").canUse(user), "and inherited further down");
        assertTrue(root.getChild("admin").getChild("deep").canUse(admin));
    }

    @Test
    void invalidNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> CommandSpec.literal("Auction"));
        assertThrows(IllegalArgumentException.class, () -> CommandSpec.literal("a b"));
        assertThrows(IllegalArgumentException.class, () -> CommandSpec.literal(""));
        assertThrows(IllegalArgumentException.class, () -> SubSpec.literal("ns:bid"));
        assertThrows(IllegalArgumentException.class, () -> Arg.of("Amount", Args.word()));
        assertThrows(IllegalArgumentException.class, () -> CommandSpec.literal("ok").aliases("Bad"));
        CommandSpec.literal("ok_name-1").executes(NOOP).build();
    }

    @Test
    void duplicateNamesAreRejectedAtBuild() {
        final IllegalArgumentException sub = assertThrows(IllegalArgumentException.class, () ->
                CommandSpec.literal("cmd")
                        .sub(SubSpec.literal("bid").executes(NOOP))
                        .sub(SubSpec.literal("bid").executes(NOOP))
                        .build());
        assertTrue(sub.getMessage().contains("bid"), sub.getMessage());

        assertThrows(IllegalArgumentException.class, () ->
                CommandSpec.literal("cmd").argument(LOT).argument(LOT).executes(NOOP).build());
        assertThrows(IllegalArgumentException.class, () ->
                CommandSpec.literal("cmd").aliases("x", "x").executes(NOOP).build());
        assertThrows(IllegalArgumentException.class, () ->
                CommandSpec.literal("cmd").aliases("cmd").executes(NOOP).build());
    }

    @Test
    void unusableNodesAreRejectedAtBuild() {
        assertThrows(IllegalArgumentException.class, () -> CommandSpec.literal("empty").build());
        assertThrows(IllegalArgumentException.class, () ->
                CommandSpec.literal("cmd").argument(LOT).sub(SubSpec.literal("x").executes(NOOP)).build());
        final Arg<String> text = Arg.of("text", Args.greedy());
        assertThrows(IllegalArgumentException.class, () ->
                CommandSpec.literal("cmd").argument(text).argument(LOT).executes(NOOP).build());
        CommandSpec.literal("cmd").argument(LOT).argument(text).executes(NOOP).build();
        assertThrows(NullPointerException.class, () -> CommandSpec.literal("cmd")
                .sub((SubCommand) () -> null).build());
    }

    @Test
    void builderMisuseIsRejected() {
        assertThrows(IllegalStateException.class, () ->
                CommandSpec.literal("cmd").optional(TIME, 1).argument(LOT));
        assertThrows(IllegalStateException.class, () ->
                CommandSpec.literal("cmd").executes(NOOP).executes(NOOP));
        assertThrows(IllegalStateException.class, () ->
                CommandSpec.literal("cmd").executes(NOOP).executesAsync(NOOP));
        assertThrows(IllegalArgumentException.class, () ->
                CommandSpec.literal("cmd").cooldown(java.time.Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () ->
                CommandSpec.literal("cmd").cooldown(java.time.Duration.ofSeconds(1), " "));
        assertThrows(IllegalArgumentException.class, () -> CommandSpec.literal("cmd").permission(" "));
    }

    @Test
    void aBuiltSpecIsNotAffectedByItsBuilder() {
        final CommandSpec.Builder builder = CommandSpec.literal("cmd").executes(NOOP);
        final CommandSpec first = builder.build();
        builder.aliases("other");
        assertEquals(List.of(), first.aliases());
        assertNull(first.description());
        assertSame(first.aliases(), first.aliases());
    }
}
