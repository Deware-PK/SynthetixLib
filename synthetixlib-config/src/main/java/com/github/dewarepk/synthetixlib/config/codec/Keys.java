package com.github.dewarepk.synthetixlib.config.codec;

import org.bukkit.NamespacedKey;

/** Text form of keys: {@code minecraft:} is optional on input and omitted on output. */
final class Keys {

    private Keys() {
    }

    /** @return {@code diamond} for {@code minecraft:diamond}, the full form for any other namespace */
    static String format(final NamespacedKey key) {
        return NamespacedKey.MINECRAFT.equals(key.getNamespace()) ? key.getKey() : key.toString();
    }

    /** @return the key for {@code text} (case-insensitive, {@code minecraft:} assumed), or {@code null} if malformed */
    static NamespacedKey parse(final String text) {
        final String lower = text.strip().toLowerCase(java.util.Locale.ROOT);
        return NamespacedKey.fromString(lower.indexOf(':') < 0 ? NamespacedKey.MINECRAFT + ":" + lower : lower);
    }
}
