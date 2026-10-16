package com.github.dewarepk.synthetixlib.config;

import com.github.dewarepk.synthetixlib.config.codec.Problem;
import com.github.dewarepk.synthetixlib.config.yaml.FileConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * A loaded config file: an immutable snapshot that {@link #reload()} swaps atomically. Created by
 * {@link Configs#file}.
 *
 * <pre>{@code
 * ConfigHandle<AuctionConfig> config = configs.file("config.yml", AuctionConfig.class, AuctionConfig::defaults).load();
 * int minBid = config.get().minBid();                       // lock-free, safe from any thread
 * config.onReload(next -> rebuildZones(next.zones()));
 * ReloadResult result = config.reload();                    // e.g. from /auction reload
 * }</pre>
 *
 * <p>A file that is missing is created, one that lacks keys gets them added (with their comments), and one with
 * invalid values keeps them while the default is used instead; a reload that finds nothing to add or migrate does
 * not touch the file. Thread-safe: reloads run one at a time.
 *
 * @param <T> the config record
 */
public final class ConfigHandle<T> {

    private final ConfigHost host;
    private final FileConfig<T> file;
    private final String name;
    private final Object lock = new Object();
    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    private volatile T current;

    ConfigHandle(final ConfigHost host, final FileConfig<T> file, final String name, final T initial) {
        this.host = host;
        this.file = file;
        this.name = name;
        this.current = initial;
    }

    /** @return the current snapshot; never {@code null}, replaced as a whole by a reload that changed something */
    public T get() {
        return current;
    }

    /** @return the file this config is stored in */
    public Path path() {
        return file.file();
    }

    /**
     * Calls {@code listener} with the new snapshot after every reload that changed the config, on the thread that
     * finished the reload: the caller for {@link #reload()}, the global region thread for {@link #reloadAsync()}.
     * A listener that throws is logged and does not stop the others.
     */
    public void onReload(final Consumer<T> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Reads the file again on the calling thread and swaps the snapshot. Invalid values use their default and are
     * listed in the result; a file that cannot be read or parsed leaves the current snapshot in place.
     *
     * @throws IllegalStateException if the {@link Configs} service is closed
     */
    public ReloadResult reload() {
        final Applied<T> applied = apply();
        applied.notifyListeners(this);
        return applied.result();
    }

    /**
     * Like {@link #reload()}, with the file IO on {@code lib.scheduler().async()}. Listeners run on the global
     * region thread, and the returned future completes there after them.
     *
     * @return a future of the result; fails with {@link IllegalStateException} if the service is or gets closed
     */
    public CompletableFuture<ReloadResult> reloadAsync() {
        final CompletableFuture<ReloadResult> future = new CompletableFuture<>();
        try {
            host.runAsync(() -> {
                try {
                    final Applied<T> applied = apply();
                    host.runGlobal(() -> {
                        applied.notifyListeners(this);
                        future.complete(applied.result());
                    });
                } catch (final RuntimeException e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (final RuntimeException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    private record Applied<T>(ReloadResult result, @Nullable T changedTo) {

        /** Skips the call if a newer reload already replaced the snapshot; that reload notifies on its own. */
        void notifyListeners(final ConfigHandle<T> handle) {
            if (changedTo != null && changedTo == handle.current) {
                handle.fire(changedTo);
            }
        }
    }

    private Applied<T> apply() {
        if (host.isClosed()) {
            throw new IllegalStateException("Configs is closed");
        }
        synchronized (lock) {
            final FileConfig.Result<T> loaded = file.load();
            final ReloadResult result = report(loaded, false);
            final T next = loaded.value();
            if (next == null || next.equals(current)) {
                return new Applied<>(result, null);
            }
            current = next;
            return new Applied<>(new ReloadResult(result.errors(), result.warnings(), true), next);
        }
    }

    /** First load, called by the builder: logs what it found. */
    static <T> ConfigHandle<T> first(final ConfigHost host, final FileConfig<T> file, final String name,
                                     final T defaults) {
        final FileConfig.Result<T> loaded = file.load();
        final ConfigHandle<T> handle = new ConfigHandle<>(host, file, name,
                loaded.value() != null ? loaded.value() : defaults);
        handle.report(loaded, true);
        return handle;
    }

    private ReloadResult report(final FileConfig.Result<T> loaded, final boolean log) {
        final List<String> errors = loaded.errors().stream().map(Problem::toString).toList();
        final List<String> warnings = loaded.warnings().stream().map(Problem::toString).toList();
        if (log) {
            final var logger = host.logger();
            errors.forEach(message -> logger.warning("[" + name + "] " + message));
            warnings.forEach(message -> logger.warning("[" + name + "] " + message));
        }
        return new ReloadResult(errors, warnings, false);
    }

    private void fire(final T value) {
        for (final Consumer<T> listener : listeners) {
            try {
                listener.accept(value);
            } catch (final RuntimeException e) {
                host.logger().log(Level.SEVERE, "[" + name + "] reload listener failed", e);
            }
        }
    }
}
