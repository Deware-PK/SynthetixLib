package com.github.dewarepk.synthetixlib.gui;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What code registered on a {@link MenuBuilder} for one menu: the names a menu file may refer to. Immutable.
 *
 * @param placeholders tag name to value
 * @param lists        list id to its source, look and click handler
 * @param states       state id to the function that answers with the name of a state
 * @param actions      action name to handler
 * @param inputs       layout character of an input slot to the handler that is told when its items change
 */
record Registrations<C>(Map<String, Function<MenuView<C>, Component>> placeholders,
                        Map<String, ListBinding<C>> lists,
                        Map<String, Function<MenuView<C>, String>> states,
                        Map<String, Consumer<MenuClick<C>>> actions,
                        Map<Character, BiConsumer<MenuView<C>, List<ItemStack>>> inputs) {

    /** Registrations without input handlers. */
    Registrations(final Map<String, Function<MenuView<C>, Component>> placeholders,
                  final Map<String, ListBinding<C>> lists,
                  final Map<String, Function<MenuView<C>, String>> states,
                  final Map<String, Consumer<MenuClick<C>>> actions) {
        this(placeholders, lists, states, actions, Map.of());
    }

    /** Tags every text has without registering them. */
    static boolean isBuiltInPlaceholder(final String name) {
        return switch (name) {
            case "player", "page", "pages" -> true;
            default -> false;
        };
    }

    /**
     * A list: where the entries come from, how one looks and what a click on it does. Types are erased on purpose;
     * {@link MenuBuilder#list} and {@link MenuBuilder#onListClick} are generic in the entry type.
     *
     * @param onClick {@code null} if no handler was registered
     * @param async   {@code true} if {@code source} must run on an async thread ({@link MenuBuilder#listAsync})
     */
    record ListBinding<C>(Function<MenuView<C>, List<?>> source, BiFunction<Object, MenuView<C>, ItemStack> render,
                          @Nullable BiConsumer<Object, MenuClick<C>> onClick, boolean async) {

        /** A list whose source runs on the viewer's thread. */
        ListBinding(final Function<MenuView<C>, List<?>> source, final BiFunction<Object, MenuView<C>, ItemStack> render,
                    final @Nullable BiConsumer<Object, MenuClick<C>> onClick) {
            this(source, render, onClick, false);
        }
    }
}
