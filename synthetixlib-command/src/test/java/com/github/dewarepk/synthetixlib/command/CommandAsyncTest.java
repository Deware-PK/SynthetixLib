package com.github.dewarepk.synthetixlib.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.command.CommandFixtures.Inbox;
import com.github.dewarepk.synthetixlib.command.CommandFixtures.TestHost;
import com.github.dewarepk.synthetixlib.core.scheduler.AsyncScheduler;
import com.github.dewarepk.synthetixlib.core.scheduler.EntityTickScheduler;
import com.github.dewarepk.synthetixlib.core.scheduler.Scheduler;
import com.github.dewarepk.synthetixlib.core.scheduler.TickScheduler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** {@code executesAsync}, and where {@code runOnSender} goes. */
class CommandAsyncTest {

    private static final Duration FIVE = Duration.ofSeconds(5);

    private final TestHost host = new TestHost();
    private final Inbox inbox = new Inbox();
    private final Player steve = CommandFixtures.player("Steve", Set.of(), inbox);
    private final List<String> events = Collections.synchronizedList(new ArrayList<>());

    @AfterEach
    void stopAsync() throws InterruptedException {
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

    @Test
    void theHandlerRunsOffTheCommandThreadAndTheCallSaysSo() throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        final CommandSpec spec = CommandSpec.literal("cmd")
                .sub(SubSpec.literal("sync").executes(ctx -> events.add("sync isAsync=" + ctx.isAsync()
                        + " thread=" + Thread.currentThread().getName())))
                .sub(SubSpec.literal("slow").executesAsync(ctx -> {
                    events.add("async isAsync=" + ctx.isAsync() + " thread=" + Thread.currentThread().getName());
                    done.countDown();
                })).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);
        final String commandThread = Thread.currentThread().getName();

        assertEquals(1, run(dispatcher, steve, "cmd sync"));
        assertEquals(1, run(dispatcher, steve, "cmd slow"), "accepted; the handler is queued");
        assertTrue(done.await(10, TimeUnit.SECONDS));
        host.shutdownAsync();

