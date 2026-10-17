package com.github.dewarepk.synthetixlib.config;

import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.config.codec.Problem;
import com.github.dewarepk.synthetixlib.config.mapping.Schema;
import com.github.dewarepk.synthetixlib.config.migration.Migration;
import com.github.dewarepk.synthetixlib.config.yaml.AtomicFiles;
import com.github.dewarepk.synthetixlib.config.yaml.FileConfig;
import com.github.dewarepk.synthetixlib.config.yaml.YamlTrees;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.bukkit.configuration.InvalidConfigurationException;
import org.jspecify.annotations.Nullable;

/**
 * Objects that players or admins create at runtime, one YAML file per object ({@code airdrops/<id>.yml}); obtained
 * from {@link Configs#collection}. The file name without {@code .yml} is the id, {@code [a-z0-9_-]{1,64}} (input is
 * lower-cased). Files whose name starts with {@code _} or {@code .} are ignored.
 *
 * <pre>{@code
 * ConfigCollection<Airdrop> airdrops = configs.collection("airdrops", Airdrop.class, id -> Airdrop.defaults(id))
 *         .template("_template.yml")
 *         .load();
 * airdrops.create("vip");
 * airdrops.update("vip", drop -> drop.withLocation(player.getLocation()));
 * airdrops.onChange(change -> ...);
 * }</pre>
 *
 * <p><b>Threading.</b> Everything is safe from any thread. The in-memory state is an immutable snapshot that every
 * mutation swaps atomically, so {@link #get}, {@link #all} and {@link #ids} are lock-free and a mutation is visible
 * to the next read at once. Mutations are serialised (an {@code update} function never runs concurrently with
 * another one, so no update is lost). The file side of a mutation is queued and written on
 * {@code lib.scheduler().async()} in submission order, atomically (temp file and move). {@link #reload()} first
 * writes everything that is queued, so it never reads a file that is behind the snapshot, and a reload does not
 * overwrite a mutation made while it ran. Listeners run on the global region thread after the snapshot changed.
 * {@code lib.close()} writes what is still queued (waits up to 5 seconds, then logs what was not written).
 *
 * <p>Known limits: {@code create} with a template reads the template file on the calling thread; a file that exists
 * but could not be loaded (broken YAML) blocks {@code create} of the same id instead of being overwritten.
 *
 * @param <T> the object record
 */
