package com.github.dewarepk.synthetixlib.gui;

import com.github.dewarepk.synthetixlib.gui.file.ClickAction;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

/**
 * Registers the code a menu file may refer to by name, then reads the file at {@link #build()}. Obtained from
 * {@link GuiService#menu}. Not thread-safe; use it in {@code onEnable}.
 *
 * <pre>{@code
 * MenuType<Void> auction = gui.menu("auction")
 *         .placeholder("balance", view -> Component.text(economy.balance(view.player())))
 *         .list("lots", view -> auctions.active(), (lot, view) -> lot.icon())
 *         .onListClick("lots", (lot, click) -> bidMenu.open(click.player(), lot))
 *         .state("notify", view -> prefs.notify(view.player()) ? "on" : "off")
 *         .action("toggle-notify", click -> { prefs.toggle(click.player()); click.view().refresh(); })
 *         .build();
 * }</pre>
 *
 * <p>Names are lower case letters, digits, {@code -} and {@code _}. A handler's view and click carry the context
 * type {@code C}; a menu that is opened for something starts with {@link #withContext()}.
 *
 * @param <C> the type of the context a view is opened for; {@link Void} if none
 */
public final class MenuBuilder<C> {

    private static final Duration DEFAULT_ASYNC_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern NAME = Pattern.compile(ClickAction.NAME_PATTERN);

    private final MenuRegistry registry;
    private final String id;
    private final Map<String, Function<MenuView<C>, Component>> placeholders = new LinkedHashMap<>();
    private final Map<String, Registrations.ListBinding<C>> lists = new LinkedHashMap<>();
    private final Map<String, BiConsumer<Object, MenuClick<C>>> listClicks = new LinkedHashMap<>();
    private final Map<String, Function<MenuView<C>, String>> states = new LinkedHashMap<>();
    private final Map<String, Consumer<MenuClick<C>>> actions = new LinkedHashMap<>();
    private final Map<String, Registrations.AsyncAction<C>> asyncActions = new LinkedHashMap<>();
    private final Map<Character, BiConsumer<MenuView<C>, List<ItemStack>>> inputChanges = new LinkedHashMap<>();
    private boolean built;

    MenuBuilder(final MenuRegistry registry, final String id) {
        this.registry = registry;
        this.id = id;
    }

    /**
     * Makes this a menu that is opened for something, e.g. a lot: {@code view.context()} is then that object. Call it
     * before registering anything, because everything registered afterwards is typed with the context.
     *
     * <pre>{@code
     * MenuType<Lot> bid = gui.menu("bid").<Lot>withContext()
     *         .placeholder("price", view -> Component.text(view.context().price()))
     *         .build();
     * }</pre>
     *
     * @param <N> the type of the context
     * @return a builder for the same menu with the context type {@code N}
     * @throws IllegalStateException if something was registered already
     */
    public <N> MenuBuilder<N> withContext() {
        ensureOpen();
        if (!placeholders.isEmpty() || !lists.isEmpty() || !listClicks.isEmpty() || !states.isEmpty()
                || !actions.isEmpty() || !asyncActions.isEmpty() || !inputChanges.isEmpty()) {
            throw new IllegalStateException("withContext() must come before the first registration of menu '" + id
                    + "'");
        }
        built = true;
        return new MenuBuilder<>(registry, id);
    }

    /**
     * Registers a tag usable in every text of the file as {@code <name>}. The value is a finished component: it is
     * inserted as it is and never parsed as MiniMessage, so player input cannot inject markup.
     *
     * @param name  e.g. {@code balance}; {@code player}, {@code page} and {@code pages} are built in
     * @param value called whenever a view renders the text
     * @return this
     * @throws IllegalArgumentException if the name is not valid, built in or registered already
     */
    public MenuBuilder<C> placeholder(final String name, final Function<MenuView<C>, Component> value) {
        ensureOpen();
        checkName("placeholder", name);
        Objects.requireNonNull(value, "value");
        if (Registrations.isBuiltInPlaceholder(name)) {
            throw new IllegalArgumentException("placeholder '" + name + "' is built in");
        }
        putNew(placeholders, "placeholder", name, value);
        return this;
    }

    /**
     * Registers a list that items with {@code list: <id>} show, one entry per slot with that character.
     *
     * <p>The entry type {@code E} is whatever {@code source} returns; {@link #onListClick} must use the same one.
     *
     * @param id     the id used in the file
     * @param source called on open, refresh and page change
     * @param render how one entry looks
     * @return this
     * @throws IllegalArgumentException if the id is not valid or registered already
     */
    public <E> MenuBuilder<C> list(final String id, final Function<MenuView<C>, List<E>> source,
                                   final BiFunction<E, MenuView<C>, ItemStack> render) {
        ensureOpen();
        checkName("list", id);
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(render, "render");
        @SuppressWarnings("unchecked")
        final BiFunction<Object, MenuView<C>, ItemStack> erased =
                (entry, view) -> render.apply((E) entry, view);
        putNew(lists, "list", id, new Registrations.ListBinding<C>(view -> source.apply(view), erased, null, false));
        return this;
    }

