package com.github.dewarepk.synthetixlib.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.command.CommandFixtures.Inbox;
import com.github.dewarepk.synthetixlib.command.CommandFixtures.TestHost;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

/** The converters of {@link Args} that need no server: parse errors, matching, suggestions, the player lookup. */
class CommandArgsTest {

    private enum Wood {
        OAK, DARK_OAK, SPRUCE
    }

    private final TestHost host = new TestHost();
    private final Inbox inbox = new Inbox();
    private final CommandSender console = CommandFixtures.console(inbox);

    private int run(final CommandDispatcher<CommandSourceStack> dispatcher, final String input) {
        try {
            return dispatcher.execute(input, CommandFixtures.source(console));
        } catch (final CommandSyntaxException e) {
            throw new AssertionError("\"" + input + "\" did not parse: " + e.getMessage(), e);
        }
    }

    private String rejection(final CommandDispatcher<CommandSourceStack> dispatcher, final String input) {
        final CommandSyntaxException e = assertThrows(CommandSyntaxException.class, () ->
                dispatcher.execute(input, CommandFixtures.source(console)));
        return e.getRawMessage().getString();
    }

    private static List<String> suggest(final CommandDispatcher<CommandSourceStack> dispatcher, final String input)
            throws Exception {
        final CommandSourceStack source = CommandFixtures.source(CommandFixtures.console(new Inbox()));
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input, source)).get().getList().stream()
                .map(s -> s.getText()).sorted().toList();
    }

    private <T> CommandDispatcher<CommandSourceStack> dispatcherOf(final Arg<T> arg, final List<T> seen) {
        final CommandSpec spec = CommandSpec.literal("cmd")
                .sub(SubSpec.literal("set").argument(arg).executes(ctx -> seen.add(ctx.get(arg)))).build();
        return CommandFixtures.dispatcher(host, spec);
    }

    @Test
    void durationUsesTheConfigSyntax() {
        final List<Duration> seen = new ArrayList<>();
        final var dispatcher = dispatcherOf(Arg.of("time", Args.duration()), seen);

        run(dispatcher, "cmd set 1h30m");
        run(dispatcher, "cmd set 250ms");
        run(dispatcher, "cmd set 2d");
        assertEquals(List.of(Duration.ofMinutes(90), Duration.ofMillis(250), Duration.ofDays(2)), seen);
    }

    @Test
    void invalidDurationIsRejectedWhileParsingWithTheLocaleMessage() {
        final List<Duration> seen = new ArrayList<>();
        final var dispatcher = dispatcherOf(Arg.of("time", Args.duration()), seen);

        final String message = rejection(dispatcher, "cmd set 5q");
        assertTrue(message.startsWith("5q is not valid: "), message);
        assertTrue(message.contains("unknown unit 'q'"), "the reason comes from the shared config parser: " + message);
        assertTrue(rejection(dispatcher, "cmd set 30").contains("missing unit"));
        assertEquals(List.of(), seen, "the handler never ran");
        assertEquals(List.of(), inbox.copy(), "Brigadier shows it, we do not send it too");
    }

    @Test
    void enumOfAcceptsNameKebabAndAnyCase() {
        final List<Wood> seen = new ArrayList<>();
        final var dispatcher = dispatcherOf(Arg.of("wood", Args.enumOf(Wood.class)), seen);

        run(dispatcher, "cmd set dark-oak");
        run(dispatcher, "cmd set DARK_OAK");
        run(dispatcher, "cmd set Spruce");
        run(dispatcher, "cmd set oak");
        assertEquals(List.of(Wood.DARK_OAK, Wood.DARK_OAK, Wood.SPRUCE, Wood.OAK), seen);
    }

    @Test
    void enumOfRejectsUnknownAndListsTheKebabNames() {
        final List<Wood> seen = new ArrayList<>();
        final var dispatcher = dispatcherOf(Arg.of("wood", Args.enumOf(Wood.class)), seen);

        assertEquals("birch is not valid: expected one of: oak, dark-oak, spruce", rejection(dispatcher, "cmd set birch"));
        assertEquals(List.of(), seen);
    }

    @Test
    void enumOfSuggestsLowerKebabCaseFilteredByPrefixIgnoringCase() throws Exception {
        final var dispatcher = dispatcherOf(Arg.of("wood", Args.enumOf(Wood.class)), new ArrayList<>());

        assertEquals(List.of("dark-oak", "oak", "spruce"), suggest(dispatcher, "cmd set "));
        assertEquals(List.of("dark-oak"), suggest(dispatcher, "cmd set DA"));
        assertEquals(List.of("spruce"), suggest(dispatcher, "cmd set s"));
    }

    @Test
    void oneOfIsCheckedAgainAtExecutionAndKeepsTheSpellingOfTheSet() throws Exception {
        final Set<String> lots = new HashSet<>(Set.of("alpha", "Beta"));
        final List<String> seen = new ArrayList<>();
        final var dispatcher = dispatcherOf(Arg.of("lot", Args.oneOf(() -> Set.copyOf(lots))), seen);

        run(dispatcher, "cmd set alpha");
        run(dispatcher, "cmd set BETA");
        assertEquals(List.of("alpha", "Beta"), seen);
        assertEquals(List.of("Beta", "alpha"), suggest(dispatcher, "cmd set "));

        lots.remove("alpha");
        final String message = rejection(dispatcher, "cmd set alpha");
        assertEquals("alpha is not valid: expected one of: Beta", message, "gone since the tab-complete");
        assertEquals(List.of("alpha", "Beta"), seen);
        assertEquals(List.of("Beta"), suggest(dispatcher, "cmd set "));
    }

    @Test
    void customConvertsAndRejectsWithTheReason() throws Exception {
        final List<Integer> seen = new ArrayList<>();
        final ArgType<Integer> lot = Args.custom(StringArgumentType.word(), id -> {
            if (id.equals("known")) {
                return 7;
            }
            throw new IllegalArgumentException("no such lot");
        }, source -> List.of("known", "kite"));
        final var dispatcher = dispatcherOf(Arg.of("lot", lot), seen);

        run(dispatcher, "cmd set known");
        assertEquals(List.of(7), seen);
        assertEquals("nope is not valid: no such lot", rejection(dispatcher, "cmd set nope"));
        assertEquals(List.of("kite", "known"), suggest(dispatcher, "cmd set k"));
        assertEquals(List.of("kite"), suggest(dispatcher, "cmd set KI"));
    }

    @Test
    void customWithANumericNativeTypeUsesItsTextAsInput() {
        final List<String> seen = new ArrayList<>();
        final ArgType<String> even = Args.custom(IntegerArgumentType.integer(0, 100), n -> {
            if (n % 2 != 0) {
                throw new IllegalArgumentException("must be even");
            }
            return "even " + n;
        });
        final var dispatcher = dispatcherOf(Arg.of("n", even), seen);

        run(dispatcher, "cmd set 42");
        assertEquals(List.of("even 42"), seen);
        assertEquals("7 is not valid: must be even", rejection(dispatcher, "cmd set 7"));
        assertThrows(CommandSyntaxException.class, () ->
                dispatcher.execute("cmd set 1000", CommandFixtures.source(console)), "vanilla range error stays");
    }

    @Test
    void customRejectsANullResult() {
        final var dispatcher = dispatcherOf(Arg.of("x", Args.<String, String>custom(StringArgumentType.word(),
                text -> null)), new ArrayList<>());
        assertTrue(rejection(dispatcher, "cmd set x").startsWith("x is not valid"));
    }

    @Test
    void argSuggestsOverridesTheTypeAndIsFilteredCaseInsensitively() throws Exception {
        final Arg<Wood> wood = Arg.of("wood", Args.enumOf(Wood.class)).suggests(source -> List.of("Acacia", "alder", "birch"));
        final var dispatcher = dispatcherOf(wood, new ArrayList<>());

        assertEquals(List.of("Acacia", "alder"), suggest(dispatcher, "cmd set A"));
        assertEquals(List.of("birch"), suggest(dispatcher, "cmd set b"));
    }

    @Test
    void aTypeUsedWithoutAServiceStillRejectsWithThePlainReason() {
        final ArgumentType<?> nativeType = Args.duration().nativeType();
        final CommandSyntaxException e = assertThrows(CommandSyntaxException.class, () ->
                nativeType.parse(new StringReader("5q")));
        assertTrue(e.getRawMessage().getString().contains("unknown unit 'q'"), e.getRawMessage().getString());
    }

    // --- offlinePlayer -------------------------------------------------------------------------------------------

    private static OfflinePlayer offline(final String name) {
        return (OfflinePlayer) Proxy.newProxyInstance(CommandArgsTest.class.getClassLoader(),
                new Class<?>[]{OfflinePlayer.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "hashCode" -> name.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "OfflinePlayer(" + name + ")";
                    default -> null;
                });
    }

    @Test
    void offlinePlayerFindsOnlyCachedPlayers() {
        final OfflinePlayer cached = offline("Steve");
        final AtomicReference<String> asked = new AtomicReference<>();
        final ArgType<OfflinePlayer> type = Args.offlinePlayerWith((source, name) -> {
            asked.set(name);
            return name.equals("Steve") ? cached : null;
        }, source -> List.of("Alex", "Steve"));
        final List<OfflinePlayer> seen = new ArrayList<>();
        final var dispatcher = dispatcherOf(Arg.of("who", type), seen);

        run(dispatcher, "cmd set Steve");
        assertEquals(1, seen.size());
        assertSame(cached, seen.get(0));
        assertEquals("Steve", asked.get(), "the typed name goes to the profile cache as is");
    }

    @Test
    void offlinePlayerCacheMissAnswersPlayerNotFoundAndTheHandlerStops() throws Exception {
        final ArgType<OfflinePlayer> type = Args.offlinePlayerWith((source, name) -> null,
                source -> List.of("Alex", "Steve"));
        final List<OfflinePlayer> seen = new ArrayList<>();
        final var dispatcher = dispatcherOf(Arg.of("who", type), seen);

        assertEquals(0, run(dispatcher, "cmd set Herobrine"));
        assertEquals(List.of("No player found: Herobrine"), inbox.copy());
        assertEquals(List.of(), seen);
        assertEquals(List.of("Alex"), suggest(dispatcher, "cmd set al"), "online names, any case");
    }

    @Test
    void offlinePlayerIsLookedUpOncePerRun() {
        final int[] lookups = {0};
        final OfflinePlayer cached = offline("Steve");
        final Arg<OfflinePlayer> who = Arg.of("who", Args.offlinePlayerWith((source, name) -> {
            lookups[0]++;
            return cached;
        }, source -> List.of()));
        final CommandSpec spec = CommandSpec.literal("cmd").argument(who).executes(ctx -> {
            ctx.get(who);
            ctx.get(who);
            ctx.optional(who);
        }).build();
        run(CommandFixtures.dispatcher(host, spec), "cmd Steve");
        assertEquals(1, lookups[0]);
    }
}
