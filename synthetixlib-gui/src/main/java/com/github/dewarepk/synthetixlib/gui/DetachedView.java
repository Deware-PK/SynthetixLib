package com.github.dewarepk.synthetixlib.gui;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A view without a viewer, for rendering a menu that nobody looks at (previews, tests). It only carries the data
 * that registered code reads; every operation does nothing.
 */
final class DetachedView<C> implements MenuView<C> {

    private final @Nullable C context;
    private final int page;

    DetachedView(final @Nullable C context, final int page) {
        this.context = context;
        this.page = page;
    }

    @Override
    public Player player() {
        throw new UnsupportedOperationException("a preview has no viewer");
    }

    @Override
    public @Nullable C context() {
        return context;
    }

    @Override
    public int page() {
        return page;
    }

    @Override
    public void page(final int page) {
    }

    @Override
    public void refresh() {
    }

    @Override
    public void refresh(final char slotChar) {
    }

    @Override
    public void close() {
    }

    @Override
    public void back() {
    }

    @Override
    public List<ItemStack> inputs(final char slotChar) {
        return List.of();
    }

    @Override
    public List<ItemStack> takeInputs(final char slotChar) {
        return List.of();
    }

    @Override
    public List<ItemStack> takeInputs() {
        return List.of();
    }
}
