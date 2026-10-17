package com.github.dewarepk.synthetixlib.config;

import com.github.dewarepk.synthetixlib.config.migration.ConfigTree;
import com.github.dewarepk.synthetixlib.config.migration.Migration;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Describes a {@link ConfigCollection} before it is loaded; obtained from {@link Configs#collection}. Not
 * thread-safe. Version and migrations apply to every file of the collection, like in
 * {@link ConfigFileBuilder}.
 *
 * <pre>{@code
 * ConfigCollection<Airdrop> airdrops = configs.collection("airdrops", Airdrop.class, id -> Airdrop.defaults(id))
 *         .version(2)
 *         .migrate(1, 2, tree -> tree.rename("loc", "location"))
 *         .template("_template.yml")
 *         .load();
 * }</pre>
 *
 * @param <T> the object record
 */
public final class ConfigCollectionBuilder<T> {

    private final Configs configs;
    private final Path dir;
    private final String name;
    private final Class<T> type;
    private final Function<String, T> defaults;
    private final List<Migration> migrations = new ArrayList<>();
    private int version = 1;
    private @Nullable String template;

    ConfigCollectionBuilder(final Configs configs, final Path dir, final String name, final Class<T> type,
                            final Function<String, T> defaults) {
        this.configs = configs;
        this.dir = dir;
        this.name = name;
        this.type = type;
        this.defaults = defaults;
    }

    /**
     * The version of the file format, written as {@code config-version} in every file. Default {@code 1}.
     *
     * @param version at least 1
     * @return this
     */
    public ConfigCollectionBuilder<T> version(final int version) {
        if (version < 1) {
            throw new IllegalArgumentException("version must be at least 1, got " + version);
        }
        this.version = version;
        return this;
    }

    /**
     * Adds a step that turns the data of a file of version {@code from} into version {@code to}; see
     * {@link ConfigFileBuilder#migrate}. Each file is backed up as {@code <id>.yml.v<from>.bak} before it is migrated.
     *
     * @return this
     */
    public ConfigCollectionBuilder<T> migrate(final int from, final int to, final Consumer<ConfigTree> action) {
        migrations.add(new Migration(from, to, action));
        return this;
    }

    /**
     * A file in the collection folder that new objects start from. If it exists, {@code create(id)} copies it (its
     * comments included) and fills the keys it lacks from the defaults for the id; otherwise {@code create} uses the
     * defaults alone. The name should start with {@code _} so the collection does not load it as an object.
     *
     * @param fileName e.g. {@code _template.yml}; a name, not a path
     * @return this
     * @throws IllegalArgumentException if it is blank or contains a path separator
     */
    public ConfigCollectionBuilder<T> template(final String fileName) {
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("template must be a file name inside the folder, got \"" + fileName
                    + "\"");
        }
        this.template = fileName;
        return this;
    }

    /**
     * Creates the folder if needed and reads every {@code <id>.yml} in it. Synchronous; call it in {@code onEnable}.
     * Problems with the content of a file are logged and never thrown: a broken file is skipped, the others load.
     *
     * @return the loaded collection
     * @throws IllegalArgumentException if the record, the version or the migrations are not valid
     * @throws IllegalStateException    if the {@link Configs} service is closed
     */
    public ConfigCollection<T> load() {
        if (configs.isClosed()) {
            throw new IllegalStateException("Configs is closed");
        }
        final ConfigCollection<T> collection = ConfigCollection.open(configs.host(), dir, name, type, defaults,
                configs.codecs(), version, migrations, template);
        configs.track(collection);
        return collection;
    }
}
