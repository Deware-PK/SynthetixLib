package com.github.dewarepk.synthetixlib.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.command.CommandFixtures.Inbox;
import com.github.dewarepk.synthetixlib.command.CommandFixtures.TestHost;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** One async run per player and node at a time (P2-17a). */
class CommandInFlightTest {

    private static final String BUSY = "Please wait, /cmd is still running.";

    private final TestHost host = new TestHost();
    private final Inbox inbox = new Inbox();
    private final Player steve = CommandFixtures.player("Steve", Set.of(), inbox);
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch entered = new CountDownLatch(1);
    private final AtomicInteger runs = new AtomicInteger();

    @AfterEach
    void stop() throws InterruptedException {
        release.countDown();
        host.shutdownAsync();
    }

    private int run(final CommandDispatcher<CommandSourceStack> dispatcher, final CommandSender sender,
                    final String input) {
        try {
            return dispatcher.execute(input, CommandFixtures.source(sender));
        } catch (final CommandSyntaxException e) {
            throw new AssertionError("\"" + input + "\" did not parse: " + e.getMessage(), e);
        }
    }

    private void awaitRelease() {
        entered.countDown();
        try {
            release.await();
        } catch (final InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Lets the running handlers finish and waits until they are done. */
    private void finishRunning() throws InterruptedException {
        release.countDown();
        host.shutdownAsync();
    }

    @Test
    void aSecondRunOfTheSameNodeWhileTheFirstIsRunningIsBusy() throws Exception {
        final CommandSpec spec = CommandSpec.literal("cmd").executesAsync(ctx -> {
            runs.incrementAndGet();
            awaitRelease();
        }).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        assertEquals(1, run(dispatcher, steve, "cmd"));
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        assertEquals(1, host.cooldowns.inFlightSize());
        assertEquals(0, run(dispatcher, steve, "cmd"));
        assertEquals(List.of(BUSY), inbox.copy());

        finishRunning();
        assertEquals(1, runs.get(), "the handler ran once");
        assertEquals(0, host.cooldowns.inFlightSize(), "released after the return");
    }

    @Test
    void theAliasThatWasTypedIsInTheBusyMessage() throws Exception {
        final CommandSpec spec = CommandSpec.literal("cmd").aliases("c").executesAsync(ctx -> awaitRelease()).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        run(dispatcher, steve, "cmd");
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        run(dispatcher, steve, "c");
        assertEquals(List.of("Please wait, /c is still running."), inbox.copy());
    }

    @Test
    void releasedAfterANormalReturnAndTheCooldownThenStartsWithoutOverlap() throws Exception {
        final CommandSpec spec = CommandSpec.literal("cmd").cooldown(Duration.ofSeconds(5)).executesAsync(ctx -> {
            runs.incrementAndGet();
            awaitRelease();
        }).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        run(dispatcher, steve, "cmd");
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        assertEquals(0, run(dispatcher, steve, "cmd"), "busy, not cooldown: it has not started yet");
        assertEquals(List.of(BUSY), inbox.copy());

        finishRunning();
        assertEquals(0, host.cooldowns.inFlightSize());
        assertEquals(1, host.cooldowns.size(), "the cooldown started after the release");
        assertEquals(0, run(dispatcher, steve, "cmd"));
        assertEquals("Wait 5s before using /cmd again.", inbox.copy().get(1));
    }

    @Test
    void releasedAfterFailAndAfterAnException() throws Exception {
        final CommandSpec spec = CommandSpec.literal("cmd")
                .sub(SubSpec.literal("fail").executesAsync(ctx -> {
                    throw ctx.fail("<red>No");
                }))
                .sub(SubSpec.literal("boom").executesAsync(ctx -> {
                    throw new IllegalStateException("boom");
                })).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        for (int i = 0; i < 3; i++) {
            assertEquals(1, run(dispatcher, steve, "cmd fail"));
            assertEquals(1, run(dispatcher, steve, "cmd boom"));
            host.awaitAsyncIdle();
            assertEquals(0, host.cooldowns.inFlightSize(), "round " + i);
        }
        assertEquals(0, inbox.copy().stream().filter(BUSY::equals).count(), "never busy: each run was released");
    }

    @Test
    void releasedWhenAnArgumentCannotBeRead() throws Exception {
        final Arg<org.bukkit.OfflinePlayer> who = Arg.of("who", Args.offlinePlayerWith((source, name) -> null,
                source -> List.of()));
        final CommandSpec spec = CommandSpec.literal("cmd").argument(who).executesAsync(ctx -> runs.incrementAndGet())
                .build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        assertEquals(0, run(dispatcher, steve, "cmd Herobrine"));
        assertEquals(0, host.cooldowns.inFlightSize());
        assertEquals(0, run(dispatcher, steve, "cmd Herobrine"));
        assertEquals(List.of("No player found: Herobrine", "No player found: Herobrine"), inbox.copy());
        assertEquals(0, runs.get());
    }

    @Test
    void releasedWhenTheSchedulerCannotQueueAndNothingIsSaid() throws Exception {
        final CommandSpec spec = CommandSpec.literal("cmd").executesAsync(ctx -> runs.incrementAndGet()).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        host.failAsync = true;
        assertEquals(0, run(dispatcher, steve, "cmd"), "the exception does not escape into Brigadier");
        assertEquals(0, host.cooldowns.inFlightSize());
        assertEquals(List.of(), inbox.copy(), "no message");
        assertEquals(1, host.logs.size());
        assertEquals(Level.FINE, host.logs.get(0).getLevel());

        host.failAsync = false;
        assertEquals(1, run(dispatcher, steve, "cmd"), "not stuck busy");
        host.awaitAsyncIdle();
        assertEquals(1, runs.get());
    }

    @Test
    void differentPlayersAndDifferentNodesDoNotBlockEachOther() throws Exception {
        final CountDownLatch bothIn = new CountDownLatch(3);
        final CommandSpec spec = CommandSpec.literal("cmd")
                .sub(SubSpec.literal("a").executesAsync(ctx -> {
                    bothIn.countDown();
                    awaitRelease();
                }))
                .sub(SubSpec.literal("b").executesAsync(ctx -> {
                    bothIn.countDown();
                    awaitRelease();
                })).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);
        final Player alex = CommandFixtures.player("Alex", Set.of(), new Inbox());

        assertEquals(1, run(dispatcher, steve, "cmd a"));
        assertEquals(1, run(dispatcher, steve, "cmd b"), "another node");
        assertEquals(1, run(dispatcher, alex, "cmd a"), "another player");
        assertTrue(bothIn.await(10, TimeUnit.SECONDS), "all three run at the same time");
        assertEquals(3, host.cooldowns.inFlightSize());
        assertEquals(List.of(), inbox.copy());
    }

    @Test
    void theConsoleHasNoGuard() throws Exception {
        final CommandSpec spec = CommandSpec.literal("cmd").allowConsole().executesAsync(ctx -> {
            runs.incrementAndGet();
            awaitRelease();
        }).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);
        final CommandSender console = CommandFixtures.console(inbox);

        assertEquals(1, run(dispatcher, console, "cmd"));
        assertEquals(1, run(dispatcher, console, "cmd"));
        assertEquals(0, host.cooldowns.inFlightSize());
        finishRunning();
        assertEquals(2, runs.get());
    }

