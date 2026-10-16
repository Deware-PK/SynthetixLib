package com.github.dewarepk.synthetixlib.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.config.yaml.FileConfig;
import com.github.dewarepk.synthetixlib.config.yaml.FileFixtures.AuctionConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigHandleTest {

    /** Runs "async" tasks on a worker and "global" tasks on another single thread, like the server would. */
    private static final class FakeHost implements ConfigHost {
        final ExecutorService async = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-async"));
        final ExecutorService global = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-global"));
        final Logger logger = Logger.getAnonymousLogger();
        volatile boolean closed;

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public Logger logger() {
            return logger;
        }

        @Override
        public void runAsync(final Runnable task) {
            if (closed) {
                throw new IllegalStateException("closed");
            }
            async.execute(task);
        }

        @Override
        public void runGlobal(final Runnable task) {
            if (closed) {
                throw new IllegalStateException("closed");
            }
            global.execute(task);
        }
    }

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();

    @AfterEach
    void shutDown() {
        host.async.shutdownNow();
        host.global.shutdownNow();
    }

    private ConfigHandle<AuctionConfig> handle() {
        final FileConfig<AuctionConfig> file = new FileConfig<>(dir.resolve("config.yml"), AuctionConfig.class,
                AuctionConfig::defaults, Codecs.create(), 1, List.of());
        return ConfigHandle.first(host, file, "config.yml", AuctionConfig.defaults());
    }

    private String read() throws IOException {
        return Files.readString(dir.resolve("config.yml"), StandardCharsets.UTF_8);
    }

    private void edit(final String from, final String to) throws IOException {
        Files.writeString(dir.resolve("config.yml"), read().replace(from, to), StandardCharsets.UTF_8);
    }

    @Test
    void theFirstLoadCreatesTheFileAndGetReturnsTheDefaults() throws IOException {
        final ConfigHandle<AuctionConfig> config = handle();

        assertEquals(AuctionConfig.defaults(), config.get());
        assertEquals(dir.resolve("config.yml"), config.path());
        assertTrue(read().contains("min-bid: 100"));
    }

    @Test
    void anUnchangedReloadReportsNoChangeAndCallsNoListener() {
        final ConfigHandle<AuctionConfig> config = handle();
        final List<AuctionConfig> seen = new ArrayList<>();
        config.onReload(seen::add);
        final AuctionConfig before = config.get();

        final ReloadResult result = config.reload();

        assertTrue(result.ok());
        assertFalse(result.changed());
        assertEquals(List.of(), result.warnings());
        assertSame(before, config.get());
        assertEquals(List.of(), seen);
    }

    @Test
    void aChangedFileSwapsTheSnapshotAndCallsListenersWithIt() throws IOException {
        final ConfigHandle<AuctionConfig> config = handle();
        final List<AuctionConfig> first = new ArrayList<>();
        final List<AuctionConfig> second = new ArrayList<>();
        config.onReload(first::add);
        config.onReload(second::add);
        edit("min-bid: 100", "min-bid: 250");

        final ReloadResult result = config.reload();

        assertTrue(result.changed());
        assertEquals(250, config.get().minBid());
        assertEquals(List.of(config.get()), first);
        assertEquals(List.of(config.get()), second);
    }

    @Test
    void invalidValuesAreListedAndTheDefaultIsUsed() throws IOException {
        final ConfigHandle<AuctionConfig> config = handle();
        edit("min-bid: 100", "min-bid: 0");
        edit("extend-seconds: 10", "extend-seconds: 12");

        final ReloadResult result = config.reload();

        assertEquals(List.of("min-bid: must be >= 1 (was 0) -> using 100"), result.errors());
        assertFalse(result.ok());
        assertTrue(result.changed());
        assertEquals(100, config.get().minBid());
        assertEquals(12, config.get().antiSnipe().extendSeconds());
    }

    @Test
    void aFileThatCannotBeParsedKeepsTheCurrentSnapshot() throws IOException {
        final ConfigHandle<AuctionConfig> config = handle();
        edit("min-bid: 100", "min-bid: 300");
        config.reload();
        final AuctionConfig loaded = config.get();
        Files.writeString(dir.resolve("config.yml"), "min-bid: [unclosed\n", StandardCharsets.UTF_8);

        final ReloadResult result = config.reload();

        assertEquals(1, result.errors().size());
        assertFalse(result.changed());
        assertSame(loaded, config.get());
    }

    @Test
    void aListenerThatThrowsDoesNotStopTheOthers() throws IOException {
        final ConfigHandle<AuctionConfig> config = handle();
        final List<AuctionConfig> seen = new ArrayList<>();
        config.onReload(next -> {
            throw new IllegalStateException("bad listener");
        });
        config.onReload(seen::add);
        edit("min-bid: 100", "min-bid: 5");

        config.reload();

        assertEquals(1, seen.size());
    }

    @Test
    void reloadAsyncDoesTheIoOffThreadAndRunsListenersOnTheGlobalThread() throws Exception {
        final ConfigHandle<AuctionConfig> config = handle();
        final List<String> threads = new ArrayList<>();
        config.onReload(next -> threads.add(Thread.currentThread().getName()));
        edit("min-bid: 100", "min-bid: 77");

        final CompletableFuture<ReloadResult> future = config.reloadAsync();
        final ReloadResult result = future.get(5, TimeUnit.SECONDS);

        assertTrue(result.changed());
        assertEquals(77, config.get().minBid());
        assertEquals(List.of("fake-global"), threads);
    }

    @Test
    void reloadAsyncOfAnUnchangedFileCompletesWithoutCallingListeners() throws Exception {
        final ConfigHandle<AuctionConfig> config = handle();
        final List<AuctionConfig> seen = new ArrayList<>();
        config.onReload(seen::add);

        final ReloadResult result = config.reloadAsync().get(5, TimeUnit.SECONDS);

        assertFalse(result.changed());
        assertEquals(List.of(), seen);
    }

    /** Queues tasks so a test decides in which order async and global work runs. */
    private static final class ManualHost implements ConfigHost {
        final ArrayDeque<Runnable> async = new ArrayDeque<>();
        final ArrayDeque<Runnable> global = new ArrayDeque<>();

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public Logger logger() {
            return Logger.getAnonymousLogger();
        }

        @Override
        public void runAsync(final Runnable task) {
            async.add(task);
        }

        @Override
        public void runGlobal(final Runnable task) {
            global.add(task);
        }
    }

    @Test
    void anOlderReloadDoesNotNotifyAfterANewerOneTookOver() throws Exception {
        final ManualHost manual = new ManualHost();
        final FileConfig<AuctionConfig> file = new FileConfig<>(dir.resolve("config.yml"), AuctionConfig.class,
                AuctionConfig::defaults, Codecs.create(), 1, List.of());
        final ConfigHandle<AuctionConfig> config = ConfigHandle.first(manual, file, "config.yml",
                AuctionConfig.defaults());
        final List<Integer> seen = new ArrayList<>();
        config.onReload(next -> seen.add(next.minBid()));

        edit("min-bid: 100", "min-bid: 200");
        final CompletableFuture<ReloadResult> first = config.reloadAsync();
        manual.async.remove().run();
        edit("min-bid: 200", "min-bid: 300");
        final CompletableFuture<ReloadResult> second = config.reloadAsync();
        manual.async.remove().run();
        assertEquals(300, config.get().minBid());

        manual.global.remove().run();   // the notification of the older reload comes first, and is stale
        manual.global.remove().run();

        assertEquals(List.of(300), seen, "only the newest snapshot is announced");
        assertTrue(first.get(1, TimeUnit.SECONDS).changed(), "the older future still completes with its result");
        assertTrue(second.get(1, TimeUnit.SECONDS).changed());
    }

    @Test
    void afterTheServiceIsClosedReloadsFail() throws Exception {
        final ConfigHandle<AuctionConfig> config = handle();
        host.closed = true;

        assertThrows(IllegalStateException.class, config::reload);
        final ExecutionException failure = assertThrows(ExecutionException.class,
                () -> config.reloadAsync().get(5, TimeUnit.SECONDS));
        assertTrue(failure.getCause() instanceof IllegalStateException, failure.toString());
    }
}
