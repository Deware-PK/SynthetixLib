package com.github.dewarepk.synthetixlib.config;

import com.github.dewarepk.synthetixlib.config.migration.ConfigTree;
import com.github.dewarepk.synthetixlib.config.migration.Migration;
import com.github.dewarepk.synthetixlib.config.yaml.FileConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Describes a config file before it is loaded; obtained from {@link Configs#file}. Not thread-safe.
 *
 * <pre>{@code
 * ConfigHandle<AuctionConfig> config = configs.file("config.yml", AuctionConfig.class, AuctionConfig::defaults)
 *         .version(2)
 *         .migrate(1, 2, tree -> tree.rename("min-price", "min-bid"))
 *         .load();
 * }</pre>
 *
 * @param <T> the config record
 */
public final class ConfigFileBuilder<T> {

    private final Configs configs;
    private final Path file;
    private final String name;
    private final Class<T> type;
    private final Supplier<T> defaults;
    private final List<Migration> migrations = new ArrayList<>();
    private int version = 1;

    ConfigFileBuilder(final Configs configs, final Path file, final String name, final Class<T> type,
                      final Supplier<T> defaults) {
        this.configs = configs;
        this.file = file;
        this.name = name;
        this.type = type;
        this.defaults = defaults;
    }

    /**
     * The version of the file format, written as {@code config-version}. Raise it whenever a release changes the
     * meaning of existing keys and add a {@link #migrate migration}. Default {@code 1}.
     *
     * @param version at least 1
     * @return this
     */
    public ConfigFileBuilder<T> version(final int version) {
        if (version < 1) {
            throw new IllegalArgumentException("version must be at least 1, got " + version);
        }
        this.version = version;
        return this;
    }

    /**
     * Adds a step that turns the data of a file of version {@code from} into version {@code to}. Steps chain: a
     * file of version 1 with steps 1 to 2 and 2 to 3 passes both. Before the first step the file is copied to
     * {@code <file>.v<from>.bak}.
     *
     * @param from   the version the step reads, at least 1
     * @param to     the version it produces, above {@code from} and at most the current {@link #version}
     * @param action edits the raw data
     * @return this
     */
    public ConfigFileBuilder<T> migrate(final int from, final int to, final Consumer<ConfigTree> action) {
        migrations.add(new Migration(from, to, action));
        return this;
    }

    /**
     * Reads the file, creating it from the defaults if it is missing, and returns the handle. Synchronous; call it
     * in {@code onEnable}. Problems with the content of the file are logged and reported by later reloads but
     * never thrown: every field that is missing or invalid falls back to its default.
     *
     * @return the loaded config
     * @throws IllegalArgumentException if the record, the version or the migrations are not valid (programming errors)
     * @throws IllegalStateException    if the {@link Configs} service is closed
     */
    public ConfigHandle<T> load() {
        if (configs.isClosed()) {
            throw new IllegalStateException("Configs is closed");
        }
        final FileConfig<T> fileConfig = new FileConfig<>(file, type, defaults, configs.codecs(), version, migrations);
        return ConfigHandle.first(configs.host(), fileConfig, name, Objects.requireNonNull(defaults.get(), "defaults"));
    }
}