    @Test
    void syncNodesAreNotGuarded() {
        final CommandSpec spec = CommandSpec.literal("cmd").executes(ctx -> runs.incrementAndGet()).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);
        run(dispatcher, steve, "cmd");
        run(dispatcher, steve, "cmd");
        assertEquals(2, runs.get());
        assertEquals(0, host.cooldowns.inFlightSize());
    }

    @Test
    void quittingKeepsTheReservationSoARejoinCannotRunTwice() throws Exception {
        final CommandSpec spec = CommandSpec.literal("cmd").executesAsync(ctx -> {
            runs.incrementAndGet();
            awaitRelease();
        }).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        run(dispatcher, steve, "cmd");
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        host.cooldowns.clear(steve.getUniqueId());          // the player quit
        assertEquals(1, host.cooldowns.inFlightSize(), "the running handler still holds its reservation");
        assertEquals(0, run(dispatcher, steve, "cmd"), "a rejoin cannot start a second run");
        assertEquals(1, runs.get());

        finishRunning();
        assertEquals(0, host.cooldowns.inFlightSize(), "released by the handler's own finally");
    }

    @Test
    void theCooldownStartsBeforeTheReservationIsReleased() throws Exception {
        final CommandSpec spec = CommandSpec.literal("cmd").cooldown(Duration.ofMinutes(1))
                .executesAsync(ctx -> runs.incrementAndGet()).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);
        final List<Duration> leftAtRelease = Collections.synchronizedList(new ArrayList<>());
        host.onRelease = () -> leftAtRelease.add(host.cooldowns.left(steve.getUniqueId(), "cmd"));

        run(dispatcher, steve, "cmd");
        host.shutdownAsync();
        assertEquals(1, leftAtRelease.size());
        assertTrue(!leftAtRelease.get(0).isZero(), "cooldown was already running when the guard was released");
    }

    @Test
    void theSweepNeverRemovesAReservation() throws Exception {
        final CommandSpec spec = CommandSpec.literal("cmd").cooldown(Duration.ofSeconds(1))
                .executesAsync(ctx -> awaitRelease()).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        run(dispatcher, steve, "cmd");
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        host.nanos.addAndGet(Duration.ofHours(1).toNanos());
        host.cooldowns.sweep();
        assertEquals(1, host.cooldowns.inFlightSize());
        assertEquals(0, run(dispatcher, steve, "cmd"));
    }

    @Test
    void manyThreadsManyPlayersOneRunEach() throws Exception {
        final int threads = 8;
        final int playersPerThread = 30;
        final List<String> started = Collections.synchronizedList(new ArrayList<>());
        final CommandSpec spec = CommandSpec.literal("cmd").executesAsync(ctx -> {
            started.add(ctx.sender().getName());
            awaitRelease();
        }).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);
        final List<Inbox> inboxes = Collections.synchronizedList(new ArrayList<>());
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
                    sources.add(CommandFixtures.source(CommandFixtures.player("T" + thread + "P" + p, Set.of(), mine)));
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
        int busy = 0;
        for (final Future<int[]> future : futures) {
            final int[] result = future.get(30, TimeUnit.SECONDS);
            accepted += result[0];
            busy += result[1];
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        final int players = threads * playersPerThread;
        assertEquals(players, accepted, "one run per player");
        assertEquals(players * 2, busy, "the other rounds were refused");
        assertEquals(players, host.cooldowns.inFlightSize());
        for (final Inbox mine : inboxes) {
            assertEquals(List.of(BUSY, BUSY), mine.copy());
        }

        finishRunning();
        assertEquals(players, started.size());
        assertEquals(players, new java.util.HashSet<>(started).size());
        assertEquals(0, host.cooldowns.inFlightSize(), "everything released");
    }
}
