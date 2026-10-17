package com.github.dewarepk.synthetixlib.config;

import com.github.dewarepk.synthetixlib.config.codec.BukkitCodecs;
import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Entry point of the config module; one instance per {@link SynthetixLib}, closed with it.
 *
 * <pre>{@code
 * Configs configs = Configs.create(lib);
 * ConfigHandle<AuctionConfig> config = configs.file("config.yml", AuctionConfig.class, AuctionConfig::defaults).load();
 * }</pre>
 *
 * <p>Besides {@link #file} for plugin-wide configs there are {@link #collection} (one file per object created at
 * runtime) and {@link #locale} (messages per language).
 *
 * <p>Its {@link #codecs()} know the Bukkit types {@code Material}, {@code Sound}, {@code EntityType},
 * {@code Enchantment}, {@code PotionEffectType}, {@code Biome} (all by key), {@code Location} and
 * {@code ItemStack}, on top of the built-in ones.
 */
public final class Configs implements AutoCloseable {

    private final SynthetixLib lib;
    private final Codecs codecs = Codecs.create();
    private final List<ConfigCollection<?>> collections = new CopyOnWriteArrayList<>();
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
        return new ConfigFileBuilder<>(this, inside(path, "file"), path, type, defaults);
    }

    /**
     * Starts describing a set of language files ({@code <dir>/<tag>.yml}) that share one record of messages; nothing
     * is read until {@link LocaleBuilder#load()}.
     *
     * <pre>{@code
     * LocaleHandle<Messages> lang = configs.locale("lang", Messages.class, Messages::defaults)
     *         .bundled("th_TH")
     *         .perPlayer(true)
     *         .load();
     * }</pre>
     *
     * @param dir      folder relative to the data folder, e.g. {@code lang}; bundled files are looked up under the
     *                 same path in the plugin jar
     * @param type     a public record of MiniMessage strings ({@code String} or {@code List<String>} components)
     * @param defaults the messages of the default locale; the single source of truth for keys and comments
     * @return a builder
     * @throws IllegalArgumentException if {@code dir} leaves the data folder or {@code type} is not a record
     * @throws IllegalStateException    if this service is closed
     */
    public <T> LocaleBuilder<T> locale(final String dir, final Class<T> type, final Supplier<T> defaults) {
        Objects.requireNonNull(dir, "dir");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(defaults, "defaults");
        if (closed) {
            throw new IllegalStateException("Configs is closed");
        }
        if (!type.isRecord()) {
            throw new IllegalArgumentException(type.getName() + " must be a record");
        }
        return new LocaleBuilder<>(this, inside(dir, "folder"), dir, type, defaults);
    }

    /**
     * Starts describing a collection: one YAML file per object in a folder of the data folder. Nothing is read until
     * {@link ConfigCollectionBuilder#load()}.
     *
     * <pre>{@code
     * ConfigCollection<Airdrop> airdrops = configs.collection("airdrops", Airdrop.class, id -> Airdrop.defaults(id))
     *         .load();
     * }</pre>
     *
     * @param dir      folder relative to the data folder, e.g. {@code airdrops}
     * @param type     a public record
     * @param defaults the default object for an id, called for every new object and every load of its file
     * @return a builder
     * @throws IllegalArgumentException if {@code dir} leaves the data folder or {@code type} is not a record
     * @throws IllegalStateException    if this service is closed
     */
    public <T> ConfigCollectionBuilder<T> collection(final String dir, final Class<T> type,
                                                     final Function<String, T> defaults) {
        Objects.requireNonNull(dir, "dir");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(defaults, "defaults");
        if (closed) {
            throw new IllegalStateException("Configs is closed");
        }
        if (!type.isRecord()) {
            throw new IllegalArgumentException(type.getName() + " must be a record");
        }
        return new ConfigCollectionBuilder<>(this, inside(dir, "folder"), dir, type, defaults);
    }

    private Path inside(final String path, final String what) {
        final Path folder = lib.plugin().getDataFolder().toPath().toAbsolutePath().normalize();
        final Path resolved = folder.resolve(path).normalize();
        if (path.isBlank() || !resolved.startsWith(folder) || resolved.equals(folder)) {
            throw new IllegalArgumentException("\"" + path + "\" is not a " + what + " inside the plugin's data folder");
        }
        return resolved;
    }

    void track(final ConfigCollection<?> collection) {
        collections.add(collection);
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

            @Override
            public @Nullable InputStream resource(final String path) {
                return lib.plugin().getResource(path);
            }
        };
    }

    /** @return {@code true} after {@link #close()} */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Writes what collections still have queued (up to 5 seconds each, then logs what was lost) and releases the
     * service. Idempotent; called by {@code lib.close()}.
     */
    @Override
    public void close() {
        closed = true;
        collections.forEach(ConfigCollection::close);
    }
}
