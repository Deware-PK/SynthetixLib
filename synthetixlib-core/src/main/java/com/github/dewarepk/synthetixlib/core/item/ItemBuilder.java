package com.github.dewarepk.synthetixlib.core.item;

import com.github.dewarepk.synthetixlib.core.pdc.PdcKey;
import com.github.dewarepk.synthetixlib.core.text.TextService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Reusable item template. Operations are recorded and applied in order on a fresh copy of the base
 * stack at {@link #build()}, so every build returns an independent {@link ItemStack}.
 *
 * <pre>{@code
 * ItemStack ticket = lib.items().of(Material.PAPER)
 *         .name("<gold>Auction ticket")
 *         .lore("<gray>Lot #<id>", Placeholder.unparsed("id", "12"))
 *         .glint(true)
 *         .build();
 * }</pre>
 *
 * <p>Not thread-safe. Player-provided text must go through {@code Placeholder.unparsed}.
 */
public final class ItemBuilder {

    private final TextService text;
    private final ItemStack base;
    private final List<Consumer<ItemStack>> operations = new ArrayList<>();
    private Integer amount;

    ItemBuilder(final TextService text, final ItemStack base) {
        this.text = text;
        this.base = base;
    }

    /** Sets the custom name (MiniMessage, italic off unless the markup sets it). */
    public ItemBuilder name(final String markup, final TagResolver... resolvers) {
        final Component name = text.parseItem(markup, resolvers);
        return meta(m -> m.customName(name));
    }

    /** Sets the base item name (overridable by anvils, unlike {@link #name}). */
    public ItemBuilder itemName(final String markup, final TagResolver... resolvers) {
        final Component name = text.parseItem(markup, resolvers);
        return meta(m -> m.itemName(name));
    }

    /** Appends one lore line (MiniMessage, italic off unless the markup sets it). */
    public ItemBuilder lore(final String markup, final TagResolver... resolvers) {
        final Component line = text.parseItem(markup, resolvers);
        return meta(m -> {
            final List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
            lore.add(line);
            m.lore(lore);
        });
    }

    /** Replaces the lore. */
    public ItemBuilder lore(final List<Component> lines) {
        final List<Component> copy = List.copyOf(Objects.requireNonNull(lines, "lines"));
        return meta(m -> m.lore(copy));
    }

    /** Removes all lore. */
    public ItemBuilder clearLore() {
        return meta(m -> m.lore(null));
    }

    /** Sets the {@code item_model} data component. */
    public ItemBuilder itemModel(final NamespacedKey model) {
        Objects.requireNonNull(model, "model");
        return meta(m -> m.setItemModel(model));
    }

    /** Sets the stack size; validated against the max stack size at {@link #build()}. */
    public ItemBuilder amount(final int amount) {
        this.amount = amount;
        return this;
    }

    /** Forces the enchantment glint on or off. */
    public ItemBuilder glint(final boolean glint) {
        return meta(m -> m.setEnchantmentGlintOverride(glint));
    }

    /** Hides or shows the whole tooltip. */
    public ItemBuilder hideTooltip(final boolean hide) {
        return meta(m -> m.setHideTooltip(hide));
    }

    /** Stores a typed PDC value. */
    public <T> ItemBuilder set(final PdcKey<T> key, final T value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        return edit(stack -> key.set(stack, value));
    }

    /** Escape hatch: edits the {@link ItemMeta} at build time. */
    public ItemBuilder meta(final Consumer<ItemMeta> action) {
        Objects.requireNonNull(action, "action");
        return edit(stack -> stack.editMeta(action));
    }

    /** Escape hatch: edits the stack (e.g. data components) at build time. */
    public ItemBuilder edit(final Consumer<ItemStack> action) {
        Objects.requireNonNull(action, "action");
        operations.add(action);
        return this;
    }

    /**
     * @return a new stack with all operations applied; never the same instance twice
     * @throws IllegalArgumentException if the amount is outside {@code 1..maxStackSize}
     */
    public ItemStack build() {
        final ItemStack stack = base.clone();
        for (final Consumer<ItemStack> operation : operations) {
            operation.accept(stack);
        }
        if (amount != null) {
            final int max = stack.getMaxStackSize();
            if (amount < 1 || amount > max) {
                throw new IllegalArgumentException("amount " + amount + " outside 1.." + max);
            }
            stack.setAmount(amount);
        }
        return stack;
    }
}
