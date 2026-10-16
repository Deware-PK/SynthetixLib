package com.github.dewarepk.synthetixlib.config;

import com.github.dewarepk.synthetixlib.config.codec.BukkitCodecs;
import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Entry point of the config module; one instance per {@link SynthetixLib}, closed with it.
 *
 * <pre>{@code
 * Configs configs = Configs.create(lib);
 * ConfigHandle<AuctionConfig> config = configs.file("config.yml", AuctionConfig.class, AuctionConfig::defaults).load();
 * }</pre>
 *
 * <p>Its {@link #codecs()} know the Bukkit types {@code Material}, {@code Sound}, {@code EntityType},
 * {@code Enchantment}, {@code PotionEffectType}, {@code Biome} (all by key), {@code Location} and
 * {@code ItemStack}, on top of the built-in ones.
 */
public final class Configs implements AutoCloseable {

    private final SynthetixLib lib;
    private final Codecs codecs = Codecs.create();
    private volatile boolean closed;

    private Configs(final SynthetixLib lib) {
        this.lib = lib;
        BukkitCodecs.register(codecs, lib.text(), lib.plugin().getServer());
    }

    /**
     * Creates the config service and attaches it to {@code lib}, so {@code lib.close()} closes it.
     *
     * @param lib the owning library instance
     * @return a new service bound to {@code lib}
     * @throws IllegalStateException if {@code lib} is already closed
     */
    public static Configs create(final SynthetixLib lib) {
        Objects.requireNonNull(lib, "lib");
        final Configs configs = new Configs(lib);
        lib.attach(configs);
        return configs;
    }

    /** @return the library instance this service belongs to */
    public SynthetixLib lib() {
        return lib;
    }

    /** @return the codec registry of this service: built-ins plus whatever you register */
    public Codecs codecs() {
        return codecs;
    }

    /**
     * Starts describing a config file in the plugin's data folder; nothing is read until
     * {@link ConfigFileBuilder#load()}. The file is created from {@code defaults} if it does not exist.
     *
     * <pre>{@code
     * ConfigHandle<AuctionConfig> config = configs.file("config.yml", AuctionConfig.class, AuctionConfig::defaults)
     *         .load();
     * }</pre>
     *
     * @param path     relative to the data folder, e.g. {@code config.yml} or {@code data/limits.yml}
     * @param type     a public record; see {@link Codecs} for what it may contain
     * @param defaults the default values, called on every load; the single source of truth for default keys,
     *                 values and (through {@code @Comment}) comments
     * @return a builder
     * @throws IllegalArgumentException if {@code path} leaves the data folder or {@code type} is not a record
     * @throws IllegalStateException    if this service is closed
     */
    public <T> ConfigFileBuilder<T> file(final String path, final Class<T> type, final Supplier<T> defaults) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(defaults, "defaults");
        if (closed) {
            throw new IllegalStateException("Configs is closed");
        }
        if (!type.isRecord()) {
            throw new IllegalArgumentException(type.getName() + " must be a record");
        }
        final Path folder = lib.plugin().getDataFolder().toPath().toAbsolutePath().normalize();
        final Path file = folder.resolve(path).normalize();
        if (path.isBlank() || !file.startsWith(folder) || file.equals(folder)) {
            throw new IllegalArgumentException("\"" + path + "\" is not a file inside the plugin's data folder");
        }
        return new ConfigFileBuilder<>(this, file, path, type, defaults);
    }

    ConfigHost host() {
        return new ConfigHost() {
            @Override
            public boolean isClosed() {
                return closed;
            }

            @Override
            public Logger logger() {
                return lib.plugin().getLogger();
            }

            @Override
            public void runAsync(final Runnable task) {
                lib.scheduler().async().run(task);
            }

            @Override
            public void runGlobal(final Runnable task) {
                lib.scheduler().global().run(task);
            }
        };
    }

    /** @return {@code true} after {@link #close()} */
    public boolean isClosed() {
        return closed;
    }

    /** Releases the service. Idempotent; called by {@code lib.close()}. */
    @Override
    public void close() {
        closed = true;
    }
}
