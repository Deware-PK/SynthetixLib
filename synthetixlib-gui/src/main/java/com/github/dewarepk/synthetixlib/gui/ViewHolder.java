package com.github.dewarepk.synthetixlib.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.Nullable;

/**
 * The holder of every inventory this module creates; it is how a listener recognises one of our menus
 * ({@code inventory.getHolder(false)} only, SnapPlug bug #8). One holder per inventory, so a view that re-opens with a
 * new inventory (a changed title) is told apart from the one that was closed.
 */
final class ViewHolder implements InventoryHolder {

    private final @Nullable MenuViewImpl<?> view;
    private Inventory inventory;

    /** @param view the view that shows the inventory, {@code null} for a preview that nobody looks at */
    ViewHolder(final @Nullable MenuViewImpl<?> view) {
        this.view = view;
    }

    void bind(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Nullable MenuViewImpl<?> view() {
        return view;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
