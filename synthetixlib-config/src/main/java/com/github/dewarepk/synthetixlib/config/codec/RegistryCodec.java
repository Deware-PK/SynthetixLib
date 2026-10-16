package com.github.dewarepk.synthetixlib.config.codec;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

/**
 * A value of a Paper registry written as its key ({@code entity.player.levelup}). The registry is looked up on each
 * call, so the codec can be created before the server has finished loading.
 */
final class RegistryCodec<T extends Keyed> implements Codec<T> {

    private final RegistryKey<T> registryKey;
    private final String noun;
    private final boolean legacySoundNames;

    /**
     * @param noun             what a value is called in messages, e.g. {@code sound}
     * @param legacySoundNames also accept {@code ENTITY_PLAYER_LEVELUP}, the old enum style, as {@code entity.player.levelup}
     */
    RegistryCodec(final RegistryKey<T> registryKey, final String noun, final boolean legacySoundNames) {
        this.registryKey = registryKey;
        this.noun = noun;
        this.legacySoundNames = legacySoundNames;
    }

    @Override
    public T decode(final Object raw, final DecodeContext ctx) {
        final String text = ScalarCodecs.text(raw, ctx).strip();
        final Registry<T> registry = RegistryAccess.registryAccess().getRegistry(registryKey);
        T value = lookup(registry, text);
        if (value == null && legacySoundNames && text.indexOf('.') < 0 && text.indexOf(':') < 0) {
            value = lookup(registry, text.replace('_', '.'));
        }
        if (value == null) {
            throw ctx.fail(Suggestions.unknown(noun, text,
                    registry.keyStream().map(Keys::format).toList()));
        }
        return value;
    }

    private static <T extends Keyed> T lookup(final Registry<T> registry, final String text) {
        final NamespacedKey key = Keys.parse(text);
        return key == null ? null : registry.get(key);
    }

    @Override
    public Object encode(final T value) {
        final NamespacedKey key = RegistryAccess.registryAccess().getRegistry(registryKey).getKey(value);
        if (key == null) {
            throw new IllegalArgumentException(value + " is not in the " + noun + " registry");
        }
        return Keys.format(key);
    }
}
