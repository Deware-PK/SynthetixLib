package com.github.dewarepk.synthetixlib.core;

import com.github.dewarepk.synthetixlib.core.text.TextService;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Per-plugin entry point of SynthetixLib.
 *
 * <p>Create exactly one instance in {@code onEnable()} and keep it in your plugin; there is no
 * static/global instance because every consumer shades its own relocated copy of the library.
 *
 * <pre>{@code
 * public final class MyPlugin extends JavaPlugin {
 *     private SynthetixLib lib;
 *
 *     public void onEnable()  { lib = SynthetixLib.init(this); }
 *     public void onDisable() { lib.close(); }
 * }
 * }</pre>
 *
 * <p>PLACEHOLDER (Phase 0): the real API is defined in docs/design/core.md (Phase 1).
 */
public final class SynthetixLib implements AutoCloseable {

    private final JavaPlugin plugin;
    private volatile boolean closed;

    private final TextService text = TextService.create();

    private SynthetixLib(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** @return the MiniMessage text service */
    public TextService text() {
        return text;
    }

    /**
     * Initialises SynthetixLib for the given plugin. Call from {@code onEnable()}.
     *
     * @param plugin the owning plugin
     * @return a new library instance bound to {@code plugin}
     */
    public static SynthetixLib init(final JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return new SynthetixLib(plugin);
    }

    /** @return the plugin this instance belongs to */
    public JavaPlugin plugin() {
        return plugin;
    }

    /** @return {@code true} after {@link #close()} was called */
    public boolean isClosed() {
        return closed;
    }

    /** Releases everything this instance owns. Call from {@code onDisable()}. Idempotent. */
    @Override
    public void close() {
        closed = true;
    }
}
