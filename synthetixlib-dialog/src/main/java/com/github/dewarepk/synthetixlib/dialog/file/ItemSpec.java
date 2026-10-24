package com.github.dewarepk.synthetixlib.dialog.file;

import com.github.dewarepk.synthetixlib.config.annotation.Range;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.jspecify.annotations.Nullable;

/**
 * The {@code item:} of a body entry. Exactly one source: {@code slot} (the item the player holds), {@code placeholder}
 * (an item that code supplies by name) or {@code material} (an item written in the file, with the other keys).
 *
 * <pre>{@code
 * item: { slot: hand }
 * item: { placeholder: lot-item }
 * item: { material: diamond, name: '<aqua>Prize', item-model: 'pack:prize' }
 * }</pre>
 *
 * @param slot        {@code hand} or {@code offhand}
 * @param placeholder name of an item registered in code
 * @param material    the material of an item written in the file
 * @param amount      stack size, 1 to 99 (only with {@code material})
 * @param name        custom name, MiniMessage (only with {@code material})
 * @param lore        lore lines, MiniMessage (only with {@code material})
 * @param itemModel   {@code item_model} key (only with {@code material})
 * @param glint       forces the glint on or off (only with {@code material})
 */
public record ItemSpec(
        Optional<Hand> slot,
        Optional<String> placeholder,
        Optional<Material> material,
        @Range(min = 1, max = 99) Optional<Integer> amount,
        Optional<String> name,
        @Nullable List<String> lore,
        Optional<NamespacedKey> itemModel,
        Optional<Boolean> glint) {

    /** Which hand {@code slot:} names. */
    public enum Hand {
        /** The main hand. */
        HAND,
        /** The off hand. */
        OFFHAND
    }

    public ItemSpec {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(placeholder, "placeholder");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(name, "name");
        lore = lore == null ? List.of() : List.copyOf(lore);
        Objects.requireNonNull(itemModel, "itemModel");
        Objects.requireNonNull(glint, "glint");
    }

    /** @return how many of the three sources are given; a valid item has exactly one */
    public int sources() {
        return (slot.isPresent() ? 1 : 0) + (placeholder.isPresent() ? 1 : 0) + (material.isPresent() ? 1 : 0);
    }

    /** @return {@code true} if a key that only belongs to a {@code material} item is set */
    public boolean hasMaterialKeys() {
        return amount.isPresent() || name.isPresent() || !lore.isEmpty() || itemModel.isPresent() || glint.isPresent();
    }
}
