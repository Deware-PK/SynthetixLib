package com.github.dewarepk.synthetixlib.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.command.CommandFixtures.Inbox;
import com.github.dewarepk.synthetixlib.command.CommandFixtures.TestHost;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Cooldown semantics of command.md section 5, with a fake clock and no server. */
class CommandCooldownTest {

    private static final Duration FIVE = Duration.ofSeconds(5);

    private final TestHost host = new TestHost();
    private final Inbox inbox = new Inbox();
    private final Player steve = CommandFixtures.player("Steve", Set.of(), inbox);
    private final List<String> ran = java.util.Collections.synchronizedList(new ArrayList<>());

    private static UUID id(final Player player) {
        return player.getUniqueId();
    }

    private int run(final CommandDispatcher<CommandSourceStack> dispatcher, final CommandSender sender,
                    final String input) {
        try {
            return dispatcher.execute(input, CommandFixtures.source(sender));
        } catch (final CommandSyntaxException e) {
            throw new AssertionError("\"" + input + "\" did not parse: " + e.getMessage(), e);
        }
    }

    private void advance(final Duration time) {
        host.nanos.addAndGet(time.toNanos());
    }

    private CommandDispatcher<CommandSourceStack> shop() {
        final CommandSpec spec = CommandSpec.literal("shop").aliases("s")
                .sub(SubSpec.literal("bid").cooldown(FIVE).executes(ctx -> ran.add("bid")))
                .sub(SubSpec.literal("sell").cooldown(FIVE).executes(ctx -> ran.add("sell")))
                .sub(SubSpec.literal("free").executes(ctx -> ran.add("free")))
                .build();
        return CommandFixtures.dispatcher(host, spec);
    }

    @Test
    void secondCallIsBlockedWithTheRemainingTimeAndFreedWhenItPasses() {
        final var dispatcher = shop();

        assertEquals(1, run(dispatcher, steve, "shop bid"));
        assertEquals(0, run(dispatcher, steve, "shop bid"));
        assertEquals(List.of("bid"), ran);
        assertEquals(List.of("Wait 5s before using /shop again."), inbox.copy());

        advance(Duration.ofSeconds(4));
        assertEquals(0, run(dispatcher, steve, "s bid"));
        assertEquals("Wait 1s before using /s again.", inbox.copy().get(1), "the alias that was typed");

        advance(Duration.ofSeconds(1));
        assertEquals(1, run(dispatcher, steve, "shop bid"));
        assertEquals(List.of("bid", "bid"), ran);
    }

    @Test
    void theTimeIsRoundedUpToWholeSecondsInTheConfigFormat() {
        final var dispatcher = shop();
        run(dispatcher, steve, "shop bid");

        advance(Duration.ofMillis(1_200));
        run(dispatcher, steve, "shop bid");
        advance(Duration.ofMillis(3_799));
        run(dispatcher, steve, "shop bid");
        assertEquals(List.of("Wait 4s before using /shop again.", "Wait 1s before using /shop again."),
                inbox.copy());

        assertEquals("1m31s", Executor.formatWait(Duration.ofMillis(90_100)));
        assertEquals("1s", Executor.formatWait(Duration.ofNanos(1)));
        assertEquals("1h", Executor.formatWait(Duration.ofHours(1)));
    }

    @Test
    void eachExecutableNodeHasItsOwnCooldown() {
        final var dispatcher = shop();

        assertEquals(1, run(dispatcher, steve, "shop bid"));
        assertEquals(1, run(dispatcher, steve, "shop sell"), "bid does not block sell");
        assertEquals(0, run(dispatcher, steve, "shop bid"));
        assertEquals(0, run(dispatcher, steve, "shop sell"));
        assertEquals(1, run(dispatcher, steve, "shop free"), "no cooldown on a node without one");
        assertEquals(1, run(dispatcher, steve, "shop free"));
        assertEquals(List.of("bid", "sell", "free", "free"), ran);
    }

    @Test
    void cooldownsAreInheritedUnlessOverridden() {
        final CommandSpec spec = CommandSpec.literal("cmd").cooldown(Duration.ofSeconds(10))
                .executes(ctx -> ran.add("root"))
                .sub(SubSpec.literal("inherits").executes(ctx -> ran.add("inherits")))
                .sub(SubSpec.literal("shorter").cooldown(Duration.ofSeconds(2)).executes(ctx -> ran.add("shorter")))
                .sub(SubSpec.literal("none").cooldown(Duration.ZERO).executes(ctx -> ran.add("none")))
                .sub(SubSpec.literal("group").sub(SubSpec.literal("deep").executes(ctx -> ran.add("deep"))))
                .build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        assertEquals(1, run(dispatcher, steve, "cmd inherits"));
        advance(Duration.ofSeconds(3));
        assertEquals(0, run(dispatcher, steve, "cmd inherits"), "10 s inherited");
        assertEquals(1, run(dispatcher, steve, "cmd shorter"));
        advance(Duration.ofSeconds(2));
        assertEquals(1, run(dispatcher, steve, "cmd shorter"), "2 s of its own");
        assertEquals(1, run(dispatcher, steve, "cmd none"));
        assertEquals(1, run(dispatcher, steve, "cmd none"), "ZERO removes the inherited cooldown");
        assertEquals(1, run(dispatcher, steve, "cmd group deep"));
        assertEquals(0, run(dispatcher, steve, "cmd group deep"), "inherited through a group");
        assertEquals(1, run(dispatcher, steve, "cmd"));
        assertEquals(0, run(dispatcher, steve, "cmd"));
    }

