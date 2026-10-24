package com.github.dewarepk.synthetixlib.dialog;

import io.papermc.paper.dialog.Dialog;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** What a {@link DialogType} needs from the service that shows it. */
interface ViewHost {

    /** Opens {@code type} for {@code player}; any thread. */
    <C> void open(DialogType<C> type, Player player, @Nullable C context);

    /** Renders {@code type} for nobody; any thread that may touch the server. */
    <C> Dialog preview(DialogType<C> type, String playerName, @Nullable C context);

    /** Creates and renders the view of {@code player}, registers it as theirs and shows nothing. */
    <C> DialogViewImpl<C> attach(DialogType<C> type, Player player, @Nullable C context);
}