    /**
     * Like {@link #list} for a slow source (a database, a web call): {@code source} runs on the async scheduler, never on
     * the server thread, and {@code render} runs on the viewer's thread. Until the entries arrive the slots show the
     * {@code loading:} item of the file's list item (nothing if it has none). The entries are shown when they arrive;
     * a view closed before that drops them. A refresh asks the source again and keeps showing the old entries meanwhile.
     *
     * <pre>{@code
     * .listAsync("lots", view -> db.activeLots(), (lot, view) -> lot.icon())
     * }</pre>
     *
     * <p>{@code source} gets the view but must not touch the world or the player; {@code view.context()} is fine.
     *
     * @param id     the id used in the file
     * @param source called on an async thread on open, refresh and page change
     * @param render how one entry looks, on the viewer's thread
     * @return this
     * @throws IllegalArgumentException if the id is not valid or registered already
     */
    public <E> MenuBuilder<C> listAsync(final String id, final Function<MenuView<C>, List<E>> source,
                                        final BiFunction<E, MenuView<C>, ItemStack> render) {
        ensureOpen();
        checkName("list", id);
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(render, "render");
        @SuppressWarnings("unchecked")
        final BiFunction<Object, MenuView<C>, ItemStack> erased =
                (entry, view) -> render.apply((E) entry, view);
        putNew(lists, "list", id, new Registrations.ListBinding<C>(view -> source.apply(view), erased, null, true));
        return this;
    }

    /**
     * Registers what a click on an entry of a list does. {@code E} must be the entry type of the list; if the
     * compiler cannot infer it, name it: {@code .<Lot>onListClick("lots", (lot, click) -> ...)}.
     *
     * @param listId  the id given to {@link #list}
     * @param handler gets the entry and the click, on the viewer's thread
     * @return this
     * @throws IllegalArgumentException if a handler is registered for this list already
     */
    public <E> MenuBuilder<C> onListClick(final String listId, final BiConsumer<E, MenuClick<C>> handler) {
        ensureOpen();
        checkName("list", listId);
        Objects.requireNonNull(handler, "handler");
        @SuppressWarnings("unchecked")
        final BiConsumer<Object, MenuClick<C>> erased = (entry, click) -> handler.accept((E) entry, click);
        putNew(listClicks, "list click handler", listId, erased);
        return this;
    }

    /**
     * Registers a state that items with {@code state: <id>} show: {@code source} answers with the name of one of the
     * item's {@code states}.
     *
     * @param id     the id used in the file
     * @param source called whenever a view renders the item
     * @return this
     * @throws IllegalArgumentException if the id is not valid or registered already
     */
    public MenuBuilder<C> state(final String id, final Function<MenuView<C>, String> source) {
        ensureOpen();
        checkName("state", id);
        putNew(states, "state", id, Objects.requireNonNull(source, "source"));
        return this;
    }

    /**
     * Registers an action that a click runs as {@code action:<name>}.
     *
     * @param name    the name used in the file
     * @param handler runs on the viewer's thread
     * @return this
     * @throws IllegalArgumentException if the name is not valid or registered already
     */
    public MenuBuilder<C> action(final String name, final Consumer<MenuClick<C>> handler) {
        ensureOpen();
        checkName("action", name);
        if (asyncActions.containsKey(name)) {
            throw new IllegalArgumentException("action '" + name + "' is registered already");
        }
        putNew(actions, "action", name, Objects.requireNonNull(handler, "handler"));
        return this;
    }

    /**
     * Like {@link #action}, but the view stays busy until the returned future completes: every other click in this view is
     * ignored meanwhile (Esc still closes it). Use it for work that outlives the click, like a database update, so a
     * double click cannot run it twice. Waits at most 30 seconds.
     *
     * <pre>{@code
     * .actionAsync("claim", click -> mail.claim(click.player()))   // returns a CompletableFuture
     * }</pre>
     *
     * <p>The gate only stops double clicks in one view. Your own update must still be atomic, e.g.
     * {@code ... WHERE claimed_at IS NULL}. A menu item may set {@code busy:} to look different while the action runs.
     * If the future fails, the failure is logged and the player gets {@code gui.action-failed} if the language file has it;
     * the later steps of the same click are skipped.
     *
     * @param name    the name used in the file, as {@code action:<name>}
     * @param handler runs on the viewer's thread and starts the work; {@code null} counts as done
     * @return this
     * @throws IllegalArgumentException if the name is not valid, or an action of this name is registered already
     * @since 0.9.0
     */
    public MenuBuilder<C> actionAsync(final String name, final Function<MenuClick<C>, CompletableFuture<?>> handler) {
        return actionAsync(name, DEFAULT_ASYNC_TIMEOUT, handler);
    }

