package com.github.dewarepk.synthetixlib.core.item;

import com.github.dewarepk.synthetixlib.core.text.TextService;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Entry point for {@link ItemBuilder}s. Obtain it from {@code lib.items()}.
 *
 * <pre>{@code
 * ItemStack ticket = lib.items().of(Material.PAPER).name("<gold>Ticket").build();
 * }</pre>
 */
public final class Items {

    private final TextService text;

    private Items(final TextService text) {
        this.text = text;
    }

    /** Internal: use {@code SynthetixLib#items()}. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static Items create(final TextService text) {
        return new Items(Objects.requireNonNull(text, "text"));
    }

    /**
     * @param material the item type
     * @return a builder starting from a fresh stack of {@code material}
     */
    public ItemBuilder of(final Material material) {
        Objects.requireNonNull(material, "material");
        return new ItemBuilder(text, new ItemStack(material));
    }

    /**
     * @param stack the template; it is cloned and never mutated
     * @return a builder starting from a copy of {@code stack}
     */
    public ItemBuilder from(final ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return new ItemBuilder(text, stack.clone());
    }
}
