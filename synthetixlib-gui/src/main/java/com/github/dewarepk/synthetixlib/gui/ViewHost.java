package com.github.dewarepk.synthetixlib.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jspecify.annotations.Nullable;

/** What a {@link MenuType} needs from the service that shows it: opening it for a viewer, or rendering it for nobody. */
interface ViewHost {

    /** Opens {@code type} for {@code player}; any thread. */
    void open(MenuType<?> type, Player player, @Nullable Object context);

    /** The title {@link #preview} would give its inventory (an inventory cannot be asked for it without a viewer). */
    Component previewTitle(MenuType<?> type, String playerName, @Nullable Object context, int page);

    /** Renders {@code type} into a new inventory that is not opened; any thread that may touch the server. */
    Inventory preview(MenuType<?> type, String playerName, @Nullable Object context, int page);

    /**
     * Creates the view of {@code player} and registers it as the one they have open, without opening anything for the
     * client. Returns the inventory of the view. For self-tests that feed events to the listener.
     */
    Inventory attach(MenuType<?> type, Player player, @Nullable Object context);
}
