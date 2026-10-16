package com.github.dewarepk.synthetixlib.config.yaml;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Helpers around Bukkit's {@link YamlConfiguration}. Its path separator is replaced by a control character, so a map
 * key such as {@code vip.gold} stays one key instead of becoming two nested sections; callers address one key at a
 * time and never build paths with {@code '.'}.
 *
 * @apiNote Internal
 */
@org.jetbrains.annotations.ApiStatus.Internal
public final class YamlTrees {

    private static final char NO_PATHS = '\u0001';

    private YamlTrees() {
    }

    /** @return an empty configuration that reads and writes comments */
    public static YamlConfiguration create() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        yaml.options().pathSeparator(NO_PATHS);
        return yaml;
    }

    /** @throws InvalidConfigurationException if {@code text} is not a YAML mapping */
    public static YamlConfiguration parse(final String text) throws InvalidConfigurationException {
        final YamlConfiguration yaml = create();
        yaml.loadFromString(text);
        return yaml;
    }

    /** @return the data as plain nested {@code Map}s (insertion ordered); lists and scalars are shared, not copied */
    public static Map<String, Object> toPlain(final ConfigurationSection section) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final String key : section.getKeys(false)) {
            final Object value = section.get(key);
            out.put(key, value instanceof ConfigurationSection child ? toPlain(child) : value);
        }
        return out;
    }

    /** Sets {@code key} to {@code value}; maps become sections. Replaces whatever was there. */
    public static void putPlain(final ConfigurationSection parent, final String key, final Object value) {
        put(parent, key, value, "", Map.of());
    }

    /**
     * Like {@link #putPlain}, and writes the comment found in {@code comments} for each key written. A key that
     * is replaced keeps its old comment unless {@code comments} has a new one.
     *
     * @param path     dotted data path of {@code key}, the key into {@code comments}
     * @param comments comment lines by dotted data path
     */
    public static void put(final ConfigurationSection parent, final String key, final Object value,
                           final String path, final Map<String, List<String>> comments) {
        final List<String> before = parent.getComments(key);
        final List<String> inlineBefore = parent.getInlineComments(key);
        parent.set(key, null);
        if (value instanceof Map<?, ?> map) {
            final ConfigurationSection child = parent.createSection(key);
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                final String childKey = String.valueOf(entry.getKey());
                put(child, childKey, entry.getValue(), path.isEmpty() ? childKey : path + "." + childKey, comments);
            }
        } else {
            parent.set(key, value);
        }
        final List<String> comment = comments.get(path);
        parent.setComments(key, comment != null ? comment : before);
        parent.setInlineComments(key, inlineBefore);
    }

    /**
     * Copy of {@code source} with {@code key} set to {@code value} as its first entry. Comments (also the file
     * header) are carried over; if {@code key} exists it is moved to the front.
     */
    public static YamlConfiguration withFirst(final YamlConfiguration source, final String key, final Object value) {
        final YamlConfiguration out = create();
        out.options().setHeader(source.options().getHeader());
        out.options().setFooter(source.options().getFooter());
        out.set(key, value);
        copy(source, out, key);
        return out;
    }

    private static void copy(final ConfigurationSection from, final ConfigurationSection to, final String skip) {
        for (final String key : from.getKeys(false)) {
            if (key.equals(skip)) {
                continue;
            }
            final Object value = from.get(key);
            if (value instanceof ConfigurationSection child) {
                copy(child, to.createSection(key), null);
            } else {
                to.set(key, value);
            }
            to.setComments(key, from.getComments(key));
            to.setInlineComments(key, from.getInlineComments(key));
        }
    }
}
