package com.github.dewarepk.synthetixlib.core.pdc;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;

/**
 * Factory for {@link PdcKey}s namespaced to the consumer plugin. Obtain it from {@code lib.keys()}.
 *
 * <pre>{@code
 * PdcKey<Integer> BIDS = lib.keys().integer("bids");
 * }</pre>
 */
public final class PdcKeys {

    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9/._-]+");

    private final JavaPlugin plugin;
    private final UuidDataType uuidType = new UuidDataType();

    private PdcKeys(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Internal: use {@code SynthetixLib#keys()}. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static PdcKeys create(final JavaPlugin plugin) {
        return new PdcKeys(Objects.requireNonNull(plugin, "plugin"));
    }

    /** @throws IllegalArgumentException if {@code name} is not {@code [a-z0-9/._-]+} */
    static void validateName(final String name) {
        if (name == null || !VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid PDC key name '" + name + "', allowed: [a-z0-9/._-]+");
        }
    }

    public PdcKey<String> string(final String name) {
        return of(name, PersistentDataType.STRING);
    }

    public PdcKey<Integer> integer(final String name) {
        return of(name, PersistentDataType.INTEGER);
    }

    public PdcKey<Long> longKey(final String name) {
        return of(name, PersistentDataType.LONG);
    }

    public PdcKey<Double> doubleKey(final String name) {
        return of(name, PersistentDataType.DOUBLE);
    }

    public PdcKey<Boolean> bool(final String name) {
        return of(name, PersistentDataType.BOOLEAN);
    }

    /** Stored as 16 bytes (most significant bits, then least significant bits, big-endian). */
    public PdcKey<UUID> uuid(final String name) {
        return of(name, uuidType);
    }

    public PdcKey<byte[]> bytes(final String name) {
        return of(name, PersistentDataType.BYTE_ARRAY);
    }

    /**
     * Key for any {@link PersistentDataType}.
     *
     * @throws IllegalArgumentException if {@code name} is invalid
     */
    public <T> PdcKey<T> of(final String name, final PersistentDataType<?, T> type) {
        validateName(name);
        Objects.requireNonNull(type, "type");
        return new PdcKey<>(new NamespacedKey(plugin, name), type);
    }
}
