package com.github.dewarepk.synthetixlib.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.command.CommandFixtures.Inbox;
import com.github.dewarepk.synthetixlib.command.CommandFixtures.TestHost;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Running specs through a real Brigadier dispatcher with Proxy senders. */
class CommandExecutionTest {

    private static final Arg<Long> AMOUNT = Arg.of("amount", Args.longRange(1, 1_000_000));
    private static final Arg<String> LOT = Arg.of("lot", Args.word());
    private static final Arg<Integer> TIMES = Arg.of("times", Args.integer(1, 10));
    private static final Arg<String> OTHER = Arg.of("other", Args.word());

    private final TestHost host = new TestHost();
    private final Inbox inbox = new Inbox();
    private final CommandSender console = CommandFixtures.console(inbox);

    private int run(final CommandDispatcher<CommandSourceStack> dispatcher, final CommandSender sender,
                    final String input) {
        try {
            return dispatcher.execute(input, CommandFixtures.source(sender));
        } catch (final CommandSyntaxException e) {
            throw new AssertionError("\"" + input + "\" did not parse: " + e.getMessage(), e);
        }
    }

    @Test
    void successReturnsOneAndHandlerSeesTheCall() {
        final List<String> seen = new ArrayList<>();
        final CommandSpec spec = CommandSpec.literal("cmd")
                .sub(SubSpec.literal("bid").argument(AMOUNT).executes(ctx -> seen.add("bid " + ctx.get(AMOUNT))))
                .build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        assertEquals(1, run(dispatcher, console, "cmd bid 42"));
        assertEquals(List.of("bid 42"), seen);
        assertEquals(List.of(), inbox.copy(), "no reply unless the handler sends one");
    }