        assertEquals("sync isAsync=false thread=" + commandThread, events.get(0));
        assertTrue(events.get(1).startsWith("async isAsync=true thread=fake-async-"), events.get(1));
    }

    @Test
    void theCooldownStartsOnlyWhenTheAsyncHandlerReturnsNormally() throws Exception {
        final CountDownLatch inside = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CommandSpec spec = CommandSpec.literal("cmd").cooldown(FIVE).executesAsync(ctx -> {
            inside.countDown();
            try {
                release.await();
            } catch (final InterruptedException e) {
                throw new IllegalStateException(e);
            }
            events.add("handler returns");
        }).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        assertEquals(1, run(dispatcher, steve, "cmd"));
        assertTrue(inside.await(10, TimeUnit.SECONDS));
        assertEquals(0, host.cooldowns.size(), "not started while the handler is still running");

        release.countDown();
        host.shutdownAsync();
        assertEquals(List.of("handler returns"), events);
        assertEquals(1, host.cooldowns.size(), "started after it returned");
        assertEquals(0, run(dispatcher, steve, "cmd"));
        assertEquals(List.of("Wait 5s before using /cmd again."), inbox.copy());
    }

    @Test
    void failAndExceptionsInAnAsyncHandlerBehaveLikeSyncOnes() throws Exception {
        final CommandSpec spec = CommandSpec.literal("cmd").cooldown(FIVE)
                .sub(SubSpec.literal("fail").executesAsync(ctx -> {
                    throw ctx.fail("<red>Nope, <sender>");
                }))
                .sub(SubSpec.literal("boom").argument(Arg.of("n", Args.integer(0, 9))).executesAsync(ctx -> {
                    throw new IllegalStateException("kaboom");
                })).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        assertEquals(1, run(dispatcher, steve, "cmd fail"));
        assertEquals(1, run(dispatcher, steve, "cmd boom 3"));
        host.shutdownAsync();

        assertEquals(List.of("Nope, Steve", "Something went wrong, see the console."), sorted(inbox.copy()));
        assertEquals(0, host.cooldowns.size(), "neither started the cooldown");
        assertEquals(1, host.logs.size());
        assertEquals(Level.SEVERE, host.logs.get(0).getLevel());
        assertTrue(host.logs.get(0).getMessage().contains("cmd boom 3"), host.logs.get(0).getMessage());
    }

    private static List<String> sorted(final List<String> messages) {
        return messages.stream().sorted().toList();
    }

    @Test
    void argumentsAreReadOnTheCommandThreadBeforeTheHandlerIsQueued() throws Exception {
        final List<String> lookups = Collections.synchronizedList(new ArrayList<>());
        final OfflinePlayer cached = (OfflinePlayer) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{OfflinePlayer.class}, (proxy, method, args) -> method.getName().equals("getName")
                        ? "Alex" : null);
        final Arg<OfflinePlayer> who = Arg.of("who", Args.offlinePlayerWith((source, name) -> {
            lookups.add(name + "@" + Thread.currentThread().getName());
            return name.equals("Alex") ? cached : null;
        }, source -> List.of()));
        final CommandSpec spec = CommandSpec.literal("cmd").argument(who).executesAsync(ctx -> {
            ctx.get(who);
            events.add("handled on " + Thread.currentThread().getName());
        }).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);
        final String commandThread = Thread.currentThread().getName();

        assertEquals(1, run(dispatcher, steve, "cmd Alex"));
        host.shutdownAsync();
        assertEquals(List.of("Alex@" + commandThread), lookups, "resolved before the handler was queued, once");
        assertEquals(1, events.size());
        assertTrue(events.get(0).startsWith("handled on fake-async-"));
    }

    @Test
    void anArgumentThatCannotBeReadStopsBeforeTheHandlerIsQueued() throws Exception {
        final Arg<OfflinePlayer> who = Arg.of("who", Args.offlinePlayerWith((source, name) -> null,
                source -> List.of()));
        final CommandSpec spec = CommandSpec.literal("cmd").cooldown(FIVE).argument(who)
                .executesAsync(ctx -> events.add("handler")).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        assertEquals(0, run(dispatcher, steve, "cmd Herobrine"));
        host.shutdownAsync();
        assertEquals(List.of(), events);
        assertEquals(List.of("No player found: Herobrine"), inbox.copy());
        assertEquals(0, host.cooldowns.size());
    }

    @Test
    void anActiveCooldownRefusesBeforeAnythingIsQueued() throws Exception {
        final CommandSpec spec = CommandSpec.literal("cmd").cooldown(FIVE)
                .executesAsync(ctx -> events.add("handler")).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);
        host.cooldowns.start(steve.getUniqueId(), "cmd", FIVE);

        assertEquals(0, run(dispatcher, steve, "cmd"));
        host.shutdownAsync();
        assertEquals(List.of(), events);
        assertEquals(List.of(), host.asyncThreads);
    }

    @Test
    void runOnSenderGoesBackToTheSenderFromTheAsyncHandler() throws Exception {
        final CommandSpec spec = CommandSpec.literal("cmd").allowConsole().executesAsync(ctx -> {
            events.add("async part");
            ctx.runOnSender(() -> {
                events.add("back on sender: " + ctx.sender().getName());
            });
        }).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);

        run(dispatcher, steve, "cmd");
        host.shutdownAsync();
        assertEquals(List.of("async part", "back on sender: Steve"), events);
        assertEquals(List.of("Steve"), host.senderRuns);
    }

    @Test
    void closedServiceDoesNotRunQueuedHandlers() throws Exception {
        final CommandSpec spec = CommandSpec.literal("cmd").executesAsync(ctx -> events.add("handler")).build();
        final var dispatcher = CommandFixtures.dispatcher(host, spec);
        host.closed = true;
        assertEquals(0, run(dispatcher, steve, "cmd"));
        host.shutdownAsync();
        assertEquals(List.of(), events);
    }

    // --- SenderThreads: where the scheduler is asked to run things -----------------------------------------------

    /** A scheduler that records which of its parts was asked to run something, and runs it right away. */
    private static final class RecordingScheduler {
        final List<String> calls = Collections.synchronizedList(new ArrayList<>());
        final AtomicBoolean entityRemoved = new AtomicBoolean();

        Scheduler scheduler() {
            final TickScheduler global = (TickScheduler) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{TickScheduler.class}, (proxy, method, args) -> {
                        calls.add("global." + method.getName());
                        ((Runnable) args[0]).run();
                        return null;
                    });
            final AsyncScheduler async = (AsyncScheduler) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{AsyncScheduler.class}, (proxy, method, args) -> {
                        calls.add("async." + method.getName());
                        ((Runnable) args[0]).run();
                        return null;
                    });
            return (Scheduler) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Scheduler.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "global" -> global;
                        case "async" -> async;
                        case "entity" -> {
                            final String who = ((Entity) args[0]).getName();
                            yield Proxy.newProxyInstance(getClass().getClassLoader(),
                                    new Class<?>[]{EntityTickScheduler.class}, (p, m, a) -> {
                                        calls.add("entity(" + who + ")." + m.getName() + "/" + a.length);
                                        if (!entityRemoved.get()) {
                                            ((Runnable) a[0]).run();
                                        }
                                        return null;
                                    });
                        }
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "RecordingScheduler";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    @Test
    void runOnSenderTargetsTheEntitySchedulerOfAPlayerElseTheGlobalOne() {
        final RecordingScheduler recording = new RecordingScheduler();
        final SenderThreads threads = new SenderThreads(recording.scheduler(), () -> false);
        final List<String> ran = new ArrayList<>();

        threads.runOnSender(steve, () -> ran.add("player"));
        threads.runOnSender(CommandFixtures.console(inbox), () -> ran.add("console"));
        threads.runAsync(() -> ran.add("async"));

        assertEquals(List.of("entity(Steve).run/1", "global.run", "async.run"), recording.calls);
        assertEquals(List.of("player", "console", "async"), ran);
    }

    @Test
    void aRetiredPlayerIsDroppedSilently() {
        final RecordingScheduler recording = new RecordingScheduler();
        recording.entityRemoved.set(true);
        final SenderThreads threads = new SenderThreads(recording.scheduler(), () -> false);
        final List<String> ran = new ArrayList<>();

        threads.runOnSender(steve, () -> ran.add("player"));
        assertEquals(List.of("entity(Steve).run/1"), recording.calls);
        assertEquals(List.of(), ran);
    }

    @Test
    void nothingIsScheduledOnceClosed() {
        final RecordingScheduler recording = new RecordingScheduler();
        final AtomicBoolean closed = new AtomicBoolean();
        final SenderThreads threads = new SenderThreads(recording.scheduler(), closed::get);

        closed.set(true);
        threads.runOnSender(steve, () -> events.add("player"));
        assertThrows(IllegalStateException.class, () -> threads.runAsync(() -> events.add("async")),
                "the caller must know the task will not run");
        assertEquals(List.of(), recording.calls);
        assertEquals(List.of(), events);
        assertTrue(closed.get());
    }

    @Test
    void aSchedulerThatClosedMeanwhile() {
        final Scheduler throwing = (Scheduler) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Scheduler.class}, (proxy, method, args) -> {
                    throw new IllegalStateException("closed");
                });
        final SenderThreads threads = new SenderThreads(throwing, () -> false);
        threads.runOnSender(steve, () -> events.add("player"));   // swallowed: a follow-up has nothing to give back
        assertThrows(IllegalStateException.class, () -> threads.runAsync(() -> events.add("async")));
        assertEquals(List.of(), events);
    }
}
