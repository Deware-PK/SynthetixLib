package com.github.dewarepk.synthetixlib.gui;

import com.github.dewarepk.synthetixlib.gui.file.MenuFile;
import com.github.dewarepk.synthetixlib.gui.file.MenuItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** {@link StackOps} for Bukkit item stacks, and the slot rules of a menu file made of them. Holds no state. */
final class BukkitStacks implements StackOps<ItemStack> {

    private final AcceptFilter.TagLookup tags = key -> {
        final Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
        return tag == null ? null : tag::isTagged;
    };

    @Override
    public boolean isEmpty(final @Nullable ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    @Override
    public int amount(final ItemStack stack) {
        return stack.getAmount();
    }

    @Override
    public int maxStack(final ItemStack stack) {
        return stack.getMaxStackSize();
    }

    @Override
    public boolean sameKind(final ItemStack a, final ItemStack b) {
        return a.isSimilar(b);
    }

    @Override
    public ItemStack copy(final ItemStack stack) {
        return stack.clone();
    }

    @Override
    public ItemStack withAmount(final ItemStack stack, final int amount) {
        final ItemStack copy = stack.clone();
        copy.setAmount(amount);
        return copy;
    }

    /**
     * The rules of the slots of {@code file}: a layout character whose item has {@code input: true} (and is not inert) makes its
     * slots input slots, every other slot is locked.
     */
    SlotMap<ItemStack> slotMap(final MenuFile file, final Set<Character> inert) {
        final Map<Character, SlotMap.Input<ItemStack>> byChar = new HashMap<>();
        final List<SlotMap.@Nullable Input<ItemStack>> slots = new ArrayList<>(file.size());
        final Set<Character> inputChars = SlotMap.inputChars(file, inert);
        for (int slot = 0; slot < file.size(); slot++) {
            final char c = file.charAt(slot);
            slots.add(inputChars.contains(c) ? byChar.computeIfAbsent(c, key -> rule(key, file.item(key))) : null);
        }
        return new SlotMap<>(slots);
    }

    private SlotMap.Input<ItemStack> rule(final char id, final MenuItem item) {
        final AcceptFilter filter = AcceptFilter.parse(item.accept()).filter();
        final Predicate<ItemStack> accepts = stack -> filter.matches(stack.getType(), tags);
        return new SlotMap.Input<>(id, accepts, item.maxAmount().orElse(Integer.MAX_VALUE));
    }
}
