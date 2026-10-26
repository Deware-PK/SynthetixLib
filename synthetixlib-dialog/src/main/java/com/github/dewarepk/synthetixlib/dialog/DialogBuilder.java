package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.dialog.file.ClickAction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
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

    private final Registrar registry;
    private final String id;
    private final Map<String, Function<DialogView<C>, Component>> placeholders = new LinkedHashMap<>();
    private final Map<String, Function<DialogView<C>, ItemStack>> items = new LinkedHashMap<>();
    private final Map<String, Consumer<DialogClick<C>>> actions = new LinkedHashMap<>();
    private final Map<String, Registrations.Submit<C>> submits = new LinkedHashMap<>();
    private final Map<String, Function<DialogView<C>, ?>> inputs = new LinkedHashMap<>();
    private final Map<String, Function<DialogView<C>, Range>> ranges = new LinkedHashMap<>();
    private final Map<String, Function<DialogView<C>, Integer>> maxLengths = new LinkedHashMap<>();
    private final Map<String, Function<DialogView<C>, Integer>> decimalLimits = new LinkedHashMap<>();
    private final Map<String, Registrations.Validator<C>> validators = new LinkedHashMap<>();
    private @Nullable Consumer<DialogView<C>> onClose;
    private boolean built;

    /** Where build() puts the dialog: the service, or the registry dialogs of its bootstrap. */
    interface Registrar {
        <C> DialogType<C> register(String id, Registrations<C> registrations);
    }

    DialogBuilder(final Registrar registry, final String id) {
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
                || !ranges.isEmpty() || !maxLengths.isEmpty() || !decimalLimits.isEmpty() || !validators.isEmpty() || onClose != null) {
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
     * Sets the limits of a number input from code for every showing, e.g. a price range from {@code config.yml}. Asked
     * when the dialog is rendered, and the answer is checked against the same values the player saw (they are kept in
     * the view). Replaces {@code min} / {@code max} (and {@code step} of a slider if the range has one) of the file;
     * the file is the fallback if the function throws. {@code <min>} and {@code <max>} of an error line are these
     * values.
     *
     * <pre>{@code
     * dialogs.dialog("sell").inputRange("price", view -> Range.of(settings.minPrice(), settings.maxPrice()))
     * }</pre>
     *
     * @param key   the key of a {@code number} or {@code number-field} input
     * @param range called whenever the dialog is shown
     * @return this
     * @throws IllegalArgumentException if the key is not valid or registered already
     */
    public DialogBuilder<C> inputRange(final String key, final Function<DialogView<C>, Range> range) {
        ensureOpen();
        checkName("inputRange", key);
        Objects.requireNonNull(range, "range");
        putNew(ranges, "inputRange", key, range);
        return this;
    }

    /**
     * Sets the most digits after the dot of a {@code format: decimal} {@code number-field} from code for every showing,
     * e.g. the decimals of the server's currency. Asked when the dialog is rendered and the answer is checked against the
     * same value (it is kept in the view). {@code 12.50} is fine with 2, {@code 12.505} is refused; {@code 0} means
     * whole numbers that are still a {@code BigDecimal}. The file's {@code decimals} is the fallback if the function
     * throws or answers outside 0..10. {@code <decimals>} of an error line is this value.
     *
     * <pre>{@code
     * dialogs.dialog("sell").inputDecimals("price", view -> settings.get().currencyDecimals())
     * }</pre>
     *
     * @param key      the key of a decimal {@code number-field} input
     * @param decimals called whenever the dialog is shown; 0 to 10
     * @return this
     * @throws IllegalArgumentException if the key is not valid or registered already
     */
    public DialogBuilder<C> inputDecimals(final String key, final Function<DialogView<C>, Integer> decimals) {
        ensureOpen();
        checkName("inputDecimals", key);
        Objects.requireNonNull(decimals, "decimals");
        putNew(decimalLimits, "inputDecimals", key, decimals);
        return this;
    }

    /**
     * Sets the {@code max-length} of a {@code text} (or {@code number-field}) input from code for every showing. Asked
     * when the dialog is rendered and the answer is checked against the same value. The file is the fallback.
     *
     * <pre>{@code
     * dialogs.dialog("note").maxLength("note", view -> settings.maxNoteLength())
     * }</pre>
     *
     * @param key    the key of the input
     * @param length called whenever the dialog is shown; at least 1
     * @return this
     * @throws IllegalArgumentException if the key is not valid or registered already
     */
    public DialogBuilder<C> maxLength(final String key, final Function<DialogView<C>, Integer> length) {
        ensureOpen();
        checkName("maxLength", key);
        Objects.requireNonNull(length, "length");
        putNew(maxLengths, "maxLength", key, length);
        return this;
    }

    /**
     * Checks the record of a {@link #submit} / {@link #submitAsync} in code, after the limits of the file and the
     * reading of the record and before the handler. A non-empty answer shows the dialog again like a limit of the file
     * would: the error line above the body, what the player typed kept, fresh buttons; the handler is not called. Runs
     * on the viewer's thread, so it may read the world; do not make it slow.
     *
     * <pre>{@code
     * dialogs.dialog("sell").<ItemStack>withContext()
     *         .submit("sell", SellForm.class, (form, click) -> auctions.list(click.player(), form))
     *         .validate("sell", SellForm.class, (form, click) -> form.price() > limit
     *                 ? Optional.of(FieldError.of("price", "<red>At most <limit>", Placeholder.unparsed("limit", "500")))
     *                 : Optional.empty());
     * }</pre>
     *
     * @param name      the name of a submit (register it first)
     * @param form      the record of that submit (lets Java type the lambda)
     * @param validator called with the record, the error to show or empty
     * @param <R>       the record of the submit
     * @return this
     * @throws IllegalArgumentException if no submit has this name, it reads another record, or it has a validator already
     */
    public <R extends Record> DialogBuilder<C> validate(final String name, final Class<R> form,
                                                        final BiFunction<R, DialogClick<C>, Optional<FieldError>> validator) {
        Objects.requireNonNull(validator, "validator");
        return validateAll(name, form, (parsed, click) -> validator.apply(parsed, click).map(List::of)
                .orElse(List.of()));
    }

    /**
     * Like {@link #validate} for a check that reports several problems at once; each error is a line above the body.
     * (A different name because two lambdas that differ only in what they return cannot be told apart by Java.)
     *
     * @param name      the name of a submit (register it first)
     * @param form      the record of that submit
     * @param validator called with the record, the errors to show or an empty list
     * @param <R>       the record of the submit
     * @return this
     * @throws IllegalArgumentException if no submit has this name, it reads another record, or it has a validator already
     */
    public <R extends Record> DialogBuilder<C> validateAll(final String name, final Class<R> form,
                                                           final BiFunction<R, DialogClick<C>, List<FieldError>> validator) {
        ensureOpen();
        checkName("validate", name);
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(validator, "validator");
        final Registrations.Submit<C> submit = submits.get(name);
        if (submit == null) {
            throw new IllegalArgumentException("validate('" + name + "'): register submit('" + name + "') first");
        }
        if (submit.form() != form) {
            throw new IllegalArgumentException("validate('" + name + "'): the submit reads " + submit.form().getSimpleName()
                    + ", not " + form.getSimpleName());
        }
        putNew(validators, "validate", name, new Registrations.Validator<C>(
                (parsed, click) -> validator.apply(form.cast(parsed), click)));
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
                Map.copyOf(submits), Map.copyOf(inputs), onClose, Map.copyOf(ranges), Map.copyOf(maxLengths),
                Map.copyOf(decimalLimits), Map.copyOf(validators)));
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
