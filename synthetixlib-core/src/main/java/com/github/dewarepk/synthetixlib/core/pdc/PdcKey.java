package com.github.dewarepk.synthetixlib.core.pdc;

import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;

/**
 * Immutable, typed {@link NamespacedKey} bound to a value type. Create via {@link PdcKeys}.
 *
 * <pre>{@code
 * PdcKey<String> LOT_ID = lib.keys().string("lot_id");
 * LOT_ID.set(itemStack, "13");
 * Optional<String> lot = LOT_ID.get(itemStack);
 * }</pre>
 *
 * <p>Holder access must happen on the thread that owns the holder (Folia).
 *
 * @param <T> the value type
 */
public final class PdcKey<T> {

    private final NamespacedKey key;
    private final PersistentDataType<?, T> type;

    PdcKey(final NamespacedKey key, final PersistentDataType<?, T> type) {
        this.key = key;
        this.type = type;
    }

    /** @return the underlying namespaced key */
    public NamespacedKey key() {
        return key;
    }

    /** @return the stored value, or empty if absent */
    public Optional<T> get(final PersistentDataHolder holder) {
        Objects.requireNonNull(holder, "holder");
        return Optional.ofNullable(holder.getPersistentDataContainer().get(key, type));
    }

    /** @return the stored value, or empty if absent or the stack is empty; reads a view of the stack, no meta copy */
    public Optional<T> get(final ItemStack item) {
        Objects.requireNonNull(item, "item");
        return Optional.ofNullable(item.getPersistentDataContainer().get(key, type));
    }

    /** @return {@code true} if a value of this key's type is stored */
    public boolean has(final PersistentDataHolder holder) {
        Objects.requireNonNull(holder, "holder");
        return holder.getPersistentDataContainer().has(key, type);
    }

    /** @return {@code true} if a value of this key's type is stored on the stack */
    public boolean has(final ItemStack item) {
        Objects.requireNonNull(item, "item");
        return !item.isEmpty() && item.getPersistentDataContainer().has(key, type);
    }

    /** Stores {@code value}. For tile entities call {@code state.update()} afterwards. */
    public void set(final PersistentDataHolder holder, final T value) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(value, "value");
        holder.getPersistentDataContainer().set(key, type, value);
    }

    /** Stores {@code value} on the stack in place.
     * @throws IllegalArgumentException if the stack is empty (AIR / amount 0) */
    public void set(final ItemStack item, final T value) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(value, "value");
        requireNotEmpty(item);
        item.editPersistentDataContainer(pdc -> pdc.set(key, type, value));
    }

    /** Removes the value if present. */
    public void remove(final PersistentDataHolder holder) {
        Objects.requireNonNull(holder, "holder");
        holder.getPersistentDataContainer().remove(key);
    }

    /** Removes the value from the stack if present. */
    public void remove(final ItemStack item) {
        Objects.requireNonNull(item, "item");
        requireNotEmpty(item);
        item.editPersistentDataContainer(pdc -> pdc.remove(key));
    }

    private static void requireNotEmpty(final ItemStack item) {
        if (item.isEmpty()) {
            throw new IllegalArgumentException("empty item");
        }
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof PdcKey<?> other && key.equals(other.key) && type.equals(other.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, type);
    }

    @Override
    public String toString() {
        return "PdcKey[" + key + "]";
    }
}