    @Test
    void notStartedWhenTheHandlerFailsOrThrows() {
        final AtomicInteger calls = new AtomicInteger();
        final CommandSpec spec = CommandSpec.literal("cmd").cooldown(FIVE).executes(ctx -> {
            final int call = calls.incrementAndGet();
            if (call == 1) {
                throw ctx.fail("<red>Not enough money");
            }
            if (call == 2) {
                throw new IllegalStateException("boom");
            }
        }).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        assertEquals(0, run(dispatcher, steve, "cmd"));
        assertEquals(0, run(dispatcher, steve, "cmd"));
        assertEquals(0, host.cooldowns.size(), "nothing started");
        assertEquals(1, run(dispatcher, steve, "cmd"), "third call still runs the handler");
        assertEquals(3, calls.get());
        assertEquals(1, host.cooldowns.size());
        assertEquals(0, run(dispatcher, steve, "cmd"));
        assertEquals(3, calls.get(), "now it is blocked");
    }

    @Test
    void bypassPermissionSkipsTheCooldownEntirely() {
        final CommandSpec spec = CommandSpec.literal("cmd").cooldown(FIVE, "shop.bypass")
                .executes(ctx -> ran.add("ran")).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);
        final Player vip = CommandFixtures.player("Vip", Set.of("shop.bypass"), new Inbox());

