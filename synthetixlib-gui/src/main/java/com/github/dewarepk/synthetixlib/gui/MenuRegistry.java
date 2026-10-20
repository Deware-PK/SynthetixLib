package com.github.dewarepk.synthetixlib.gui;

import com.github.dewarepk.synthetixlib.config.FilesReloadResult;
import com.github.dewarepk.synthetixlib.config.ReloadResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/** The menus built by one {@link GuiService}, and the one place that reads their files. */
final class MenuRegistry {

    private final MenuLoader loader;
    private final Logger logger;
    private final ViewHost host;
    private final Map<String, MenuType<?>> menus = new ConcurrentHashMap<>();
    private final Object lock = new Object();
    private volatile boolean closed;

    MenuRegistry(final MenuLoader loader, final Logger logger, final ViewHost host) {
        this.loader = loader;
        this.logger = logger;
        this.host = host;
    }

    /** @return the menu built with this id, {@code null} if none */
    @Nullable MenuType<?> find(final String id) {
        return menus.get(id);
    }

    boolean isClosed() {
        return closed;
    }

    void close() {
        closed = true;
    }

    /**
     * Builds the menu of {@code id}: reads its file and logs what is wrong with it.
     *
     * @throws IllegalStateException if closed, the id was built already, or there is no file and no bundled one
     */
    <C> MenuType<C> register(final String id, final Registrations<C> registrations) {
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("GuiService is closed");
            }
            if (menus.containsKey(id)) {
                throw new IllegalStateException("A menu with the id '" + id + "' was built already");
            }
            final MenuType<C> type = new MenuType<>(id, registrations, loader, host, this);
            final ReloadResult report = type.reload();
            report.errors().forEach(message -> logger.warning("[menus/" + id + ".yml] " + message));
            report.warnings().forEach(message -> logger.warning("[menus/" + id + ".yml] " + message));
            menus.put(id, type);
            return type;
        }
    }

    /** @throws IllegalStateException if closed */
    FilesReloadResult reload() {
        if (closed) {
            throw new IllegalStateException("GuiService is closed");
        }
        synchronized (lock) {
            final Map<String, ReloadResult> results = new LinkedHashMap<>();
            final List<String> ids = new ArrayList<>(menus.keySet());
            ids.sort(null);
            for (final String id : ids) {
                results.put(id, menus.get(id).reload());
            }
            return new FilesReloadResult(results, List.of());
        }
    }
}
