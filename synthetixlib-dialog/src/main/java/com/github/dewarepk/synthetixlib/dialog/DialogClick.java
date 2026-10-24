package com.github.dewarepk.synthetixlib.dialog;

import org.bukkit.entity.Player;

/**
 * A click on a button whose click list has {@code action:<name>}, handed to the handler registered under that name.
 * The answers of the inputs are {@link #response()}; a {@code submit} gets them as a record as well.
 *
 * @param <C> the type of the context the dialog was opened for; {@link Void} if none
 */
public interface DialogClick<C> {

    /** @return the view the click came from */
    DialogView<C> view();

    /** @return what the viewer filled in when they clicked; answers that break the limits of the file are empty */
    DialogResponse response();

    /** @return the viewer who clicked */
    default Player player() {
        return view().player();
    }
}
