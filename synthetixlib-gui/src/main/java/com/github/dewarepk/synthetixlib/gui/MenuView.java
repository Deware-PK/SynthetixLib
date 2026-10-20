package com.github.dewarepk.synthetixlib.gui;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * One player's open copy of a menu: own inventory, own page, own context. Handlers registered on a
 * {@link MenuBuilder} receive it; nothing about a viewer is kept in the {@link MenuType}.
 *
 * <pre>{@code
 * .placeholder("balance", view -> Component.text(economy.balance(view.player())))
 * .action("toggle-notify", click -> { prefs.toggle(click.player()); click.view().refresh(); })
 * }</pre>
 *
 * @param <C> the type of {@link #context()}; {@link Void} for a menu that is not opened for something
 */
public interface MenuView<C> {

    /** @return the viewer */
    Player player();

    /** @return what the menu was opened for, {@code null} for a menu without a context */
    @Nullable C context();

    /** @return the current page, from 0 */
    int page();

    /** Shows another page; a page outside the lists is clamped to the nearest one. */
    void page(int page);

    /** Renders every item again. Safe from any thread. */
    void refresh();

    /** Renders the slots of one layout character again. Safe from any thread. */
    void refresh(char slotChar);

    /** Closes the menu for the viewer. Safe from any thread. */
    void close();

    /** Opens the previous menu of the viewer, or closes if there is none. Safe from any thread. */
    void back();

    /**
     * The items the viewer put into the input slots ({@code input: true}) with this layout character, in slot order. Empty
     * slots are left out and the stacks are copies. Call it on the viewer's thread, e.g. from a click handler.
     *
     * @param slotChar the layout character of the input slots
     * @return the items; empty if there are none or the character has no input slots
     */
    List<ItemStack> inputs(char slotChar);

    /**
     * Takes the items out of the input slots with this layout character and returns them. The slots are empty afterwards,
     * so a second call returns nothing: a confirm click that takes the items cannot take them twice. What is taken is no
     * longer handed back when the menu closes; it is the caller's to use, give or return. Call it on the viewer's thread,
     * e.g. from a click handler.
     *
     * <pre>{@code
     * .action("sell", click -> {
     *     List<ItemStack> items = click.view().takeInputs('S');   // slots are empty now
     *     market.sell(click.player(), items);
     * })
     * }</pre>
     *
     * @param slotChar the layout character of the input slots
     * @return the items that were in the slots; empty if there were none
     */
    List<ItemStack> takeInputs(char slotChar);

    /**
     * Like {@link #takeInputs(char)} for every input slot of the menu, in slot order.
     *
     * @return the items that were in the input slots; empty if there were none
     */
    List<ItemStack> takeInputs();
}
