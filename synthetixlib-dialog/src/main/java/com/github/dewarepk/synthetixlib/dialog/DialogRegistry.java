package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.config.FilesReloadResult;
import com.github.dewarepk.synthetixlib.config.ReloadResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/** The dialogs built by one {@link DialogService}, and the one place that reads their files. */
final class DialogRegistry {

    private final DialogLoader loader;
    private final Logger logger;
    private final ViewHost host;
    private final Map<String, DialogType<?>> dialogs = new ConcurrentHashMap<>();
    private final Object lock = new Object();
    private volatile boolean closed;

    DialogRegistry(final DialogLoader loader, final Logger logger, final ViewHost host) {
        this.loader = loader;
        this.logger = logger;
        this.host = host;
    }

    /** @return the dialog built with this id, {@code null} if none */
    @Nullable DialogType<?> find(final String id) {
        return dialogs.get(id);
    }

    boolean isClosed() {
        return closed;
    }

    void close() {
        closed = true;
    }

    /**
     * Builds the dialog of {@code id}: reads its file and logs what is wrong with it.
     *
     * @throws IllegalStateException if closed, the id was built already, or there is no file and no bundled one
     */
    <C> DialogType<C> register(final String id, final Registrations<C> registrations) {
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("DialogService is closed");
            }
            if (dialogs.containsKey(id)) {
                throw new IllegalStateException("A dialog with the id '" + id + "' was built already");
            }
            final DialogType<C> type = new DialogType<>(id, registrations, loader, host);
            final ReloadResult report = type.reload();
            report.errors().forEach(message -> logger.warning("[dialogs/" + id + ".yml] " + message));
            report.warnings().forEach(message -> logger.warning("[dialogs/" + id + ".yml] " + message));
            dialogs.put(id, type);
            return type;
        }
    }

    /** @throws IllegalStateException if closed */
    FilesReloadResult reload() {
        if (closed) {
            throw new IllegalStateException("DialogService is closed");
        }
        synchronized (lock) {
            final Map<String, ReloadResult> results = new LinkedHashMap<>();
            final List<String> ids = new ArrayList<>(dialogs.keySet());
            ids.sort(null);
            for (final String id : ids) {
                results.put(id, dialogs.get(id).reload());
            }
            return new FilesReloadResult(results, List.of());
        }
    }
}