    /**
     * Like {@link #actionAsync(String, Function)} with another timeout. When it passes, the view is free again and a
     * warning is logged; the future is not cancelled, so the work may still finish.
     *
     * @param name    the name used in the file, as {@code action:<name>}
     * @param timeout how long the view stays busy at most; at least one tick (50 ms)
     * @param handler runs on the viewer's thread and starts the work; {@code null} counts as done
     * @return this
     * @throws IllegalArgumentException if the name is not valid, the timeout is shorter than a tick, or an action of
     *                                  this name is registered already
     * @since 0.9.0
     */
    public MenuBuilder<C> actionAsync(final String name, final Duration timeout,
                                      final Function<MenuClick<C>, CompletableFuture<?>> handler) {
        ensureOpen();
        checkName("action", name);
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(handler, "handler");
        if (timeout.compareTo(Duration.ofMillis(50)) < 0) {
            throw new IllegalArgumentException("the timeout of action '" + name + "' must be at least one tick (50 ms)");
        }
        if (actions.containsKey(name)) {
            throw new IllegalArgumentException("action '" + name + "' is registered already");
        }
        putNew(asyncActions, "action", name, new Registrations.AsyncAction<>(handler, timeout));
        return this;
    }

    /**
     * Registers what happens after a player changed the items of an input slot ({@code input: true} in the file): put
     * something in, took something out, shift-clicked, dragged. The handler gets the viewer's view and the items now in all
     * the slots with this character (copies, empty slots left out), once per change, on the viewer's thread after the
     * server finished the click. It is not called for changes the library makes itself (handing the items back, and
     * {@link MenuView#takeInputs()}).
     *
     * <pre>{@code
     * .onInputChange('S', (view, items) -> {
     *     view.refresh('P');                                  // e.g. show the price of what was put in
     *     prices.preview(view.player(), items);
     * })
     * }</pre>
     *
     * @param slotChar the layout character of the input slots
     * @param handler  gets the view and the items in the slots
     * @return this
     * @throws IllegalArgumentException if {@code slotChar} is a space or a handler is registered for it already
     */
    public MenuBuilder<C> onInputChange(final char slotChar, final BiConsumer<MenuView<C>, List<ItemStack>> handler) {
        ensureOpen();
        if (slotChar == ' ') {
            throw new IllegalArgumentException("a space is an empty slot, not an input slot");
        }
        Objects.requireNonNull(handler, "handler");
        if (inputChanges.putIfAbsent(slotChar, handler) != null) {
            throw new IllegalArgumentException("input change handler '" + slotChar + "' is registered already");
        }
        return this;
    }

    /**
     * Reads {@code menus/<id>.yml} (copied from the plugin jar if it does not exist yet), checks it against the
     * registrations and logs every problem with the file and the item. A problem in the file never throws: the item
     * concerned is inert or its slots empty and the menu still works.
     *
     * @return the menu, registered with the service so {@link GuiService#reload()} finds it
     * @throws IllegalStateException if there is no file and no bundled one, a handler was registered for a list that
     *                               was not, a menu with this id was built already, or the service is closed
     */
    public MenuType<C> build() {
        ensureOpen();
        for (final String listId : listClicks.keySet()) {
            if (!lists.containsKey(listId)) {
                throw new IllegalStateException("onListClick(\"" + listId + "\") but no list(\"" + listId
                        + "\") in menu '" + id + "'");
            }
        }
        final Map<String, Registrations.ListBinding<C>> bound = new LinkedHashMap<>();
        lists.forEach((listId, list) -> bound.put(listId,
                new Registrations.ListBinding<>(list.source(), list.render(), listClicks.get(listId), list.async())));
        final Registrations<C> registrations = new Registrations<>(Map.copyOf(placeholders), Map.copyOf(bound),
                Map.copyOf(states), Map.copyOf(actions), Map.copyOf(inputChanges), Map.copyOf(asyncActions));
        built = true;
        return registry.register(id, registrations);
    }

    private void ensureOpen() {
        if (built) {
            throw new IllegalStateException("This builder of menu '" + id + "' was already used");
        }
    }

    private static void checkName(final String what, final String name) {
        Objects.requireNonNull(name, what);
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("'" + name + "' is not a valid " + what
                    + " name (lower case letters, digits, - and _, at most 64)");
        }
    }

    private static <V> void putNew(final Map<String, V> target, final String what, final String name, final V value) {
        if (target.putIfAbsent(name, value) != null) {
            throw new IllegalArgumentException(what + " '" + name + "' is registered already");
        }
    }
}
