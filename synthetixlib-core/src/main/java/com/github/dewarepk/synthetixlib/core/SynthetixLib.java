package com.github.dewarepk.synthetixlib.core;

import com.github.dewarepk.synthetixlib.core.item.Items;
import com.github.dewarepk.synthetixlib.core.pdc.PdcKeys;
import com.github.dewarepk.synthetixlib.core.scheduler.Scheduler;
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
 */
public final class SynthetixLib implements AutoCloseable {

    private final JavaPlugin plugin;
    private volatile boolean closed;

    private final TextService text = TextService.create();
    private final Scheduler scheduler;
    private final PdcKeys keys;
    private final Items items;

    private SynthetixLib(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = Scheduler.create(plugin);
        this.keys = PdcKeys.create(plugin);
        this.items = Items.create(text);
    }

    /** @return the item builder factory */
    public Items items() {
        return items;
    }

    /** @return the factory for typed PDC keys namespaced to {@link #plugin()} */
    public PdcKeys keys() {
        return keys;
    }

    /** @return the MiniMessage text service */
    public TextService text() {
        return text;
    }

    /**
     * @return the Folia-safe scheduler; its tasks are cancelled by {@link #close()} and scheduling
     *         after close throws {@link IllegalStateException}
     */
    public Scheduler scheduler() {
        return scheduler;
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
        scheduler.close();
    }
}
