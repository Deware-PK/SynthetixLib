package com.github.dewarepk.synthetixlib.dialog;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What code registered on a {@link DialogBuilder} for one dialog: the names a dialog file may refer to. Immutable.
 *
 * @param placeholders tag name to value
 * @param items        item placeholder name to the item
 * @param actions      action name to handler (no form)
 * @param submits      action name to the form it reads and the handler
 * @param inputs       input key to the function that answers its initial value
 * @param onClose      called when the viewer leaves with an exit button, {@code null} if none
 */
record Registrations<C>(Map<String, Function<DialogView<C>, Component>> placeholders,
                        Map<String, Function<DialogView<C>, ItemStack>> items,
                        Map<String, Consumer<DialogClick<C>>> actions,
                        Map<String, Submit<C>> submits,
                        Map<String, Function<DialogView<C>, ?>> inputs,
                        @Nullable Consumer<DialogView<C>> onClose) {

    /**
     * A {@code submit}: the record the answers are read into and what to do with it. The type is erased on purpose;
     * {@link DialogBuilder#submit} is generic in the record.
     *
     * @param form    the record class
     * @param handler called with an instance of {@code form}
     * @param async   {@code true} if the handler must run on an async thread ({@link DialogBuilder#submitAsync})
     */
    record Submit<C>(Class<?> form, BiConsumer<Object, DialogClick<C>> handler, boolean async) {
    }

    /** Nothing registered: what a registry dialog has until code calls {@code registered(key)}. */
    static <C> Registrations<C> none() {
        return new Registrations<>(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), null);
    }

    /** Tags every text has without registering them. */
    static boolean isBuiltInPlaceholder(final String name) {
        return name.equals("player");
    }

    /** @return {@code true} if an {@code action:<name>} finds a handler */
    boolean hasAction(final String name) {
        return actions.containsKey(name) || submits.containsKey(name);
    }
}