    @Test
    void rangeIsCheckedByBrigadier() {
        final CommandSpec spec = CommandSpec.literal("cmd")
                .sub(SubSpec.literal("bid").argument(AMOUNT).executes(ctx -> {
                })).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);
        assertThrows(CommandSyntaxException.class, () ->
                dispatcher.execute("cmd bid 0", CommandFixtures.source(console)));
        assertThrows(CommandSyntaxException.class, () ->
                dispatcher.execute("cmd bid", CommandFixtures.source(console)));
    }

    @Test
    void requiresHidesTheNodeFromSenderWithoutPermission() {
        final List<String> ran = new ArrayList<>();
        final CommandSpec spec = CommandSpec.literal("secret").permission("p.secret")
                .executes(ctx -> ran.add("ran")).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);
        final Player nobody = CommandFixtures.player("N", Set.of(), new Inbox());
        final Player allowed = CommandFixtures.player("Y", Set.of("p.secret"), new Inbox());

        assertThrows(CommandSyntaxException.class, () ->
                dispatcher.execute("secret", CommandFixtures.source(nobody)));
        assertEquals(List.of(), ran);
        assertEquals(1, run(dispatcher, allowed, "secret"));
        assertEquals(List.of("ran"), ran);

        final var parse = dispatcher.parse("", CommandFixtures.source(nobody));
        final List<String> visible = dispatcher.getRoot().getChildren().stream()
                .filter(node -> node.canUse(parse.getContext().getSource())).map(node -> node.getName()).toList();
        assertEquals(List.of(), visible, "not even offered to tab-complete");
    }

    @Test
    void permissionIsAlsoCheckedWhereTheRedirectSkipsTheNode() {
        final List<String> ran = new ArrayList<>();
        final CommandSpec spec = CommandSpec.literal("secret").aliases("sec").permission("p.secret")
                .executes(ctx -> ran.add("ran")).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);
        final CommandSender nobody = CommandFixtures.player("N", Set.of(), new Inbox());
        // A redirect copies the requirement, but even if a path got through, the executor refuses.
        final Executor executor = new Executor(new Node("secret", "secret", null, null, "p.secret",
                List.of("p.secret"), false, null, List.of(), ctx -> ran.add("ran"), false, List.of()), host);
        final var parsed = dispatcher.parse("sec", CommandFixtures.source(nobody));
        assertEquals(0, executor.run(parsed.getContext().build("sec")));
        assertEquals(List.of(), ran);
    }

    @Test
    void playerOnlyNodeStaysVisibleAndRepliesToTheConsole() {
        final List<String> ran = new ArrayList<>();
        final CommandSpec spec = CommandSpec.literal("cmd").aliases("c").playerOnly()
                .executes(ctx -> ran.add(ctx.player().getName()))
                .sub(SubSpec.literal("console").allowConsole().executes(ctx -> ran.add("console ok")))
                .build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        assertEquals(0, run(dispatcher, console, "c"));
        assertEquals(List.of("Only players can use this."), inbox.copy());
        assertEquals(List.of(), ran);

        assertEquals(1, run(dispatcher, console, "cmd console"));
        assertEquals(List.of("console ok"), ran);

        final Player player = CommandFixtures.player("Steve", Set.of(), new Inbox());
        assertEquals(1, run(dispatcher, player, "cmd"));
        assertEquals(List.of("console ok", "Steve"), ran);
    }

    @Test
    void labelIsTheAliasThatWasTyped() {
        final List<String> labels = new ArrayList<>();
        final List<String> inputs = new ArrayList<>();
        final CommandSpec spec = CommandSpec.literal("auction").aliases("ah")
                .sub(SubSpec.literal("bid").argument(AMOUNT).executes(ctx -> {
                    labels.add(ctx.label());
                    inputs.add(ctx.input());
                })).executes(ctx -> {
                    labels.add(ctx.label());
                    inputs.add(ctx.input());
                }).build();
        final var dispatcher = CommandFixtures.dispatcher(host, "myplugin", spec);

        run(dispatcher, console, "auction bid 1");
        run(dispatcher, console, "ah bid 2");
        run(dispatcher, console, "myplugin:auction bid 3");
        run(dispatcher, console, "ah");
        assertEquals(List.of("auction", "ah", "auction", "ah"), labels);
        assertEquals(List.of("auction bid 1", "ah bid 2", "myplugin:auction bid 3", "ah"), inputs);
    }

    @Test
    void labelIsTheTypedAliasAlsoBehindRedirects() {
        final List<String> labels = new ArrayList<>();
        final CommandSpec spec = CommandSpec.literal("auction").aliases("ah")
                .sub(SubSpec.literal("bid").argument(AMOUNT).executes(ctx -> labels.add(ctx.label())))
                .build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);
        // Paper also registers "<plugin>:<alias>" for every alias.
        dispatcher.getRoot().addChild(LiteralArgumentBuilder.<CommandSourceStack>literal("plugin:ah")
                .redirect(dispatcher.getRoot().getChild("auction")).build());
        // /execute [as <target>] run <command>: "run" sends the rest back to the dispatcher root, like vanilla.
        dispatcher.getRoot().addChild(LiteralArgumentBuilder.<CommandSourceStack>literal("execute")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("as")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("target",
                                        StringArgumentType.word())
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("run")
                                        .redirect(dispatcher.getRoot()))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("run").redirect(dispatcher.getRoot()))
                .build());

        run(dispatcher, console, "auction bid 1");
        run(dispatcher, console, "ah bid 1");
        run(dispatcher, console, "plugin:ah bid 1");
        run(dispatcher, console, "execute as steve run ah bid 1");
        run(dispatcher, console, "execute run auction bid 1");
        assertEquals(List.of("auction", "ah", "ah", "ah", "auction"), labels);
    }

    @Test
    void inputHelperStripsTheLeadingSlash() {
        assertEquals("ah bid 10", Inputs.input("/ah bid 10"));
        assertEquals("ah bid 10", Inputs.input("ah bid 10"));
    }

    @Test
    void everyPermissionOnThePathIsRequiredAlsoThroughTheAliasRedirect() {
        final List<String> ran = new ArrayList<>();
        final CommandSpec spec = CommandSpec.literal("auction").permission("auction.use")
                .executes(ctx -> ran.add("root"))
                .sub(SubSpec.literal("admin").permission("auction.admin").executes(ctx -> ran.add("admin")))
                .build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);
        // Like Paper: the alias redirects to the command but does not carry its requirement.
        dispatcher.getRoot().addChild(LiteralArgumentBuilder.<CommandSourceStack>literal("ah")
                .redirect(dispatcher.getRoot().getChild("auction")).build());
        final Inbox inbox = new Inbox();
        final CommandSender both = CommandFixtures.player("B", Set.of("auction.use", "auction.admin"), inbox);
        final CommandSender onlyChild = CommandFixtures.player("C", Set.of("auction.admin"), inbox);

        assertEquals(1, run(dispatcher, both, "auction admin"));
        assertEquals(1, run(dispatcher, both, "ah admin"));
        assertEquals(List.of("admin", "admin"), ran);

        assertEquals(0, run(dispatcher, onlyChild, "ah admin"), "root permission missing, reached via the alias");
        assertThrows(CommandSyntaxException.class, () ->
                dispatcher.execute("auction admin", CommandFixtures.source(onlyChild)));
        assertEquals(List.of("admin", "admin"), ran, "handler not run");
        assertEquals(List.of(), inbox.copy(), "refused silently, like a hidden node");
    }

    @Test
    void localeOfNonPlayersIsTheDefaultOfTheLangHandle() {
        final List<Locale> seen = new ArrayList<>();
        final CommandSpec spec = CommandSpec.literal("cmd").executes(ctx -> seen.add(ctx.locale())).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        run(dispatcher, console, "cmd");
        host.defaultLocale = Locale.forLanguageTag("th-TH");
        run(dispatcher, console, "cmd");
        assertEquals(List.of(Locale.US, Locale.forLanguageTag("th-TH")), seen);
    }

    @Test
    void argumentsAreTypedAndOptionalOnesFallBack() {
        final List<String> seen = new ArrayList<>();
        final CommandSpec spec = CommandSpec.literal("cmd")
                .sub(SubSpec.literal("a").argument(LOT).argument(AMOUNT).optional(TIMES, 3).executes(ctx -> {
                    seen.add(ctx.get(LOT) + "|" + ctx.get(AMOUNT) + "|" + ctx.get(TIMES)
                            + "|" + ctx.optional(TIMES) + "|" + ctx.getOrDefault(TIMES, 9)
                            + "|" + ctx.has(TIMES) + "|" + ctx.has(LOT)
                            + "|" + ctx.get("amount", Long.class));
                    try {
                        ctx.get(OTHER);
                    } catch (final IllegalArgumentException e) {
                        seen.add("other: IAE");
                    }
                    try {
                        ctx.get("amount", String.class);
                    } catch (final IllegalArgumentException e) {
                        seen.add("amount: IAE");
                    }
                    try {
                        ctx.get("nope", String.class);
                    } catch (final IllegalArgumentException e) {
                        seen.add("nope: IAE");
                    }
                })).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        run(dispatcher, console, "cmd a lot 5");
        run(dispatcher, console, "cmd a lot 5 7");
        assertEquals(List.of(
                "lot|5|3|Optional.empty|9|false|true|5", "other: IAE", "amount: IAE", "nope: IAE",
                "lot|5|7|Optional[7]|7|true|true|5", "other: IAE", "amount: IAE", "nope: IAE"), seen);
    }

    @Test
    void failStopsWithMessageAndReturnsZero() {
        final List<String> reached = new ArrayList<>();
        final CommandSpec spec = CommandSpec.literal("cmd").executes(ctx -> {
            reached.add("before");
            if (reached.size() == 1) {
                throw ctx.fail("<red>No <thing> for /<label> by <sender>", Placeholder.unparsed("thing", "bid"));
            }
            reached.add("after");
        }).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        assertEquals(0, run(dispatcher, console, "cmd"));
        assertEquals(List.of("before"), reached);
        assertEquals(List.of("No bid for /cmd by CONSOLE"), inbox.copy());
        assertTrue(host.logs.isEmpty(), "a failure is not an error");
    }

    @Test
    void failWithEmptyMessageIsSilent() {
        final CommandSpec spec = CommandSpec.literal("cmd").executes(ctx -> {
            throw new CommandFailure(Component.empty());
        }).build();
        assertEquals(0, run(CommandFixtures.dispatcher(host, spec), console, "cmd"));
        assertEquals(List.of(), inbox.copy());
    }

    @Test
    void unexpectedExceptionIsLoggedWithTheInputAndTheSenderGetsTheErrorMessage() {
        final CommandSpec spec = CommandSpec.literal("cmd")
                .sub(SubSpec.literal("boom").argument(LOT).executes(ctx -> {
                    throw new IllegalStateException("kaboom");
                })).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        assertEquals(0, run(dispatcher, console, "cmd boom abc"));
        assertEquals(List.of("Something went wrong, see the console."), inbox.copy());
        assertEquals(1, host.logs.size());
        assertEquals(Level.SEVERE, host.logs.get(0).getLevel());
        assertTrue(host.logs.get(0).getMessage().contains("cmd boom abc"), host.logs.get(0).getMessage());
        assertInstanceOf(IllegalStateException.class, host.logs.get(0).getThrown());
    }

    @Test
    void closedServiceDoesNothing() {
        final List<String> ran = new ArrayList<>();
        final CommandSpec spec = CommandSpec.literal("cmd").executes(ctx -> ran.add("ran")).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);
        host.closed = true;
        assertEquals(0, run(dispatcher, console, "cmd"));
        assertEquals(List.of(), ran);
        assertEquals(List.of(), inbox.copy());
    }

    @Test
    void callAnswersAndDescribesTheSender() {
        final Inbox playerInbox = new Inbox();
        final Player player = CommandFixtures.player("Steve", Set.of("x.y"), playerInbox);
        final List<String> facts = new ArrayList<>();
        final CommandSpec spec = CommandSpec.literal("cmd").allowConsole().executes(ctx -> {
            facts.add("player=" + ctx.isPlayer() + " console=" + ctx.isConsole()
                    + " present=" + ctx.playerIfPresent().isPresent()
                    + " perm=" + ctx.hasPermission("x.y") + " locale=" + ctx.locale());
            facts.add("executor=" + (ctx.executor() != null) + " location=" + (ctx.location() != null)
                    + " source=" + (ctx.source() != null));
            ctx.reply("<green>Hi <sender> via <label>");
            ctx.reply(Component.text("component"));
            ctx.actionBar("<gray>bar");
            try {
                ctx.player();
            } catch (final IllegalStateException e) {
                facts.add("player(): ISE");
            }
        }).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        run(dispatcher, player, "cmd");
        run(dispatcher, console, "cmd");
        assertEquals(List.of(
                "player=true console=false present=true perm=true locale=en_US",
                "executor=true location=true source=true",
                "player=false console=true present=false perm=true locale=en_US",
                "executor=false location=true source=true",
                "player(): ISE"), facts);
        assertEquals(List.of("Hi Steve via cmd", "component", "actionbar:bar"), playerInbox.copy());
        assertEquals(List.of("Hi CONSOLE via cmd", "component", "actionbar:bar"), inbox.copy());
    }

    @Test
    void suggestionsAreFilteredByThePrefixAndEmptyWhenClosed() throws Exception {
        final Arg<String> lot = Arg.of("lot", Args.word())
                .suggests(source -> List.of("alpha", "Beta", "alpine", "gamma"));
        final CommandSpec spec = CommandSpec.literal("cmd")
                .sub(SubSpec.literal("cancel").argument(lot).executes(ctx -> {
                })).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);
        final CommandSourceStack source = CommandFixtures.source(console);

        assertEquals(List.of("alpha", "alpine"), suggest(dispatcher, source, "cmd cancel al"));
        assertEquals(List.of("Beta"), suggest(dispatcher, source, "cmd cancel BE"));
        assertEquals(List.of("alpha", "alpine", "Beta", "gamma"), suggest(dispatcher, source, "cmd cancel "));
        host.closed = true;
        assertEquals(List.of(), suggest(dispatcher, source, "cmd cancel "));
    }

    private static List<String> suggest(final CommandDispatcher<CommandSourceStack> dispatcher,
                                        final CommandSourceStack source, final String input) throws Exception {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input, source)).get().getList().stream()
                .map(s -> s.getText()).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    @Test
    void oneSpecRunsOnManyThreadsWithoutSharedState() throws Exception {
        final Arg<Integer> number = Arg.of("n", Args.integer(0, 1_000_000));
        final CommandSpec spec = CommandSpec.literal("cc").aliases("c2")
                .sub(SubSpec.literal("say").argument(number)
                        .executes(ctx -> ctx.reply("<label>:<sender>:" + ctx.get(number))))
                .build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        final int threads = 8;
        final int rounds = 300;
        final List<Inbox> inboxes = new ArrayList<>();
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final Inbox mine = new Inbox();
            inboxes.add(mine);
            final CommandSourceStack source = CommandFixtures.source(
                    CommandFixtures.player("T" + t, Set.of(), mine));
            final String label = t % 2 == 0 ? "cc" : "c2";
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < rounds; i++) {
                    dispatcher.execute(label + " say " + i, source);
                }
                return null;
            }));
        }
        start.countDown();
        for (final Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        for (int t = 0; t < threads; t++) {
            final List<String> expected = new ArrayList<>();
            for (int i = 0; i < rounds; i++) {
                expected.add((t % 2 == 0 ? "cc" : "c2") + ":T" + t + ":" + i);
            }
            assertEquals(expected, inboxes.get(t).copy(), "thread " + t);
        }
        assertTrue(host.logs.isEmpty());
    }

    @Test
    void compilingTwiceBuildsTheSameTreeButFreshNodes() {
        final CommandSpec spec = CommandSpec.literal("cmd").aliases("c")
                .executes(ctx -> {
                })
                .sub(SubSpec.literal("a").argument(LOT).optional(TIMES, 1).executes(ctx -> {
                })).build();
        final var first = spec.compile(host);
        final var second = spec.compile(host);
        assertEquals(CommandFixtures.shape(first), CommandFixtures.shape(second));
        assertNotNull(first);
        assertFalse(first == second);
        assertFalse(first.getChild("a") == second.getChild("a"));
        assertEquals("cmd*(a(<lot>*(<times>*)))", CommandFixtures.shape(first));
    }
}
