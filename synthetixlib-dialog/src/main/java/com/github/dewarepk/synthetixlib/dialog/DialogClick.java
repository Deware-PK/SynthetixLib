package com.github.dewarepk.synthetixlib.dialog;

import org.bukkit.entity.Player;

/**
 * A click on a button whose click list has {@code action:<name>}, handed to the handler registered under that name.
 * The typed answers of the inputs come with the submit handling.
 *
 * @param <C> the type of the context the dialog was opened for; {@link Void} if none
 */
public interface DialogClick<C> {

    /** @return the view the click came from */
    DialogView<C> view();

    /** @return the viewer who clicked */
    default Player player() {
        return view().player();
    }
}
