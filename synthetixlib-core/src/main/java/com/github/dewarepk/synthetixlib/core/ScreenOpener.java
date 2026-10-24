package com.github.dewarepk.synthetixlib.core;

import org.bukkit.entity.Player;

/**
 * Opens a screen (a menu, a dialog) by its id for a player. The gui and dialog modules hand one to each other, so a menu
 * file can say {@code dialog:<id>} and a dialog file {@code menu:<id>} without the modules depending on each other.
 *
 * <pre>{@code
 * gui.link("dialog", dialogs.opener());     // menus: dialog:sell
 * dialogs.link("menu", gui.opener());       // dialogs: menu:shop
 * }</pre>
 */
@FunctionalInterface
public interface ScreenOpener {

    /**
     * Opens the screen. Called on the thread that owns {@code player}.
     *
     * @param player the viewer
     * @param id     the id of the screen in the module that opens it
     * @return {@code true} if a screen with this id was opened, {@code false} if there is none
     */
    boolean open(Player player, String id);
}
