package com.github.dewarepk.synthetixlib.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.config.annotation.Comment;
import com.github.dewarepk.synthetixlib.config.annotation.Range;
import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigCollectionTest {

    @Comment("A drop")
    public record Drop(@Comment("Display name") String name,
                       @Comment("How many times it ran") @Range(min = 0) int count,
                       List<String> tags) {

        static Drop defaults(final String id) {
            return new Drop("Drop " + id, 0, List.of("a"));
        }

        Drop withCount(final int count) {
            return new Drop(name, count, tags);
        }

        Drop withName(final String name) {
            return new Drop(name, count, tags);
        }
    }

    /** Real worker threads: "async" and "global" are one single thread each, like on a server. */
    private static final class ThreadedHost implements ConfigHost {
        final ExecutorService async = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-async"));
        final ExecutorService global = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-global"));
        volatile boolean closed;

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public Logger logger() {
            return Logger.getAnonymousLogger();
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

        /** Returns after everything queued so far, including events that queued work followed by events. */
        void settle() throws Exception {
            async.submit(() -> { }).get(30, TimeUnit.SECONDS);
            global.submit(() -> { }).get(30, TimeUnit.SECONDS);
        }
    }

    /** Queues tasks so a test decides when "async" and "global" work runs. */
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

        void runAll() {
            while (!async.isEmpty() || !global.isEmpty()) {
                while (!async.isEmpty()) {
                    async.remove().run();
                }
                while (!global.isEmpty()) {
                    global.remove().run();
                }
            }
        }
    }

    @TempDir
    Path root;

    private final ThreadedHost host = new ThreadedHost();

    @AfterEach
    void shutDown() {
        host.async.shutdownNow();
        host.global.shutdownNow();
    }

    private Path dir() {
        return root.resolve("drops");
    }

    private ConfigCollection<Drop> open(final ConfigHost with) {
        return open(with, null);
    }

    private ConfigCollection<Drop> open(final ConfigHost with, final String template) {
        return ConfigCollection.open(with, dir(), "drops", Drop.class, Drop::defaults, Codecs.create(), 1, List.of(),
                template);
    }

    private String read(final String fileName) throws IOException {
        return Files.readString(dir().resolve(fileName), StandardCharsets.UTF_8);
    }

    private void write(final String fileName, final String text) throws IOException {
        Files.createDirectories(dir());
        Files.writeString(dir().resolve(fileName), text, StandardCharsets.UTF_8);
    }

    /** Collects changes as {@code kind:id}; filled on the global thread. */
    private static List<String> record(final ConfigCollection<Drop> collection) {
        final List<String> seen = Collections.synchronizedList(new ArrayList<>());
        collection.onChange(change -> seen.add(switch (change) {
            case Change.Added<Drop> add -> "added:" + add.id();
            case Change.Updated<Drop> upd -> "updated:" + upd.id();
            case Change.Removed<Drop> rem -> "removed:" + rem.id();
        }));
        return seen;
    }

    // --- create ---------------------------------------------------------------------------------------------------

    @Test
    void createIsVisibleAtOnceAndTheFileIsWrittenLaterWithComments() throws Exception {
        final ManualHost manual = new ManualHost();
        final ConfigCollection<Drop> drops = open(manual);

        final Drop created = drops.create("vip");

        assertEquals(Drop.defaults("vip"), created);
        assertEquals(created, drops.get("vip").orElseThrow());
        assertFalse(Files.exists(dir().resolve("vip.yml")), "the write is queued, not done");
        manual.runAll();
        final String text = read("vip.yml");
        assertTrue(text.startsWith("# A drop\n"), text);
        assertTrue(text.contains("config-version: 1\n# Display name\nname: Drop vip\n"), text);
    }

    @Test
    void createWithAValueWritesThatValue() throws Exception {
        final ConfigCollection<Drop> drops = open(host);

        drops.create("big", new Drop("Big", 7, List.of("x", "y")));
        host.settle();

        final String text = read("big.yml");
        assertTrue(text.contains("name: Big") && text.contains("count: 7") && text.contains("- x"), text);
        assertEquals(new Drop("Big", 7, List.of("x", "y")), open(host).get("big").orElseThrow());
    }

    @Test
    void idsAreValidatedAndLowerCased() {
        final ConfigCollection<Drop> drops = open(host);

        assertEquals("Drop my-drop_1", drops.create("My-Drop_1").name());
        assertTrue(drops.get("MY-DROP_1").isPresent());
        assertEquals(java.util.Set.of("my-drop_1"), drops.ids());
        for (final String bad : List.of("", "a b", "a.b", "../x", "x/y", "ü", "a".repeat(65))) {
            assertThrows(IllegalArgumentException.class, () -> drops.create(bad), bad);
        }
        assertTrue(drops.get("a b").isEmpty(), "looking up an invalid id just finds nothing");
    }

    @Test
    void createFailsIfTheIdExists() {
        final ConfigCollection<Drop> drops = open(host);
        drops.create("one");

        assertThrows(IllegalStateException.class, () -> drops.create("one"));
        assertThrows(IllegalStateException.class, () -> drops.create("ONE", Drop.defaults("x")));
    }

    @Test
    void createDoesNotOverwriteABrokenFile() throws Exception {
        write("broken.yml", "name: [unclosed\n");
        final ConfigCollection<Drop> drops = open(host);

        assertTrue(drops.get("broken").isEmpty());
        assertThrows(IllegalStateException.class, () -> drops.create("broken"));
        assertEquals("name: [unclosed\n", read("broken.yml"));
    }

    @Test
    void aTemplateFileIsTheStartOfNewObjectsAndMissingKeysComeFromTheDefaults() throws Exception {
        write("_template.yml", "# my template\ncount: 5\n# hand written note\ntags:\n- t1\n");
        final ConfigCollection<Drop> drops = open(host, "_template.yml");

        final Drop created = drops.create("fromtemplate");
        host.settle();

        assertEquals(new Drop("Drop fromtemplate", 5, List.of("t1")), created);
        final String text = read("fromtemplate.yml");
        assertTrue(text.contains("# my template") && text.contains("# hand written note"), text);
        assertTrue(text.contains("count: 5") && text.contains("name: Drop fromtemplate"), text);
        assertEquals("# my template\ncount: 5\n# hand written note\ntags:\n- t1\n", read("_template.yml"),
                "the template itself is never changed");
        assertFalse(drops.ids().contains("_template"));
    }

    @Test
    void withoutATemplateFileTheDefaultsAreUsed() {
        final ConfigCollection<Drop> drops = open(host, "_template.yml");

        assertEquals(Drop.defaults("x"), drops.create("x"));
    }

    // --- update / delete / rename ---------------------------------------------------------------------------------

    @Test
    void updateWritesOnlyChangedKeysSoCommentsAndHandEditsSurvive() throws Exception {
        final ConfigCollection<Drop> drops = open(host);
        drops.create("one");
        host.settle();
        write("one.yml", read("one.yml").replace("# Display name", "# my own comment on the name")
                .replace("name: Drop one", "name: Hand edited name") + "extra-key: kept\n");

        final Drop updated = drops.update("one", drop -> drop.withCount(9));
        host.settle();

        assertEquals(9, updated.count());
        assertEquals(9, drops.get("one").orElseThrow().count());
        final String text = read("one.yml");
        assertTrue(text.contains("count: 9"), text);
        assertTrue(text.contains("# my own comment on the name\nname: Hand edited name"), text);
        assertTrue(text.contains("extra-key: kept"), text);
        assertTrue(text.contains("# How many times it ran\ncount: 9"), text);
    }

    @Test
    void anUpdateThatChangesNothingWritesAndAnnouncesNothing() throws Exception {
        final ConfigCollection<Drop> drops = open(host);
        drops.create("one");
        host.settle();
        final List<String> seen = record(drops);
        final byte[] before = Files.readAllBytes(dir().resolve("one.yml"));

        drops.update("one", drop -> drop.withCount(0));
        host.settle();

        assertEquals(List.of(), seen);
        assertEquals(new String(before, StandardCharsets.UTF_8), read("one.yml"));
    }

    @Test
    void updateOfAMissingIdFailsAndDoesNotCallTheFunction() {
        final ConfigCollection<Drop> drops = open(host);

        assertThrows(NoSuchElementException.class, () -> drops.update("nope", drop -> {
            throw new AssertionError("must not be called");
        }));
    }

    @Test
    void updateRewritesAFileThatWasRemovedByHand() throws Exception {
        final ConfigCollection<Drop> drops = open(host);
        drops.create("one");
        host.settle();
        Files.delete(dir().resolve("one.yml"));

        drops.update("one", drop -> drop.withCount(3));
        host.settle();

        assertTrue(read("one.yml").contains("count: 3"));
    }

    @Test
    void deleteMovesTheFileToTheDeletedFolder() throws Exception {
        final ConfigCollection<Drop> drops = open(host);
        drops.create("gone");
        host.settle();

        assertTrue(drops.delete("gone"));
        assertFalse(drops.delete("gone"));
        host.settle();

        assertTrue(drops.get("gone").isEmpty());
        assertFalse(Files.exists(dir().resolve("gone.yml")));
        try (Stream<Path> files = Files.list(dir().resolve(".deleted"))) {
            final List<String> names = files.map(p -> p.getFileName().toString()).toList();
            assertEquals(1, names.size());
            assertTrue(names.get(0).matches("gone-\\d{8}-\\d{6}\\.yml"), names.toString());
        }
        assertEquals(java.util.Set.of(), open(host).ids(), "a deleted object does not come back");
    }

    @Test
    void deletingAnIdTwiceKeepsBothFiles() throws Exception {
        final ConfigCollection<Drop> drops = open(host);
        for (int i = 0; i < 2; i++) {
            drops.create("again");
            drops.delete("again");
        }
        host.settle();

        try (Stream<Path> files = Files.list(dir().resolve(".deleted"))) {
            assertEquals(2, files.count(), "the second file gets a numbered name instead of replacing the first");
        }
    }

    @Test
    void renameMovesTheFileAndAnnouncesRemovedThenAdded() throws Exception {
        final ConfigCollection<Drop> drops = open(host);
        drops.create("old");
        host.settle();
        final List<String> seen = record(drops);

        drops.rename("old", "new-name");
        host.settle();

        assertTrue(drops.get("old").isEmpty());
        assertTrue(drops.get("new-name").isPresent());
        assertFalse(Files.exists(dir().resolve("old.yml")));
        assertTrue(Files.exists(dir().resolve("new-name.yml")));
        assertEquals(List.of("removed:old", "added:new-name"), seen);
        assertThrows(NoSuchElementException.class, () -> drops.rename("old", "x"));
        drops.create("taken");
        assertThrows(IllegalStateException.class, () -> drops.rename("new-name", "taken"));
    }

    @Test
    void anIdCanBeCreatedAgainRightAfterItsDelete() throws Exception {
        final ManualHost manual = new ManualHost();
        final ConfigCollection<Drop> drops = open(manual);
        drops.create("x");
        manual.runAll();

        drops.delete("x");                       // the file is still on disk: the move is queued
        drops.create("x", new Drop("Second", 2, List.of()));
        manual.runAll();

        assertTrue(read("x.yml").contains("name: Second"));
    }

    // --- loading and reload ---------------------------------------------------------------------------------------

    @Test
    void filesWithAnUnderscoreOrDotOrOtherExtensionAreIgnored() throws Exception {
        write("_template.yml", "count: 1\n");
        write(".hidden.yml", "count: 1\n");
        write("notes.txt", "hello");
        write("fine.yml", "name: Fine\ncount: 2\ntags: []\n");

        final ConfigCollection<Drop> drops = open(host);

        assertEquals(java.util.Set.of("fine"), drops.ids());
        assertEquals(new Drop("Fine", 2, List.of()), drops.get("fine").orElseThrow());
    }

    @Test
    void aBrokenFileIsSkippedWithAnErrorAndTheOthersLoad() throws Exception {
        write("good.yml", "name: Good\ncount: 1\ntags: []\n");
        write("bad.yml", "name: [unclosed\n");
        write("Bad Name.yml", "name: x\n");
        final ConfigCollection<Drop> drops = open(host);

        final FilesReloadResult result = drops.reload();

        assertEquals(java.util.Set.of("good"), drops.ids());
        assertFalse(result.ok());
        assertEquals(1, result.files().get("bad").errors().size());
        assertTrue(result.files().get("Bad Name.yml").errors().get(0).contains("not a valid id"));
        assertTrue(result.files().get("good").ok());
        assertTrue(result.errors().stream().anyMatch(e -> e.startsWith("bad: ")), result.errors().toString());
    }

    @Test
    void aFileWithUpperCaseLettersIsReportedWithTheNameToRenameItTo() throws Exception {
        write("MyDrop.yml", "name: x\n");
        write("Bad Name.yml", "name: x\n");
        final ConfigCollection<Drop> drops = open(host);

        final FilesReloadResult result = drops.reload();

        assertTrue(drops.ids().isEmpty());
        assertTrue(result.files().get("MyDrop.yml").errors().get(0).endsWith("; rename it to mydrop.yml"),
                result.files().get("MyDrop.yml").errors().toString());
        assertFalse(result.files().get("Bad Name.yml").errors().get(0).contains("rename it"),
                "no suggestion when the lower-case name is not valid either");
    }

    @Test
    void createExplainsAFileThatDiffersOnlyInLetterCase() throws Exception {
        write("MyDrop.yml", "name: x\n");
        final ConfigCollection<Drop> drops = open(host);

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> drops.create("MyDrop"));

        assertTrue(e.getMessage().contains("a file named MyDrop.yml already exists"), e.getMessage());
        assertTrue(e.getMessage().contains("ids are lower case"), e.getMessage());
        assertTrue(e.getMessage().contains("rename it to mydrop.yml"), e.getMessage());
        assertEquals("name: x\n", read("MyDrop.yml"), "the file is untouched");
        assertTrue(drops.ids().isEmpty());
    }

    @Test
    void createStillSaysBrokenForAFileOfTheSameName() throws Exception {
        write("broken.yml", "name: [unclosed\n");
        final ConfigCollection<Drop> drops = open(host);

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> drops.create("broken"));

        assertTrue(e.getMessage().contains("could not be loaded"), e.getMessage());
    }

    @Test
    void anInvalidValueUsesTheDefaultForThatFieldAndIsReported() throws Exception {
        write("one.yml", "name: One\ncount: -4\ntags: []\n");
        final ConfigCollection<Drop> drops = open(host);

        assertEquals(0, drops.get("one").orElseThrow().count());
        write("one.yml", "name: One\ncount: -9\ntags: []\n");
        final FilesReloadResult result = drops.reload();

        assertEquals(List.of("one: count: must be >= 0 (was -9) -> using 0"), result.errors());
        assertEquals("name: One\ncount: -9\ntags: []\n", read("one.yml").replace("config-version: 1\n", ""),
                "the user's text stays");
    }

    @Test
    void reloadAfterAHandEditAnnouncesUpdatedAndNewAndRemovedFiles() throws Exception {
        final ConfigCollection<Drop> drops = open(host);
        drops.create("edited");
        drops.create("removed");
        host.settle();
        final List<String> seen = record(drops);
        write("edited.yml", read("edited.yml").replace("count: 0", "count: 42"));
        write("fresh.yml", "name: Fresh\ncount: 1\ntags: []\n");
        Files.delete(dir().resolve("removed.yml"));

        final FilesReloadResult result = drops.reload();
        host.settle();

        assertTrue(result.changed());
        assertEquals(List.of("removed"), result.removed());
        assertEquals(42, drops.get("edited").orElseThrow().count());
        assertTrue(drops.get("fresh").isPresent());
        assertTrue(drops.get("removed").isEmpty());
        assertEquals(java.util.Set.of("updated:edited", "added:fresh", "removed:removed"),
                java.util.Set.copyOf(seen));
        assertEquals(3, seen.size());
    }

    @Test
    void anUnchangedReloadChangesNothingAndLeavesTheFilesAlone() throws Exception {
        final ConfigCollection<Drop> drops = open(host);
        drops.create("one");
        host.settle();
        final List<String> seen = record(drops);
        final String before = read("one.yml");
        final Map<String, Drop> snapshot = drops.all();

        final FilesReloadResult result = drops.reload();
        host.settle();

        assertFalse(result.changed());
        assertTrue(result.ok());
        assertEquals(before, read("one.yml"));
        assertEquals(List.of(), seen);
        assertEquals(snapshot, drops.all());
    }

    @Test
    void reloadAddsMissingKeysToAFileLikeAConfig() throws Exception {
        write("one.yml", "name: One\n");
        final ConfigCollection<Drop> drops = open(host);

        assertTrue(read("one.yml").contains("count: 0"), read("one.yml"));
        assertEquals(new Drop("One", 0, List.of("a")), drops.get("one").orElseThrow());
    }

    @Test
    void reloadFirstWritesWhatIsQueuedSoItNeverRevertsAMutation() throws Exception {
        final ManualHost manual = new ManualHost();
        final ConfigCollection<Drop> drops = open(manual);
        drops.create("one");
        manual.runAll();
        final List<String> seen = new ArrayList<>();
        drops.onChange(change -> seen.add(change.getClass().getSimpleName() + ":" + change.id()));

        drops.update("one", drop -> drop.withCount(5));     // queued, the file still says 0
        final FilesReloadResult result = drops.reload();
        manual.runAll();

        assertEquals(5, drops.get("one").orElseThrow().count());
        assertFalse(result.changed(), "the disk caught up with the snapshot first: " + result);
        assertEquals(List.of("Updated:one"), seen, "only the update itself was announced");
        assertTrue(read("one.yml").contains("count: 5"));
    }

    @Test
    void reloadAsyncReadsOffThreadAndAnnouncesOnTheGlobalThread() throws Exception {
        final ConfigCollection<Drop> drops = open(host);
        drops.create("one");
        host.settle();
        final List<String> threads = Collections.synchronizedList(new ArrayList<>());
        drops.onChange(change -> threads.add(Thread.currentThread().getName()));
        write("one.yml", read("one.yml").replace("count: 0", "count: 8"));

        final FilesReloadResult result = drops.reloadAsync().get(5, TimeUnit.SECONDS);

        assertTrue(result.changed());
        assertEquals(List.of("fake-global"), threads);
        assertEquals(8, drops.get("one").orElseThrow().count());
    }

    // --- events ---------------------------------------------------------------------------------------------------

    @Test
    void changesAreAnnouncedOnTheGlobalThreadInTheOrderTheyHappened() throws Exception {
        final ConfigCollection<Drop> drops = open(host);
        final List<String> seen = record(drops);
        final List<String> threads = Collections.synchronizedList(new ArrayList<>());
        drops.onChange(change -> threads.add(Thread.currentThread().getName()));

        drops.create("a");
        drops.update("a", drop -> drop.withCount(1));
        drops.create("b");
        drops.delete("a");
        host.settle();

        assertEquals(List.of("added:a", "updated:a", "added:b", "removed:a"), seen);
        assertEquals(List.of("fake-global", "fake-global", "fake-global", "fake-global"), threads);
    }

    @Test
    void aListenerThatThrowsDoesNotStopTheOthers() throws Exception {
        final ConfigCollection<Drop> drops = open(host);
        drops.onChange(change -> {
            throw new IllegalStateException("bad listener");
        });
        final List<String> seen = record(drops);

        drops.create("a");
        host.settle();

        assertEquals(List.of("added:a"), seen);
    }

    // --- concurrency ----------------------------------------------------------------------------------------------

    @Test
    void concurrentUpdatesFromManyThreadsLoseNothing() throws Exception {
        final ConfigCollection<Drop> drops = open(host);
        drops.create("counter");
        final int threads = 8;
        final int perThread = 60;
        final CountDownLatch start = new CountDownLatch(1);
        final List<CompletableFuture<Void>> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            workers.add(CompletableFuture.runAsync(() -> {
                try {
                    start.await();
                } catch (final InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                for (int i = 0; i < perThread; i++) {
                    drops.update("counter", drop -> drop.withCount(drop.count() + 1));
                }
            }));
        }
        start.countDown();
        CompletableFuture.allOf(workers.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
        host.settle();

        assertEquals(threads * perThread, drops.get("counter").orElseThrow().count());
        assertTrue(read("counter.yml").contains("count: " + threads * perThread), read("counter.yml"));
        assertEquals(threads * perThread, open(host).get("counter").orElseThrow().count(),
                "a fresh load of the files sees the last update too");
        try (Stream<Path> files = Files.list(dir())) {
            assertEquals(List.of("counter.yml"), files.map(p -> p.getFileName().toString()).toList(),
                    "no temp file is left behind");
        }
    }

    @Test
    void concurrentCreatesOfTheSameIdSucceedExactlyOnce() throws Exception {
        final ConfigCollection<Drop> drops = open(host);
        final int threads = 8;
        final CountDownLatch start = new CountDownLatch(1);
        final List<CompletableFuture<Boolean>> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            workers.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await();
                    drops.create("same");
                    return true;
                } catch (final IllegalStateException e) {
                    return false;
                } catch (final InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }));
        }
        start.countDown();
        int created = 0;
        for (final CompletableFuture<Boolean> worker : workers) {
            created += worker.get(10, TimeUnit.SECONDS) ? 1 : 0;
        }

        assertEquals(1, created);
    }

    @Test
    void mutationsRacingWithReloadsEndConsistent() throws Exception {
        final ConfigCollection<Drop> drops = open(host);
        drops.create("counter");
        host.settle();
        final CompletableFuture<Void> reloader = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < 40; i++) {
                drops.reload();
            }
        });
        for (int i = 1; i <= 200; i++) {
            final int value = i;
            drops.update("counter", drop -> drop.withCount(value));
        }
        reloader.get(30, TimeUnit.SECONDS);
        host.settle();

        assertEquals(200, drops.get("counter").orElseThrow().count());
        assertEquals(200, open(host).get("counter").orElseThrow().count());
    }

    // --- close ----------------------------------------------------------------------------------------------------

    @Test
    void closeWritesWhatIsStillQueuedThenMutationsFail() throws Exception {
        final ManualHost manual = new ManualHost();
        final ConfigCollection<Drop> drops = open(manual);
        drops.create("one");
        drops.update("one", drop -> drop.withCount(11));
        drops.create("two");
        assertFalse(Files.exists(dir().resolve("one.yml")));

        drops.close();

        assertTrue(read("one.yml").contains("count: 11"));
        assertTrue(Files.exists(dir().resolve("two.yml")));
        assertThrows(IllegalStateException.class, () -> drops.create("three"));
        assertThrows(IllegalStateException.class, () -> drops.update("one", drop -> drop.withCount(1)));
        assertThrows(IllegalStateException.class, () -> drops.delete("one"));
        assertThrows(IllegalStateException.class, drops::reload);
        assertNotNull(drops.get("one").orElse(null), "reading still works");
        drops.close();   // idempotent
    }

    @Test
    void afterTheHostIsClosedReloadAsyncFails() {
        final ConfigCollection<Drop> drops = open(host);
        host.closed = true;

        final CompletableFuture<FilesReloadResult> future = drops.reloadAsync();

        assertTrue(future.isCompletedExceptionally());
    }
}
