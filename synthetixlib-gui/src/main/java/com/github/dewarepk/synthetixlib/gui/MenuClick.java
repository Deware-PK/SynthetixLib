package com.github.dewarepk.synthetixlib.gui;

import org.bukkit.entity.Player;

/**
 * A click that reached a handler: who clicked and in which view. Runs on the viewer's thread.
 *
 * <pre>{@code
 * .action("confirm", click -> auctions.bid(click.player(), click.view().context()))
 * .onListClick("lots", (lot, click) -> bidMenu.open(click.player(), lot))
 * }</pre>
 *
 * @param <C> the type of the view's context
 */
public interface MenuClick<C> {

    /** @return the player who clicked */
    Player player();

    /** @return the view that was clicked */
    MenuView<C> view();
}