        assertEquals(1, run(dispatcher, vip, "cmd"));
        assertEquals(1, run(dispatcher, vip, "cmd"));
        assertEquals(0, host.cooldowns.size(), "nothing stored for the bypass holder");
        assertEquals(1, run(dispatcher, steve, "cmd"));
        assertEquals(0, run(dispatcher, steve, "cmd"), "others are still limited");
    }

    @Test
    void consoleAndOtherNonPlayersHaveNoCooldown() {
        final CommandSpec spec = CommandSpec.literal("cmd").allowConsole().cooldown(FIVE)
                .executes(ctx -> ran.add("ran")).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);
        final CommandSender console = CommandFixtures.console(inbox);

        assertEquals(1, run(dispatcher, console, "cmd"));
        assertEquals(1, run(dispatcher, console, "cmd"));
        assertEquals(0, host.cooldowns.size());
    }

    @Test
    void skipCooldownLetsThisRunSucceedWithoutStartingIt() {
        final AtomicInteger calls = new AtomicInteger();
        final CommandSpec spec = CommandSpec.literal("cmd").cooldown(FIVE).executes(ctx -> {
            if (calls.incrementAndGet() == 1) {
                ctx.skipCooldown();
            }
        }).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        assertEquals(1, run(dispatcher, steve, "cmd"));
        assertEquals(1, run(dispatcher, steve, "cmd"), "the first run did not start it");
        assertEquals(0, run(dispatcher, steve, "cmd"), "the second one did");
    }

    @Test
    void resetCooldownBeforeTheHandlerReturnsStopsItFromStarting() {
        final CommandSpec spec = CommandSpec.literal("cmd").cooldown(FIVE).executes(ctx -> ctx.resetCooldown())
                .build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        assertEquals(1, run(dispatcher, steve, "cmd"));
        assertEquals(1, run(dispatcher, steve, "cmd"));
        assertEquals(0, host.cooldowns.size());
    }

    @Test
    void resetCooldownLaterRemovesTheOneThatStartedAndCooldownLeftReports() {
        final AtomicReference<CommandCall> saved = new AtomicReference<>();
        final CommandSpec spec = CommandSpec.literal("cmd").cooldown(FIVE).executes(ctx -> {
            assertEquals(Duration.ZERO, ctx.cooldownLeft(), "nothing yet while the handler runs");
            saved.set(ctx);
        }).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        run(dispatcher, steve, "cmd");
        advance(Duration.ofSeconds(2));
        assertEquals(Duration.ofSeconds(3), saved.get().cooldownLeft());
        assertEquals(0, run(dispatcher, steve, "cmd"));

        saved.get().resetCooldown();
        assertEquals(Duration.ZERO, saved.get().cooldownLeft());
        assertEquals(1, run(dispatcher, steve, "cmd"), "refunded");
    }

    @Test
    void cooldownLeftIsZeroForTheConsole() {
        final AtomicReference<Duration> left = new AtomicReference<>();
        final CommandSpec spec = CommandSpec.literal("cmd").allowConsole().cooldown(FIVE)
                .executes(ctx -> left.set(ctx.cooldownLeft())).build();
        run(CommandFixtures.dispatcher(host, spec), CommandFixtures.console(inbox), "cmd");
        assertEquals(Duration.ZERO, left.get());
    }

    @Test
    void quittingClearsThePlayersCooldownsOnly() {
        final var dispatcher = shop();
        final Player alex = CommandFixtures.player("Alex", Set.of(), new Inbox());
        run(dispatcher, steve, "shop bid");
        run(dispatcher, steve, "shop sell");
        run(dispatcher, alex, "shop bid");
        assertEquals(3, host.cooldowns.size());

        host.cooldowns.clear(id(steve));  // what the PlayerQuitEvent listener of the service does
        assertEquals(1, host.cooldowns.size());
        assertEquals(1, run(dispatcher, steve, "shop bid"));
        assertEquals(0, run(dispatcher, alex, "shop bid"), "Alex is still on cooldown");
    }

    @Test
    void expiredEntriesDoNotAccumulate() {
        final var dispatcher = shop();
        for (int i = 0; i < 20; i++) {
            run(dispatcher, CommandFixtures.player("P" + i, Set.of(), new Inbox()), "shop bid");
        }
        assertEquals(20, host.cooldowns.size());

        advance(Duration.ofSeconds(3));
        host.cooldowns.sweep();
        assertEquals(20, host.cooldowns.size(), "nothing expired yet");

        advance(Duration.ofSeconds(3));
        run(dispatcher, steve, "shop bid");
        assertEquals(21, host.cooldowns.size(), "expired ones stay until looked at or swept");
        host.cooldowns.sweep();
        assertEquals(1, host.cooldowns.size(), "the sweep dropped all 20 expired entries");

        // clean on access: looking at an expired entry removes it
        advance(Duration.ofSeconds(10));
        assertEquals(Duration.ZERO, host.cooldowns.left(id(steve), "shop bid"));
        assertEquals(0, host.cooldowns.size());
    }

    @Test
    void closingTheServiceDropsEverything() {
        final var dispatcher = shop();
        run(dispatcher, steve, "shop bid");
        host.cooldowns.clearAll();
        assertEquals(0, host.cooldowns.size());
    }

    @Test
    void hugeCooldownsDoNotOverflow() {
        host.cooldowns.start(id(steve), "x", Duration.ofSeconds(Long.MAX_VALUE / 4));
        host.cooldowns.start(id(steve), "y", java.time.temporal.ChronoUnit.FOREVER.getDuration());
        assertTrue(host.cooldowns.left(id(steve), "x").compareTo(Duration.ofDays(365)) > 0);
        assertTrue(host.cooldowns.left(id(steve), "y").compareTo(Duration.ofDays(365)) > 0);
    }

    @Test
    void manyThreadsManyPlayersLoseAndInventNoCooldowns() throws Exception {
        final int threads = 8;
        final int playersPerThread = 60;
        final CommandSpec spec = CommandSpec.literal("cmd").cooldown(FIVE).executes(ctx -> {
            ran.add(ctx.sender().getName());
        }).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);
        final List<Inbox> inboxes = java.util.Collections.synchronizedList(new ArrayList<>());
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<int[]>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int thread = t;
            futures.add(pool.submit(() -> {
                final List<CommandSourceStack> sources = new ArrayList<>();
                for (int p = 0; p < playersPerThread; p++) {
                    final Inbox mine = new Inbox();
                    inboxes.add(mine);
                    sources.add(CommandFixtures.source(
                            CommandFixtures.player("T" + thread + "P" + p, Set.of(), mine)));
                }
                start.await();
                final int[] results = new int[2];
                for (int round = 0; round < 3; round++) {
                    for (final CommandSourceStack source : sources) {
                        results[dispatcher.execute("cmd", source) == 1 ? 0 : 1]++;
                    }
                }
                return results;
            }));
        }
        start.countDown();
        int accepted = 0;
        int blocked = 0;
        for (final Future<int[]> future : futures) {
            final int[] result = future.get(30, TimeUnit.SECONDS);
            accepted += result[0];
            blocked += result[1];
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        final int players = threads * playersPerThread;
        assertEquals(players, accepted, "each player got exactly one run");
        assertEquals(players * 2, blocked, "and was blocked on the other rounds");
        assertEquals(players, host.cooldowns.size(), "one entry per player");
        assertEquals(players, new java.util.HashSet<>(ran).size(), "every player ran once");
        for (final Inbox mine : inboxes) {
            assertEquals(2, mine.copy().size(), "two blocked messages each");
        }

        advance(FIVE);
        host.cooldowns.sweep();
        assertEquals(0, host.cooldowns.size());
    }

    @Test
    void settingTheCooldownTwiceKeepsTheLastAndTheSpecIsImmutable() {
        final SubSpec sub = SubSpec.literal("bid").cooldown(Duration.ofSeconds(1)).cooldown(FIVE)
                .executes(ctx -> ran.add("bid"));
        final var dispatcher = CommandFixtures.dispatcher(host, CommandSpec.literal("cmd").sub(sub).build());
        assertEquals(1, run(dispatcher, steve, "cmd bid"));
        advance(Duration.ofSeconds(2));
        assertEquals(0, run(dispatcher, steve, "cmd bid"));
        assertThrows(IllegalArgumentException.class, () -> SubSpec.literal("x").cooldown(Duration.ofSeconds(-1)));
    }
}