public final class ConfigCollection<T> {

    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,64}");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String EXTENSION = ".yml";
    private static final long CLOSE_WAIT_SECONDS = 5;

    @FunctionalInterface
    private interface Write {
        void run() throws IOException;
    }

    /** A queued file write; {@code ids} are the objects it touches. */
    private record Op(List<String> ids, String description, Write write) {
    }

    private record Scan(Map<String, Path> files, Map<String, String> invalid) {
    }

    private final ConfigHost host;
    private final Path dir;
    private final String name;
    private final Class<T> type;
    private final Function<String, T> defaults;
    private final Codecs codecs;
    private final int version;
    private final List<Migration> migrations;
    private final @Nullable String template;

    /** Guards the snapshot swap, {@link #touched}, {@link #pending} and {@link #closed}. Never held during IO. */
    private final Object lock = new Object();
    /** Held by whoever reads or writes files: the async drain, a reload, close. Taken before {@link #lock}. */
    private final ReentrantLock ioLock = new ReentrantLock();
    private final ConcurrentLinkedQueue<Op> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean drainScheduled = new AtomicBoolean();
    private final List<Consumer<Change<T>>> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> pending = new HashMap<>();
    private volatile Map<String, T> snapshot = Map.of();
    private @Nullable Set<String> touched;
    private boolean closed;

    private ConfigCollection(final ConfigHost host, final Path dir, final String name, final Class<T> type,
                             final Function<String, T> defaults, final Codecs codecs, final int version,
                             final List<Migration> migrations, final @Nullable String template) {
        this.host = host;
        this.dir = dir;
        this.name = name;
        this.type = type;
        this.defaults = defaults;
        this.codecs = codecs;
        this.version = version;
        this.migrations = List.copyOf(migrations);
        this.template = template;
        fileConfig("validate", file("validate"));   // rejects bad versions and migrations right away
    }

    /** First load, called by the builder: creates the folder, reads every file and logs what it found. */
    static <T> ConfigCollection<T> open(final ConfigHost host, final Path dir, final String name,
                                        final Class<T> type, final Function<String, T> defaults,
                                        final Codecs codecs, final int version, final List<Migration> migrations,
                                        final @Nullable String template) {
        final ConfigCollection<T> collection = new ConfigCollection<>(host, dir, name, type, defaults, codecs,
                version, migrations, template);
        collection.initialLoad();
        return collection;
    }

    private void initialLoad() {
        try {
            Files.createDirectories(dir);
        } catch (final IOException e) {
            host.logger().warning("[" + name + "] cannot create " + dir + ": " + e);
        }
        final Scan scan;
        try {
            scan = scan();
        } catch (final IOException e) {
            host.logger().warning("[" + name + "] cannot list " + dir + ": " + e);
            return;
        }
        scan.invalid().forEach((file, message) -> host.logger().warning("[" + name + "/" + file + "] " + message));
        final Map<String, T> loaded = new TreeMap<>();
        for (final Map.Entry<String, Path> entry : scan.files().entrySet()) {
            final FileConfig.Result<T> result = loadFile(entry.getKey());
            result.errors().forEach(p -> host.logger().warning("[" + name + "/" + entry.getKey() + "] " + p));
            result.warnings().forEach(p -> host.logger().warning("[" + name + "/" + entry.getKey() + "] " + p));
            if (result.value() != null) {
                loaded.put(entry.getKey(), result.value());
            }
        }
        snapshot = Collections.unmodifiableMap(loaded);
    }

    // --- reading ------------------------------------------------------------------------------------------------

    /**
     * @param id the id; lower-cased, an invalid id is simply not found
     * @return the object, or empty
     */
    public Optional<T> get(final String id) {
        return Optional.ofNullable(snapshot.get(Objects.requireNonNull(id, "id").toLowerCase(Locale.ROOT)));
    }

    /** @return an immutable snapshot of all objects by id, in id order; later changes do not show up in it */
    public Map<String, T> all() {
        return snapshot;
    }

    /** @return the ids of an immutable snapshot, in order */
    public Set<String> ids() {
        return snapshot.keySet();
    }

    /**
     * Calls {@code listener} after the snapshot changed, on the global region thread, once per changed object in the
     * order the changes happened. A listener that throws is logged and does not stop the others.
     *
     * @param listener receives {@link Change.Added}, {@link Change.Updated} and {@link Change.Removed}
     */
    public void onChange(final Consumer<Change<T>> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    // --- mutations ----------------------------------------------------------------------------------------------

    /**
     * Creates a new object from the template file (if the collection has one and it exists) or from the defaults for
     * {@code id}; the file {@code <id>.yml} is written with comments.
     *
     * @param id the new id
     * @return the new object, already visible through {@link #get}
     * @throws IllegalArgumentException if {@code id} is not {@code [a-z0-9_-]{1,64}}
     * @throws IllegalStateException    if the id exists, its file exists but could not be loaded, a file with the
     *                                  same name in other letter case ({@code MyDrop.yml}) exists, or the service is
     *                                  closed
     */
    public T create(final String id) {
        return createInternal(id, null);
    }

    /**
     * Creates a new object holding {@code value}.
     *
     * @throws IllegalArgumentException if {@code id} is not {@code [a-z0-9_-]{1,64}}
     * @throws IllegalStateException    if the id exists, its file exists but could not be loaded, or the service is
     *                                  closed
     */
    public T create(final String id, final T value) {
        return createInternal(id, Objects.requireNonNull(value, "value"));
    }

    private T createInternal(final String rawId, final @Nullable T given) {
        final String id = validId(rawId);
        synchronized (lock) {
            ensureOpen();
            if (snapshot.containsKey(id)) {
                throw new IllegalStateException("\"" + id + "\" already exists in " + name);
            }
            final Path file = file(id);
            if (!pending.containsKey(id)) {
                final String variant = otherCase(id);
                if (variant != null) {
                    throw new IllegalStateException("a file named " + variant + " already exists in " + name
                            + " (ids are lower case); rename it to " + id + EXTENSION);
                }
                if (Files.exists(file)) {
                    throw new IllegalStateException(file.getFileName() + " exists in " + name
                            + " but could not be loaded; fix or delete it first");
                }
            }
            final T value;
            final String text;
            if (given != null) {
                value = given;
                text = fileConfig(id, file).render(given);
            } else {
                final T fresh = Objects.requireNonNull(defaults.apply(id), "defaults returned null for " + id);
                final FileConfig.Computed<T> fromTemplate = fromTemplate(id, fresh);
                if (fromTemplate != null) {
                    value = Objects.requireNonNull(fromTemplate.result().value());
                    text = Objects.requireNonNull(fromTemplate.text());
                } else {
                    value = fresh;
                    text = fileConfig(id, file).render(fresh);
                }
            }
            final Map<String, T> next = new TreeMap<>(snapshot);
            next.put(id, value);
            snapshot = Collections.unmodifiableMap(next);
            enqueue(List.of(id), "create " + file.getFileName(),
                    () -> AtomicFiles.write(file, text.getBytes(StandardCharsets.UTF_8)));
            dispatch(List.of(new Change.Added<>(id, value)));
            return value;
        }
    }

    /**
     * The name of a file in the folder that is {@code <id>.yml} except for the case of its letters ({@code MyDrop.yml}
     * for {@code mydrop}); {@code null} if there is none. Such a file is not an object, but on a case-insensitive file
     * system it is the file {@code create} would have to write.
     */
    private @Nullable String otherCase(final String id) {
        final String wanted = id + EXTENSION;
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> list = Files.list(dir)) {
            return list.map(path -> path.getFileName().toString())
                    .filter(fileName -> fileName.equalsIgnoreCase(wanted) && !fileName.equals(wanted))
                    .findFirst().orElse(null);
        } catch (final IOException e) {
            return null;
        }
    }

    /** The template read as a file of {@code id}: its value and the text to write; {@code null} if unusable. */
    private FileConfig.@Nullable Computed<T> fromTemplate(final String id, final T fresh) {
        if (template == null) {
            return null;
        }
        final Path source = dir.resolve(template);
        if (!Files.isRegularFile(source)) {
            return null;
        }
        final FileConfig.Computed<T> computed = new FileConfig<>(source, type, () -> fresh, codecs, version,
                migrations).compute();
        computed.result().errors().forEach(p -> host.logger().warning("[" + name + "/" + template + "] " + p));
        if (computed.result().value() == null) {
            host.logger().warning("[" + name + "] template " + template + " cannot be used, using the defaults");
            return null;
        }
        if (computed.text() != null) {
            return computed;
        }
        try {
            return new FileConfig.Computed<>(computed.result(), Files.readString(source, StandardCharsets.UTF_8));
        } catch (final IOException e) {
            host.logger().warning("[" + name + "] cannot read template " + template + ": " + e);
            return null;
        }
    }

    /**
     * Replaces the object {@code id} by what {@code change} returns. Only the keys that differ are written to the
     * file, so its comments and hand edits survive. {@code change} runs while other mutations wait; keep it short and
     * do not block in it. Returning an equal value changes nothing.
     *
     * @param id     an existing id
     * @param change gets the current value, returns the new one
     * @return the new value
     * @throws NoSuchElementException if there is no object with this id
     * @throws IllegalStateException  if the service is closed
     */
    public T update(final String id, final UnaryOperator<T> change) {
        final String key = validId(id);
        Objects.requireNonNull(change, "change");
        synchronized (lock) {
            ensureOpen();
            final T old = snapshot.get(key);
            if (old == null) {
                throw new NoSuchElementException("no \"" + key + "\" in " + name);
            }
            final T next = Objects.requireNonNull(change.apply(old), "update must not return null");
            if (next.equals(old)) {
                return old;
            }
            final Map<String, T> copy = new TreeMap<>(snapshot);
            copy.put(key, next);
            snapshot = Collections.unmodifiableMap(copy);
            final Path file = file(key);
            enqueue(List.of(key), "update " + file.getFileName(), () -> writeUpdate(key, file, old, next));
            dispatch(List.of(new Change.Updated<>(key, next, old)));
            return next;
        }
    }

    private void writeUpdate(final String id, final Path file, final T old, final T next) throws IOException {
        final String text;
        if (!Files.exists(file)) {
            text = fileConfig(id, file).render(next);
        } else {
            try {
                text = YamlTrees.applyChanges(Files.readString(file, StandardCharsets.UTF_8), tree(old), tree(next),
                        Schema.comments(type));
            } catch (final InvalidConfigurationException e) {
                host.logger().severe("[" + name + "] cannot update " + file.getFileName()
                        + ": it is not valid YAML any more, the change is only in memory");
                return;
            }
        }
        AtomicFiles.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> tree(final T value) {
        return (Map<String, Object>) codecs.encode(value, type);
    }

    /**
     * Gives the object a new id by moving its file.
     *
     * @throws IllegalArgumentException if an id is not valid
     * @throws NoSuchElementException   if {@code from} does not exist
     * @throws IllegalStateException    if {@code to} exists or the service is closed
     */
    public void rename(final String from, final String to) {
        final String source = validId(from);
        final String target = validId(to);
        synchronized (lock) {
            ensureOpen();
            final T value = snapshot.get(source);
            if (value == null) {
                throw new NoSuchElementException("no \"" + source + "\" in " + name);
            }
            if (source.equals(target)) {
                return;
            }
            final Path targetFile = file(target);
            if (snapshot.containsKey(target) || !pending.containsKey(target) && Files.exists(targetFile)) {
                throw new IllegalStateException("\"" + target + "\" already exists in " + name);
            }
            final Map<String, T> next = new TreeMap<>(snapshot);
            next.remove(source);
            next.put(target, value);
            snapshot = Collections.unmodifiableMap(next);
            final Path sourceFile = file(source);
            enqueue(List.of(source, target), "rename " + sourceFile.getFileName() + " to " + targetFile.getFileName(),
                    () -> move(source, sourceFile, target, targetFile, value));
            dispatch(List.of(new Change.Removed<>(source, value), new Change.Added<>(target, value)));
        }
    }

    private void move(final String sourceId, final Path source, final String targetId, final Path target,
                      final T value) throws IOException {
        if (Files.exists(source)) {
            moveNoReplace(source, target);
        } else {
            AtomicFiles.write(target, fileConfig(targetId, target).render(value).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Removes the object and moves its file to {@code .deleted/<id>-<timestamp>.yml} inside the collection folder;
     * nothing is destroyed.
     *
     * @return {@code false} if there was no such object
     * @throws IllegalStateException if the service is closed
     */
    public boolean delete(final String id) {
        final String key = validId(id);
        synchronized (lock) {
            ensureOpen();
            final T value = snapshot.get(key);
            if (value == null) {
                return false;
            }
            final Map<String, T> next = new TreeMap<>(snapshot);
            next.remove(key);
            snapshot = Collections.unmodifiableMap(next);
            final Path file = file(key);
            enqueue(List.of(key), "delete " + file.getFileName(), () -> moveToDeleted(key, file));
            dispatch(List.of(new Change.Removed<>(key, value)));
            return true;
        }
    }

    private void moveToDeleted(final String id, final Path file) throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        final Path trash = dir.resolve(".deleted");
        Files.createDirectories(trash);
        final String stamp = LocalDateTime.now().format(STAMP);
        Path target = trash.resolve(id + "-" + stamp + EXTENSION);
        for (int i = 2; Files.exists(target); i++) {
            target = trash.resolve(id + "-" + stamp + "-" + i + EXTENSION);
        }
        moveNoReplace(file, target);
    }

    private static void moveNoReplace(final Path source, final Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (final AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    // --- reload -------------------------------------------------------------------------------------------------

    /**
     * Reads all files again on the calling thread (after writing what is queued) and swaps the snapshot: new files
     * are {@link Change.Added}, edited ones {@link Change.Updated}, vanished ones {@link Change.Removed}. A file
     * that cannot be parsed is skipped with an error and keeps its current value; the others still load. A file with
     * missing keys gets them added, like a config file.
     *
     * @return one result per file
     * @throws IllegalStateException if the service is closed
     */
    public FilesReloadResult reload() {
        ensureOpen();
        ioLock.lock();
        try {
            return reloadLocked();
        } finally {
            ioLock.unlock();
        }
    }

    /**
     * Like {@link #reload()} with the file IO on {@code lib.scheduler().async()}; listeners and the returned future
     * run on the global region thread, the future after the listeners.
     *
     * @return a future of the result; fails with {@link IllegalStateException} if the service is or gets closed
     */
    public CompletableFuture<FilesReloadResult> reloadAsync() {
        final CompletableFuture<FilesReloadResult> future = new CompletableFuture<>();
        try {
            ensureOpen();
            host.runAsync(() -> {
                try {
                    ensureOpen();
                    final FilesReloadResult result;
                    ioLock.lock();
                    try {
                        result = reloadLocked();
                    } finally {
                        ioLock.unlock();
                    }
                    host.runGlobal(() -> future.complete(result));
                } catch (final RuntimeException e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (final RuntimeException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    /** Caller holds {@link #ioLock}. */
    private FilesReloadResult reloadLocked() {
        synchronized (lock) {
            touched = new HashSet<>();   // from now on mutations are remembered, the reload must not undo them
        }
        try {
            drainLocked();
            final Scan scan;
            try {
                scan = scan();
            } catch (final IOException e) {
                final String message = "cannot list " + dir + ": " + e;
                host.logger().warning("[" + name + "] " + message);
                return new FilesReloadResult(Map.of("", new ReloadResult(List.of(message), List.of(), false)),
                        List.of());
            }
            final Map<String, FileConfig.Result<T>> loaded = new LinkedHashMap<>();
            for (final String id : scan.files().keySet()) {
                loaded.put(id, loadFile(id));
            }
            return apply(scan, loaded);
        } finally {
            synchronized (lock) {
                touched = null;
            }
        }
    }

    private FilesReloadResult apply(final Scan scan, final Map<String, FileConfig.Result<T>> loaded) {
        synchronized (lock) {
            final Set<String> skip = Objects.requireNonNull(touched);
            final Map<String, T> current = snapshot;
            final Map<String, T> next = new TreeMap<>(current);
            final List<Change<T>> changes = new ArrayList<>();
            final Map<String, ReloadResult> results = new LinkedHashMap<>();
            for (final Map.Entry<String, FileConfig.Result<T>> entry : loaded.entrySet()) {
                final String id = entry.getKey();
                final FileConfig.Result<T> result = entry.getValue();
                final T value = result.value();
                boolean changed = false;
                if (value != null && !skip.contains(id)) {
                    final T old = current.get(id);
                    if (old == null) {
                        next.put(id, value);
                        changes.add(new Change.Added<>(id, value));
                        changed = true;
                    } else if (!old.equals(value)) {
                        next.put(id, value);
                        changes.add(new Change.Updated<>(id, value, old));
                        changed = true;
                    }
                }
                results.put(id, new ReloadResult(texts(result.errors()), texts(result.warnings()), changed));
            }
            scan.invalid().forEach((file, message) ->
                    results.put(file, new ReloadResult(List.of(message), List.of(), false)));
            final List<String> removed = new ArrayList<>();
            for (final Map.Entry<String, T> entry : current.entrySet()) {
                final String id = entry.getKey();
                if (!scan.files().containsKey(id) && !skip.contains(id)) {
                    next.remove(id);
                    removed.add(id);
                    changes.add(new Change.Removed<>(id, entry.getValue()));
                }
            }
            results.forEach((id, result) -> {
                result.errors().forEach(m -> host.logger().warning("[" + name + "/" + id + "] " + m));
            });
            if (!changes.isEmpty()) {
                snapshot = Collections.unmodifiableMap(next);
                dispatch(changes);
            }
            return new FilesReloadResult(results, removed);
        }
    }

    private static List<String> texts(final List<Problem> problems) {
        return problems.stream().map(Problem::toString).toList();
    }

    private FileConfig.Result<T> loadFile(final String id) {
        try {
            return fileConfig(id, file(id)).load();
        } catch (final RuntimeException e) {
            return new FileConfig.Result<>(null, List.of(new Problem("", "cannot load: " + e)), List.of());
        }
    }

    private Scan scan() throws IOException {
        final Map<String, Path> files = new TreeMap<>();
        final Map<String, String> invalid = new TreeMap<>();
        if (!Files.isDirectory(dir)) {
            return new Scan(files, invalid);
        }
        try (Stream<Path> list = Files.list(dir)) {
            for (final Path path : (Iterable<Path>) list::iterator) {
                final String fileName = path.getFileName().toString();
                if (!fileName.endsWith(EXTENSION) || fileName.startsWith("_") || fileName.startsWith(".")
                        || !Files.isRegularFile(path)) {
                    continue;
                }
                final String id = fileName.substring(0, fileName.length() - EXTENSION.length());
                if (ID.matcher(id).matches()) {
                    files.put(id, path);
                } else {
                    final String lower = id.toLowerCase(Locale.ROOT);
                    final String hint = ID.matcher(lower).matches() ? "; rename it to " + lower + EXTENSION : "";
                    invalid.put(fileName, "not a valid id (use a-z, 0-9, _ and -, at most 64 characters) -> ignored"
                            + hint);
                }
            }
        }
        return new Scan(files, invalid);
    }

    // --- file queue ---------------------------------------------------------------------------------------------

    /** Caller holds {@link #lock}. */
    private void enqueue(final List<String> ids, final String description, final Write write) {
        for (final String id : ids) {
            pending.merge(id, 1, Integer::sum);
        }
        if (touched != null) {
            touched.addAll(ids);
        }
        queue.add(new Op(ids, description, write));
        scheduleDrain();
    }

    private void scheduleDrain() {
        if (!drainScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            host.runAsync(() -> {
                drainScheduled.set(false);
                ioLock.lock();
                try {
                    drainLocked();
                } finally {
                    ioLock.unlock();
                }
            });
        } catch (final RuntimeException e) {
            drainScheduled.set(false);   // the scheduler is gone; close() writes what is queued
        }
    }

    /** Runs queued writes in submission order. Caller holds {@link #ioLock}. */
    private void drainLocked() {
        Op op;
        while ((op = queue.poll()) != null) {
            try {
                op.write().run();
            } catch (final IOException | RuntimeException e) {
                host.logger().log(Level.SEVERE, "[" + name + "] cannot " + op.description() + ": " + e, e);
            } finally {
                release(op);
            }
        }
    }

    private void release(final Op op) {
        synchronized (lock) {
            for (final String id : op.ids()) {
                pending.computeIfPresent(id, (key, count) -> count > 1 ? count - 1 : null);
            }
        }
    }

    // --- events -------------------------------------------------------------------------------------------------

    /** Caller holds {@link #lock}, so changes reach the global thread in the order they were made. */
    private void dispatch(final List<Change<T>> changes) {
        try {
            host.runGlobal(() -> changes.forEach(this::fire));
        } catch (final RuntimeException e) {
            // closed: nobody is listening any more
        }
    }

    private void fire(final Change<T> change) {
        for (final Consumer<Change<T>> listener : listeners) {
            try {
                listener.accept(change);
            } catch (final RuntimeException e) {
                host.logger().log(Level.SEVERE, "[" + name + "] change listener failed", e);
            }
        }
    }

    // --- lifecycle ----------------------------------------------------------------------------------------------

    /**
     * Writes what is queued, waiting up to 5 seconds for a write in progress; logs what could not be written.
     * Later mutations fail with {@link IllegalStateException}; reading still works. Idempotent.
     */
    void close() {
        synchronized (lock) {
            closed = true;
        }
        boolean done = false;
        try {
            if (ioLock.tryLock(CLOSE_WAIT_SECONDS, TimeUnit.SECONDS)) {
                try {
                    drainLocked();
                    done = true;
                } finally {
                    ioLock.unlock();
                }
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!done && !queue.isEmpty()) {
            final List<String> left = queue.stream().map(Op::description).toList();
            host.logger().severe("[" + name + "] not written before shutdown: " + String.join(", ", left));
        }
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    private void ensureOpen() {
        synchronized (lock) {
            if (closed || host.isClosed()) {
                throw new IllegalStateException("Configs is closed");
            }
        }
    }

    private Path file(final String id) {
        return dir.resolve(id + EXTENSION);
    }

    private FileConfig<T> fileConfig(final String id, final Path file) {
        return new FileConfig<>(file, type, () -> defaults.apply(id), codecs, version, migrations);
    }

    private static String validId(final String raw) {
        final String id = Objects.requireNonNull(raw, "id").toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("\"" + raw + "\" is not a valid id: use a-z, 0-9, _ and -, "
                    + "1 to 64 characters");
        }
        return id;
    }
}
