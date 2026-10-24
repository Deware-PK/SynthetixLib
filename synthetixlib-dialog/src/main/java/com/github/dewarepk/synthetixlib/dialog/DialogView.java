package com.github.dewarepk.synthetixlib.dialog;

import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * One viewer's copy of an open dialog. Handlers registered on a {@link DialogBuilder} receive it; everything that
 * belongs to one viewer lives here, never in a {@link DialogType}. A view is single use per showing: the first button
 * that is clicked ends the showing, and the library then closes the dialog, shows it again ({@link #reopen()}) or
 * shows the next one ({@link #next}), whichever the handler asked for last.
 *
 * <pre>{@code
 * dialogs.dialog("sell").<Lot>withContext()
 *         .action("refresh", click -> click.view().reopen())      // the typed values stay
 *         .action("confirm", click -> click.view().next(confirm, click.view().context()))
 * }</pre>
 *
 * @param <C> the type of the context the dialog was opened for; {@link Void} if none
 */
public interface DialogView<C> {

    /** @return the viewer */
    Player player();

    /** @return what the dialog was opened for, {@code null} if nothing */
    @Nullable C context();

    /**
     * Shows this dialog again with fresh buttons. What the player typed or chose at their last click comes back as the
     * initial values of the inputs. From a handler this takes effect after the handler returns, on the viewer's thread;
     * from anywhere else it is scheduled to the viewer's thread.
     */
    void reopen();

    /**
     * Closes the dialog on the viewer's screen. From a handler this takes effect after the handler returns.
     */
    void close();

    /**
     * Replaces this dialog with another one; {@code back} returns to this one. From a handler this takes effect after the
     * handler returns.
     *
     * @param type    the dialog to show
     * @param context what it is about, {@code null} for none
     * @param <N>     the context type of the next dialog
     */
    <N> void next(DialogType<N> type, @Nullable N context);
}
