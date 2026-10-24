package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.dialog.file.ClickAction;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Registers the code a dialog file may refer to by name, then reads the file at {@link #build()}. Obtained from
 * {@link DialogService#dialog}. Not thread-safe; use it in {@code onEnable}. Registering only stores: what the
 * handlers do when a dialog is shown or answered is handled by the views.
 *
 * <pre>{@code
 * record SellForm(double price, String duration, String note, boolean anonymous) {}
 *
 * DialogType<ItemStack> sell = dialogs.dialog("sell").<ItemStack>withContext()
 *         .placeholder("item-name", view -> Component.text(view.context().getType().name()))
 *         .submit("sell", SellForm.class, (form, click) -> auctions.list(click.player(), form))
 *         .build();
 * }</pre>
 *
 * <p>Names are lower case letters, digits, {@code -} and {@code _}. {@code action} and {@code submit} share the
 * names used by {@code action:<name>} in the file. A handler's view and click carry the context type {@code C}; a
 * dialog that is opened for something starts with {@link #withContext()}.
 *
 * @param <C> the type of the context a view is opened for; {@link Void} if none
 */
public final class DialogBuilder<C> {

    private static final Pattern NAME = Pattern.compile(ClickAction.NAME_PATTERN);

    private final DialogRegistry registry;
    private final String id;
    private final Map<String, Function<DialogView<C>, Component>> placeholders = new LinkedHashMap<>();
    private final Map<String, Function<DialogView<C>, ItemStack>> items = new LinkedHashMap<>();
    private final Map<String, Consumer<DialogClick<C>>> actions = new LinkedHashMap<>();
    private final Map<String, Registrations.Submit<C>> submits = new LinkedHashMap<>();
    private final Map<String, Function<DialogView<C>, ?>> inputs = new LinkedHashMap<>();
    private @Nullable Consumer<DialogView<C>> onClose;
    private boolean built;

    DialogBuilder(final DialogRegistry registry, final String id) {
        this.registry = registry;
        this.id = id;
    }

    /**
     * Makes this a dialog that is opened for something, e.g. a lot: {@code view.context()} is then that object. Call
     * it before registering anything, because everything registered afterwards is typed with the context.
     *
     * <pre>{@code
     * DialogType<Lot> bid = dialogs.dialog("bid").<Lot>withContext()
     *         .placeholder("price", view -> Component.text(view.context().price()))
     *         .build();
     * }</pre>
     *
     * @param <N> the type of the context
     * @return a builder for the same dialog with the context type {@code N}
     * @throws IllegalStateException if something was registered already
     */
    public <N> DialogBuilder<N> withContext() {
        ensureOpen();
        if (!placeholders.isEmpty() || !items.isEmpty() || !actions.isEmpty() || !submits.isEmpty() || !inputs.isEmpty()
                || onClose != null) {
            throw new IllegalStateException("withContext() must come before the first registration of dialog '" + id
                    + "'");
        }
        built = true;
        return new DialogBuilder<>(registry, id);
    }

    /**
     * Registers a tag usable in every text of the file as {@code <name>}. The value is a finished component: it is
     * inserted as it is and never parsed as MiniMessage, so player input cannot inject markup.
     *
     * @param name  e.g. {@code fee}; {@code player} is built in
     * @param value called whenever a view renders the text
     * @return this
     * @throws IllegalArgumentException if the name is not valid, built in or registered already
     */
    public DialogBuilder<C> placeholder(final String name, final Function<DialogView<C>, Component> value) {
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
     * Registers an item that a body entry shows with {@code item: { placeholder: <name> }}. The stack is copied before
     * it is shown, so the plugin may return its own.
     *
     * <pre>{@code
     * dialogs.dialog("sell").<Lot>withContext().item("lot-item", view -> view.context().icon())
     * }</pre>
     *
     * @param name  e.g. {@code lot-item}
     * @param value called whenever a view is shown; an empty stack is shown as a barrier
     * @return this
     * @throws IllegalArgumentException if the name is not valid or registered already
     */
    public DialogBuilder<C> item(final String name, final Function<DialogView<C>, ItemStack> value) {
        ensureOpen();
        checkName("item", name);
        Objects.requireNonNull(value, "value");
        putNew(items, "item", name, value);
        return this;
    }

    /**
     * Registers the handler of {@code action:<name>} for a button that needs no form.
     *
     * @param name    the name used in the file
     * @param handler called when the button is clicked
     * @return this
     * @throws IllegalArgumentException if the name is not valid or used by an action or submit already
     */
    public DialogBuilder<C> action(final String name, final Consumer<DialogClick<C>> handler) {
        ensureOpen();
        checkName("action", name);
        Objects.requireNonNull(handler, "handler");
        checkFree(name);
        actions.put(name, handler);
        return this;
    }

    /**
     * Registers the handler of {@code action:<name>} for a button that sends the inputs. The answers are read into a
     * record whose components are the input keys (kebab-case); {@link #build()} warns if they do not match the file.
     *
     * <pre>{@code
     * dialogs.dialog("sell").submit("sell", SellForm.class, (form, click) -> auctions.list(click.player(), form))
     * }</pre>
     *
     * @param name    the name used in the file
     * @param form    a public record
     * @param handler called with the answers
     * @param <R>     the record
     * @return this
     * @throws IllegalArgumentException if the name is not valid or used by an action or submit already
     */
    public <R extends Record> DialogBuilder<C> submit(final String name, final Class<R> form,
                                                      final BiConsumer<R, DialogClick<C>> handler) {
        return submit(name, form, handler, false);
    }

    /**
     * Like {@link #submit} for a handler that is slow (a database): it runs on an async thread, never on the
     * viewer's.
     *
     * @param name    the name used in the file
     * @param form    a public record
     * @param handler called with the answers on an async thread
     * @param <R>     the record
     * @return this
     * @throws IllegalArgumentException if the name is not valid or used by an action or submit already
     */
    public <R extends Record> DialogBuilder<C> submitAsync(final String name, final Class<R> form,
                                                           final BiConsumer<R, DialogClick<C>> handler) {
        return submit(name, form, handler, true);
    }

    private <R extends Record> DialogBuilder<C> submit(final String name, final Class<R> form,
                                                       final BiConsumer<R, DialogClick<C>> handler,
                                                       final boolean async) {
        ensureOpen();
        checkName("submit", name);
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(handler, "handler");
        checkFree(name);
        submits.put(name, new Registrations.Submit<>(form,
                (answers, click) -> handler.accept(form.cast(answers), click), async));
        return this;
    }

    /**
     * Registers where the initial value of an input comes from, e.g. the last price: a number for a number input, a
     * text, a boolean, or the id of an option.
     *
     * @param key     the key of the input in the file
     * @param initial called whenever the dialog is shown
     * @return this
     * @throws IllegalArgumentException if the key is not valid or registered already
     */
    public DialogBuilder<C> input(final String key, final Function<DialogView<C>, ?> initial) {
        ensureOpen();
        checkName("input", key);
        Objects.requireNonNull(initial, "initial");
        putNew(inputs, "input", key, initial);
        return this;
    }

    /**
     * Registers what happens when the viewer leaves with an exit button. Escape is not reported by the client for
     * notices and confirmations, so a dialog that must get an answer sets {@code can-close-with-escape: false}.
     *
     * @param handler called with the view that was left
     * @return this
     */
    public DialogBuilder<C> onClose(final Consumer<DialogView<C>> handler) {
        ensureOpen();
        this.onClose = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /**
     * Reads {@code dialogs/<id>.yml} (copying the bundled one first if the file is missing), checks it against what
     * was registered and logs what is wrong: an {@code action:} or placeholder that code did not register, a submit
     * form that does not match the inputs. A button that cannot work is inert; the dialog is still built.
     *
     * @return the dialog
     * @throws IllegalStateException if the service is closed, the id was built already, this builder was used already,
     *                               or there is no file and no bundled one
     */
    public DialogType<C> build() {
        ensureOpen();
        built = true;
        return registry.register(id, new Registrations<>(Map.copyOf(placeholders), Map.copyOf(items), Map.copyOf(actions),
                Map.copyOf(submits), Map.copyOf(inputs), onClose));
    }

    private void ensureOpen() {
        if (built) {
            throw new IllegalStateException("The builder of dialog '" + id + "' was used already");
        }
    }

    private static void checkName(final String what, final String name) {
        Objects.requireNonNull(name, what);
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("'" + name + "' is not a valid " + what
                    + " name (lower case letters, digits, - and _, at most 64)");
        }
    }

    private void checkFree(final String name) {
        if (actions.containsKey(name) || submits.containsKey(name)) {
            throw new IllegalArgumentException("action '" + name + "' is registered already");
        }
    }

    private static <V> void putNew(final Map<String, V> into, final String what, final String name, final V value) {
        if (into.putIfAbsent(name, value) != null) {
            throw new IllegalArgumentException(what + " '" + name + "' is registered already");
        }
    }
}
