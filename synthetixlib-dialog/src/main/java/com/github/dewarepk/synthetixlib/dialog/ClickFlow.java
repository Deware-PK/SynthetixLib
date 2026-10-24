package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.dialog.file.ClickAction;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.NamespacedKey;

/**
 * Runs the built-in steps of a click in order. What each step does is up to {@link Steps}; this class only decides the
 * order, keeps one failing step from stopping the others, and tells the caller whether the dialog was replaced (so it
 * must not be closed afterwards) or closed. Free of the server so it is unit-tested.
 */
final class ClickFlow {

    /** What the steps do. */
    interface Steps {
        void command(String command);

        void console(String command);

        void message(String text);

        void sound(NamespacedKey key);

        /** @return {@code true} if another dialog was opened */
        boolean open(String dialog);

        /** @return {@code true} if a menu was opened */
        boolean menu(String menu);

        /** @return {@code true} if the previous dialog was opened */
        boolean back();

        void run(String action);
    }

    /**
     * @param replaced another dialog or menu replaced this one, so the library must not close the player's screen
     * @param closed   a {@code close} step ran
     */
    record Result(boolean replaced, boolean closed) {
    }

    private ClickFlow() {
    }

    /**
     * @param actions the steps of the button, in order
     * @param steps   does them
     * @param failed  told about a step that threw; the next steps still run
     */
    static Result run(final List<ClickAction> actions, final Steps steps, final Consumer<RuntimeException> failed) {
        boolean replaced = false;
        boolean closed = false;
        for (final ClickAction action : actions) {
            try {
                switch (action) {
                    case ClickAction.Close ignored -> closed = true;
                    case ClickAction.Back ignored -> replaced |= steps.back();
                    case ClickAction.Open open -> replaced |= steps.open(open.dialog());
                    case ClickAction.Menu menu -> replaced |= steps.menu(menu.menu());
                    case ClickAction.Command command -> steps.command(command.command());
                    case ClickAction.Console console -> steps.console(console.command());
                    case ClickAction.Message message -> steps.message(message.text());
                    case ClickAction.PlaySound sound -> steps.sound(sound.sound());
                    case ClickAction.Run run -> steps.run(run.name());
                    case ClickAction.Url ignored -> { } // only works as the single step of a static button
                    case ClickAction.Copy ignored -> { }
                }
            } catch (final RuntimeException e) {
                failed.accept(e);
            }
        }
        return new Result(replaced, closed);
    }
}
