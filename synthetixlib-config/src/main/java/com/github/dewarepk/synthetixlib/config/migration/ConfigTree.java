package com.github.dewarepk.synthetixlib.config.migration;

import com.github.dewarepk.synthetixlib.config.yaml.YamlTrees;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.jspecify.annotations.Nullable;

/**
 * Mutable view of the raw data of a config file, used inside migrations. Paths use dots
 * ({@code anti-snipe.enabled}), so keys that contain a dot cannot be addressed. Values are plain data: sections are
 * {@code Map}s, lists are {@code List}s. Comments of keys that are renamed or moved travel with them.
 *
 * <pre>{@code
 * configs.file("config.yml", AuctionConfig.class, AuctionConfig::defaults)
 *         .version(3)
 *         .migrate(1, 2, tree -> tree.rename("min-price", "min-bid"))
 *         .migrate(2, 3, tree -> {
 *             tree.move("snipe-enabled", "anti-snipe.enabled");
 *             tree.transform("bid-time", old -> old + "s");     // 60 -> "60s"
 *         })
 *         .load();
 * }</pre>
 */
public final class ConfigTree {

    private final ConfigurationSection root;

    private ConfigTree(final ConfigurationSection root) {
        this.root = root;
    }

    /**
     * Wraps the data of a file.
     *
     * @apiNote Internal: instances are handed to your migration
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static ConfigTree of(final ConfigurationSection root) {
        return new ConfigTree(Objects.requireNonNull(root, "root"));
    }

    /** @return {@code true} if something is stored at {@code path} */
    public boolean has(final String path) {
        return get(path) != null;
    }

    /**
     * @return the value at {@code path} (a section is returned as a copy, edit it with {@link #set}), or
     *         {@code null} if there is none
     */
    public @Nullable Object get(final String path) {
        final String[] keys = split(path);
        final ConfigurationSection parent = parent(keys, false);
        if (parent == null) {
            return null;
        }
        final Object value = parent.get(keys[keys.length - 1]);
        return value instanceof ConfigurationSection section ? YamlTrees.toPlain(section) : value;
    }

    /**
     * Stores {@code value} at {@code path}, creating sections on the way (a plain value in the way is replaced). A
     * {@code Map} becomes a section. {@code null} removes the key. An existing comment on the key is kept.
     *
     * @return this
     */
    public ConfigTree set(final String path, final @Nullable Object value) {
        if (value == null) {
            return remove(path);
        }
        final String[] keys = split(path);
        final ConfigurationSection parent = parent(keys, true);
        YamlTrees.putPlain(parent, keys[keys.length - 1], value);
        return this;
    }

    /**
     * Removes {@code path}; nothing happens if it does not exist.
     *
     * @return this
     */
    public ConfigTree remove(final String path) {
        final String[] keys = split(path);
        final ConfigurationSection parent = parent(keys, false);
        if (parent != null) {
            parent.set(keys[keys.length - 1], null);
        }
        return this;
    }

    /**
     * Renames the last segment of {@code path}: {@code rename("zones.a.r", "radius")} makes {@code zones.a.radius}.
     * Nothing happens if {@code path} does not exist; an existing {@code newName} is overwritten.
     *
     * @param newName a single key, without dots
     * @return this
     */
    public ConfigTree rename(final String path, final String newName) {
        Objects.requireNonNull(newName, "newName");
        if (newName.isEmpty() || newName.indexOf('.') >= 0) {
            throw new IllegalArgumentException("newName must be a single key without dots, got \"" + newName + "\"");
        }
        final int dot = path.lastIndexOf('.');
        return move(path, dot < 0 ? newName : path.substring(0, dot + 1) + newName);
    }

    /**
     * Moves the value at {@code from} to {@code to}, together with its comments. Nothing happens if {@code from}
     * does not exist; an existing value at {@code to} is overwritten.
     *
     * @return this
     */
    public ConfigTree move(final String from, final String to) {
        final String[] fromKeys = split(from);
        final String[] toKeys = split(to);
        final ConfigurationSection source = parent(fromKeys, false);
        final String sourceKey = fromKeys[fromKeys.length - 1];
        if (source == null || source.get(sourceKey) == null || from.equals(to)) {
            return this;
        }
        final Object value = get(from);
        final List<String> comments = source.getComments(sourceKey);
        final List<String> inline = source.getInlineComments(sourceKey);
        remove(from);
        final ConfigurationSection target = parent(toKeys, true);
        final String targetKey = toKeys[toKeys.length - 1];
        YamlTrees.putPlain(target, targetKey, value);
        target.setComments(targetKey, comments);
        target.setInlineComments(targetKey, inline);
        return this;
    }

    /**
     * Replaces the value at {@code path} with {@code change.apply(value)}; {@code null} removes the key. Not
     * called if there is no value.
     *
     * <pre>{@code
     * tree.transform("bid-time", old -> old instanceof Number n ? n + "s" : old);
     * }</pre>
     *
     * @return this
     */
    public ConfigTree transform(final String path, final UnaryOperator<@Nullable Object> change) {
        Objects.requireNonNull(change, "change");
        final Object old = get(path);
        if (old != null) {
            set(path, change.apply(old));
        }
        return this;
    }

    /**
     * Merges the keys of another YAML file into this tree, e.g. the config of an older product: sections are merged
     * key by key and a value from the file replaces the one here.
     *
     * @param file the YAML file to read
     * @return {@code false} if {@code file} does not exist (nothing changed), else {@code true}
     * @throws UncheckedIOException  if the file cannot be read
     * @throws IllegalStateException if the file is not valid YAML
     */
    public boolean importFrom(final Path file) {
        Objects.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) {
            return false;
        }
        final Map<String, Object> data;
        try {
            data = YamlTrees.toPlain(YamlTrees.parse(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (final IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        } catch (final InvalidConfigurationException e) {
            throw new IllegalStateException("Not valid YAML: " + file, e);
        }
        merge(root, data);
        return true;
    }

    private static void merge(final ConfigurationSection into, final Map<String, Object> data) {
        for (final Map.Entry<String, Object> entry : data.entrySet()) {
            final ConfigurationSection existing = into.getConfigurationSection(entry.getKey());
            if (entry.getValue() instanceof Map<?, ?> map && existing != null) {
                @SuppressWarnings("unchecked")
                final Map<String, Object> nested = (Map<String, Object>) map;
                merge(existing, nested);
            } else {
                YamlTrees.putPlain(into, entry.getKey(), entry.getValue());
            }
        }
    }

    private static String[] split(final String path) {
        Objects.requireNonNull(path, "path");
        final String[] keys = path.split("\\.", -1);
        for (final String key : keys) {
            if (key.isEmpty()) {
                throw new IllegalArgumentException("Invalid path \"" + path + "\" (empty segment)");
            }
        }
        return keys;
    }

    /** The section holding the last key of {@code keys}; created on the way if {@code create}, else may be null. */
    private @Nullable ConfigurationSection parent(final String[] keys, final boolean create) {
        ConfigurationSection section = root;
        for (int i = 0; i < keys.length - 1; i++) {
            ConfigurationSection child = section.getConfigurationSection(keys[i]);
            if (child == null) {
                if (!create) {
                    return null;
                }
                child = section.createSection(keys[i]);
            }
            section = child;
        }
        return section;
    }
}
