package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.core.item.ItemBuilder;
import com.github.dewarepk.synthetixlib.core.item.Items;
import com.github.dewarepk.synthetixlib.core.text.TextService;
import com.github.dewarepk.synthetixlib.dialog.file.ClickAction;
import com.github.dewarepk.synthetixlib.dialog.file.DialogBody;
import com.github.dewarepk.synthetixlib.dialog.file.DialogButton;
import com.github.dewarepk.synthetixlib.dialog.file.DialogButtons;
import com.github.dewarepk.synthetixlib.dialog.file.DialogFile;
import com.github.dewarepk.synthetixlib.dialog.file.DialogInput;
import com.github.dewarepk.synthetixlib.dialog.file.DialogKind;
import com.github.dewarepk.synthetixlib.dialog.file.ItemSpec;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.set.RegistrySet;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Turns one dialog file into a Paper {@link Dialog} for one viewer: texts with the placeholders of that view, the
 * initial values of the inputs, and the buttons with fresh one-use callbacks. A new dialog is made for every showing and
 * never kept, so nothing of one viewer reaches another.
 *
 * <p>Placeholder values are components that are inserted as they are; they are never parsed again (no MiniMessage
 * injection from item names or player input). A tag that nothing registered is left as text by MiniMessage. The text
 * and {@code <lang>} handling is the gui renderer's, copied because the two modules do not depend on each other.
 *
 * <p>Paper's dialog API is experimental; every call to it is in this class.
 */
final class DialogRenderer<C> {

    /** What a renderer needs from the service. */
    record Env(TextService text, Items items, Supplier<@Nullable DialogMessages> messages,
               Supplier<DialogSettings> settings, Consumer<String> warn, Set<String> warned,
               Function<String, @Nullable DialogType<?>> lookup) {
    }

    private static final int DEFAULT_BUTTON_WIDTH = 150;
    private static final int DEFAULT_COLUMNS = 2;

    private final Env env;
    private final DialogType<C> type;
    private final DialogViewImpl<C> view;
    private final int generation;
    private final Set<String> visited;
    private final DialogLoader.Resolved resolved;
    private final Registrations<C> registered;
    private final @Nullable StaticKeys statics;
    private final Pass pass = new Pass();
    private boolean hasCallback;
    private boolean hasStatic;

    /**
     * @param generation the showing the callbacks belong to
     * @param visited    ids of the dialogs being rendered around this one (a list that lists itself)
     */
    DialogRenderer(final Env env, final DialogType<C> type, final DialogViewImpl<C> view, final int generation,
                   final Set<String> visited) {
        this(env, type, view, generation, visited, null);
    }

    /** @param statics set for a registry dialog: buttons carry custom click keys instead of callbacks */
    DialogRenderer(final Env env, final DialogType<C> type, final DialogViewImpl<C> view, final int generation,
                   final Set<String> visited, final @Nullable StaticKeys statics) {
        this.statics = statics;
        this.env = env;
        this.type = type;
        this.view = view;
        this.generation = generation;
        this.visited = visited;
        this.resolved = view.resolved();
        this.registered = type.registrations();
    }

    /** @return MiniMessage text with the placeholders of this view (a {@code message:} step) */
    Component parse(final String markup) {
        return pass.parse(markup);
    }

    /**
     * How a registry dialog is rendered: the keys it may use.
     *
     * @param namespace   namespace of the registry key of the dialog
     * @param path        path of the registry key; a button is the custom click key {@code namespace:path/<button>}
     * @param dialogKeys  registry key of a dialog of the same bootstrap by file id, {@code null} if none
     */
    record StaticKeys(String namespace, String path, Function<String, @Nullable TypedKey<Dialog>> dialogKeys) {
    }

    /** The two halves of a Paper dialog: what a registry entry takes. */
    record Parts(DialogBase base, io.papermc.paper.registry.data.dialog.type.DialogType kind) {
    }

    /** @return the dialog; throws if the Paper API refuses what the file describes */
    Dialog render() {
        final Parts parts = parts();
        return Dialog.create(factory -> factory.empty().base(parts.base()).type(parts.kind()));
    }

    /** @return base and type of the dialog; throws if the Paper API refuses what the file describes */
    Parts parts() {
        final DialogFile file = resolved.file();
        view.clearCallbacks();
        final Set<String> inside = new LinkedHashSet<>(visited);
        inside.add(type.id());

        final List<io.papermc.paper.registry.data.dialog.body.DialogBody> body = new ArrayList<>();
        final List<String> errorTexts = new ArrayList<>();
        for (final FormSubmit.Failure failure : view.failures()) {
            final Component line = errorLine(failure);
            errorTexts.add(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line));
            body.add(io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(line));
        }
        view.errorTexts(errorTexts);
        for (final DialogBody entry : file.body()) {
            body.add(body(entry));
        }
        final InputLimits limits = statics != null ? InputLimits.file()
                : InputLimits.resolve(type.id(), file.inputs(), registered.ranges(), registered.maxLengths(),
                registered.decimals(), view, env.warn());
        view.limits(limits);
        final List<io.papermc.paper.registry.data.dialog.input.DialogInput> inputs = new ArrayList<>();
        file.inputs().forEach((key, input) -> inputs.add(input(key, input, limits)));

        final io.papermc.paper.registry.data.dialog.type.DialogType kind = kind(file, inside);
        final DialogBase.DialogAfterAction after = !hasCallback ? DialogBase.DialogAfterAction.CLOSE
                : hasStatic ? DialogBase.DialogAfterAction.NONE : DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE;
        final DialogBase.Builder base = DialogBase.builder(pass.parse(file.title()))
                .canCloseWithEscape(file.canCloseWithEscape())
                .pause(file.pause())
                .afterAction(after)
                .body(body)
                .inputs(inputs);
        file.externalTitle().ifPresent(title -> base.externalTitle(pass.parse(title)));
        return new Parts(base.build(), kind);
    }

    /**
     * The line above the body for a refused input: the {@code errors.<key>} line of the file, else the language message
     * {@code invalid-decimals} (too many decimals only) or {@code invalid-input}, else English.
     * {@code <min> <max> <value> <decimals>} are plain text, never parsed.
     */
    private Component errorLine(final FormSubmit.Failure failure) {
        final TagResolver values = TagResolver.resolver(Placeholder.unparsed("min", failure.min()),
                Placeholder.unparsed("max", failure.max()), Placeholder.unparsed("value", failure.value()),
                Placeholder.unparsed("decimals", failure.decimals()));
        if (failure.markup() != null) {
            return env.text().parse(failure.markup(), values, pass.withExtra(TagResolver.resolver(values, failure.extra())),
                    failure.extra());
        }
        final DialogMessages source = env.messages().get();
        final TagResolver nested = TagResolver.resolver(values, pass.placeholders());
        Component message = null;
        if (source != null && failure.tooManyDecimals()) {
            message = source.get(view.playerOrNull(), "invalid-decimals", nested);
        }
        if (message == null && source != null) {
            message = source.get(view.playerOrNull(), "invalid-input", nested);
        }
        if (message != null) {
            return message;
        }
        if (failure.tooManyDecimals()) {
            return Component.text("At most " + failure.decimals() + " decimals: " + failure.key(), NamedTextColor.RED);
        }
        return Component.text(failure.key().isEmpty() ? "Invalid value" : "Invalid value: " + failure.key(),
                NamedTextColor.RED);
    }

    // --- type and buttons ---------------------------------------------------------------------------------------

    private io.papermc.paper.registry.data.dialog.type.DialogType kind(final DialogFile file,
                                                                       final Set<String> inside) {
        final DialogButtons buttons = file.buttons();
        final ActionButton exit = buttons.exit().map(button -> button("buttons.exit", button)).orElse(null);
        final int columns = buttons.columns().orElse(DEFAULT_COLUMNS);
        final DialogKind dialogKind = file.kind();
        switch (dialogKind) {
            case NOTICE -> {
                final DialogButton ok = buttons.named().get("ok");
                return ok == null ? io.papermc.paper.registry.data.dialog.type.DialogType.notice()
                        : io.papermc.paper.registry.data.dialog.type.DialogType.notice(button("buttons.ok", ok));
            }
            case CONFIRMATION -> {
                return io.papermc.paper.registry.data.dialog.type.DialogType.confirmation(
                        button("buttons.yes", buttons.named().get("yes")),
                        button("buttons.no", buttons.named().get("no")));
            }
            case MULTI_ACTION -> {
                final List<ActionButton> actions = new ArrayList<>();
                buttons.named().forEach((name, button) -> actions.add(button("buttons." + name, button)));
                if (actions.isEmpty()) {
                    return io.papermc.paper.registry.data.dialog.type.DialogType.notice();
                }
                final var builder = io.papermc.paper.registry.data.dialog.type.DialogType.multiAction(actions)
                        .columns(columns);
                if (exit != null) {
                    builder.exitAction(exit);
                }
                return builder.build();
            }
            case LIST -> {
                if (statics != null) {
                    return staticList(buttons, columns, exit);
                }
                final List<Dialog> nested = new ArrayList<>();
                for (final String id : buttons.dialogs()) {
                    final Dialog dialog = nested(id, inside);
                    if (dialog != null) {
                        nested.add(dialog);
                    }
                }
                if (nested.isEmpty()) {
                    return io.papermc.paper.registry.data.dialog.type.DialogType.notice();
                }
                final var builder = io.papermc.paper.registry.data.dialog.type.DialogType
                        .dialogList(RegistrySet.valueSet(RegistryKey.DIALOG, nested))
                        .columns(columns)
                        .buttonWidth(buttons.buttonWidth().orElse(DEFAULT_BUTTON_WIDTH));
                if (exit != null) {
                    builder.exitAction(exit);
                }
                return builder.build();
            }
            case LINKS -> {
                return io.papermc.paper.registry.data.dialog.type.DialogType.serverLinks(exit, columns,
                        buttons.buttonWidth().orElse(DEFAULT_BUTTON_WIDTH));
            }
        }
        throw new IllegalStateException("unreachable: " + dialogKind);
    }

    /** A list in a registry dialog links to the dialogs of the same bootstrap, by their registry keys. */
    private io.papermc.paper.registry.data.dialog.type.DialogType staticList(final DialogButtons buttons,
                                                                             final int columns,
                                                                             final @Nullable ActionButton exit) {
        final List<TypedKey<Dialog>> keys = new ArrayList<>();
        for (final String id : buttons.dialogs()) {
            final TypedKey<Dialog> key = statics.dialogKeys().apply(id);
            if (key == null) {
                env.warn().accept("[dialogs/" + type.id() + ".yml] buttons.dialogs: the dialog '" + id
                        + "' is not registered in the same bootstrap, left out");
            } else {
                keys.add(key);
            }
        }
        if (keys.isEmpty()) {
            return io.papermc.paper.registry.data.dialog.type.DialogType.notice();
        }
        final var builder = io.papermc.paper.registry.data.dialog.type.DialogType
                .dialogList(RegistrySet.keySet(RegistryKey.DIALOG, keys))
                .columns(columns)
                .buttonWidth(buttons.buttonWidth().orElse(DEFAULT_BUTTON_WIDTH));
        if (exit != null) {
            builder.exitAction(exit);
        }
        return builder.build();
    }

    /** A dialog a list links to, rendered for the same viewer; it shares the one-use state of this screen. */
    private @Nullable Dialog nested(final String id, final Set<String> inside) {
        final DialogType<?> found = env.lookup().apply(id);
        if (found == null) {
            env.warn().accept("[dialogs/" + type.id() + ".yml] buttons.dialogs: the dialog '" + id
                    + "' was not built by this service, left out");
            return null;
        }
        if (inside.contains(id)) {
            env.warn().accept("[dialogs/" + type.id() + ".yml] buttons.dialogs: '" + id + "' would contain itself, left out");
            return null;
        }
        return renderNested(found, inside);
    }

    private <N> Dialog renderNested(final DialogType<N> found, final Set<String> inside) {
        final DialogViewImpl<N> child = new DialogViewImpl<>(view.manager(), found, view.playerOrNull(),
                view.playerName(), null, view);
        return new DialogRenderer<>(env, found, child, generation, inside).render();
    }

    private ActionButton button(final String path, final @Nullable DialogButton button) {
        if (button == null) {
            throw new IllegalStateException("the dialog has no " + path);
        }
        final boolean inert = resolved.inert().contains(path);
        final List<ClickAction> actions = inert ? List.of() : button.clicks().actions();
        final ActionButton.Builder builder = ActionButton.builder(pass.parse(button.label()))
                .width(button.width().orElse(DEFAULT_BUTTON_WIDTH));
        button.tooltip().ifPresent(tooltip -> builder.tooltip(pass.parse(tooltip)));

        final ClickAction single = actions.size() == 1 ? actions.get(0) : null;
        if (single instanceof ClickAction.Url url) {
            hasStatic = true;
            builder.action(DialogAction.staticAction(ClickEvent.openUrl(url.url().toString())));
        } else if (single instanceof ClickAction.Copy copy) {
            hasStatic = true;
            builder.action(DialogAction.staticAction(ClickEvent.copyToClipboard(copy.text())));
        } else if (statics != null) {
            hasCallback = true;
            builder.action(DialogAction.customClick(
                    Key.key(statics.namespace(), statics.path() + "/" + path.substring("buttons.".length())), null));
        } else {
            hasCallback = true;
            final boolean exit = isExit(resolved.file().kind(), path, actions);
            final var callback = view.callbackOf(generation, actions, exit);
            view.registerCallback(path, callback);
            builder.action(DialogAction.customClick(callback, ClickCallback.Options.builder().uses(1)
                    .lifetime(env.settings().get().callbackLifetime()).build()));
        }
        return builder.build();
    }

    /** Whether a button ends the dialog without an answer: the exit button, or a confirmation's no that only closes. */
    static boolean isExit(final DialogKind kind, final String path, final List<ClickAction> actions) {
        final boolean closesOnly = actions.stream().allMatch(action -> action instanceof ClickAction.Close);
        return path.equals("buttons.exit") || kind == DialogKind.CONFIRMATION && path.equals("buttons.no") && closesOnly;
    }

    // --- body ---------------------------------------------------------------------------------------------------

    private io.papermc.paper.registry.data.dialog.body.DialogBody body(final DialogBody entry) {
        return switch (entry) {
            case DialogBody.Message message -> message(message.text(), message.width().orElse(null));
            case DialogBody.Item item -> {
                final var builder = io.papermc.paper.registry.data.dialog.body.DialogBody.item(stack(item.item()))
                        .showDecorations(item.showDecorations())
                        .showTooltip(item.showTooltip());
                item.description().ifPresent(text -> builder.description(message(text, null)));
                item.width().ifPresent(builder::width);
                item.height().ifPresent(builder::height);
                yield builder.build();
            }
        };
    }

    private io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody message(final String text,
                                                                                     final @Nullable Integer width) {
        final Component parsed = pass.parse(text);
        return width == null ? io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(parsed)
                : io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(parsed, width);
    }

    /** The item of a body entry; a barrier stands in for one that is missing, because Paper refuses an empty stack. */
    private ItemStack stack(final ItemSpec spec) {
        ItemStack stack = null;
        try {
            if (spec.slot().isPresent()) {
                final Player viewer = view.playerOrNull();
                if (viewer != null) {
                    final ItemStack held = spec.slot().get() == ItemSpec.Hand.HAND
                            ? viewer.getInventory().getItemInMainHand() : viewer.getInventory().getItemInOffHand();
                    stack = held.isEmpty() ? null : held.clone();
                }
            } else if (spec.placeholder().isPresent()) {
                final var source = registered.items().get(spec.placeholder().get());
                stack = source == null ? null : source.apply(view);
                if (stack != null) {
                    stack = stack.clone();
                }
            } else {
                stack = configured(spec);
            }
        } catch (final RuntimeException e) {
            env.warn().accept("[dialogs/" + type.id() + ".yml] an item could not be built: " + e);
        }
        if (stack == null || stack.isEmpty()) {
            return new ItemStack(Material.BARRIER);
        }
        return stack;
    }

    private @Nullable ItemStack configured(final ItemSpec spec) {
        final Material material = spec.material().orElse(null);
        if (material == null || material.isAir() || !material.isItem()) {
            return null;
        }
        final ItemBuilder builder = env.items().of(material);
        spec.amount().ifPresent(amount -> builder.amount(Math.min(amount, material.getMaxStackSize())));
        spec.name().ifPresent(name -> builder.name(name, pass.resolver()));
        for (final String line : spec.lore()) {
            builder.lore(line, pass.resolver());
        }
        spec.itemModel().ifPresent(builder::itemModel);
        spec.glint().ifPresent(builder::glint);
        return builder.build();
    }

    // --- inputs -------------------------------------------------------------------------------------------------

    private io.papermc.paper.registry.data.dialog.input.DialogInput input(final String key, final DialogInput input,
                                                                          final InputLimits limits) {
        final String wire = InputKeys.wire(key);
        final Object initial = initialOf(key);
        final Component label = pass.parse(input.label());
        return switch (input) {
            case DialogInput.TextInput text -> {
                final int limit = limits.maxLength(key, text);
                final var builder = io.papermc.paper.registry.data.dialog.input.DialogInput.text(wire, label)
                        .maxLength(limit);
                text.width().ifPresent(builder::width);
                text.multiline().ifPresent(multiline -> builder.multiline(
                        TextDialogInput.MultilineOptions.create(multiline.maxLines().orElse(null),
                                multiline.height().orElse(null))));
                // plain text, never parsed: whatever the player typed is shown as typed
                final String start = initial instanceof String s ? s : text.initial().orElse(null);
                if (start != null) {
                    builder.initial(start.length() > limit ? start.substring(0, limit) : start);
                }
                yield builder.build();
            }
            case DialogInput.BoolInput bool -> io.papermc.paper.registry.data.dialog.input.DialogInput.bool(wire, label)
                    .initial(initial instanceof Boolean b ? b : bool.initial().orElse(false))
                    .build();
            case DialogInput.OptionInput option -> {
                final String chosen = initial instanceof String id && option.options().containsKey(id) ? id : null;
                final List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
                option.options().forEach((id, entry) -> entries.add(SingleOptionDialogInput.OptionEntry.create(id,
                        pass.parse(entry.display().orElse(id)), chosen != null ? id.equals(chosen) : entry.isInitial())));
                final var builder = io.papermc.paper.registry.data.dialog.input.DialogInput.singleOption(wire, label,
                        entries);
                option.width().ifPresent(builder::width);
                yield builder.build();
            }
            case DialogInput.NumberInput number -> {
                final InputLimits.Bounds bounds = limits.bounds(key, number);
                final var builder = io.papermc.paper.registry.data.dialog.input.DialogInput.numberRange(wire, label,
                        (float) bounds.min(), (float) bounds.max());
                number.width().ifPresent(builder::width);
                bounds.step().ifPresent(step -> builder.step((float) step));
                number.format().ifPresent(builder::labelFormat);
                final Double start = initial instanceof Number n ? Double.valueOf(n.doubleValue())
                        : number.initial().orElse(null);
                if (start != null && !start.isNaN()) {
                    builder.initial((float) Math.max(bounds.min(), Math.min(bounds.max(), start)));
                }
                yield builder.build();
            }
            case DialogInput.NumberFieldInput field -> {
                // a text input to the client; the server reads the text as a number (FormValidator)
                final int limit = limits.maxLength(key, field);
                final InputLimits.Bounds bounds = limits.bounds(key, field);
                final var builder = io.papermc.paper.registry.data.dialog.input.DialogInput.text(wire, label)
                        .maxLength(limit);
                field.width().ifPresent(builder::width);
                final String start = initial instanceof String typed ? typed
                        : initial instanceof Number n ? plain(n, field.isInteger(), bounds)
                        : field.initial().map(value -> plain(value, field.isInteger(), bounds)).orElse(null);
                if (start != null) {
                    builder.initial(start.length() > limit ? start.substring(0, limit) : start);
                }
                yield builder.build();
            }
        };
    }

    /** A number as the text a number-field starts with: no exponent, a whole number without {@code .0}, in the range. */
    private static String plain(final Number number, final boolean integer, final InputLimits.Bounds bounds) {
        if (number instanceof Long || number instanceof Integer) {
            return Long.toString(number.longValue());
        }
        if (number instanceof BigDecimal big) {
            return big.stripTrailingZeros().toPlainString();
        }
        final double value = number.doubleValue();
        if (Double.isNaN(value)) {
            return "";
        }
        return FormSubmit.format(Math.max(bounds.min(), Math.min(bounds.max(), integer ? Math.rint(value) : value)));
    }

    /** The value an input starts with when code or the last click chose one: typed values win over code. */
    private @Nullable Object initialOf(final String key) {
        final Object typed = view.answers().get(key);
        if (typed != null) {
            return typed;
        }
        final var source = registered.inputs().get(key);
        if (source == null) {
            return null;
        }
        try {
            return source.apply(view);
        } catch (final RuntimeException e) {
            env.warn().accept("[dialogs/" + type.id() + ".yml] the initial value of input '" + key + "' threw " + e);
            return null;
        }
    }

    // --- text ---------------------------------------------------------------------------------------------------

    /** The tag of a message the language files do not know: MiniMessage's own {@code <lang>}, a translatable. */
    private Component translatable(final String key, final ArgumentQueue arguments) {
        final List<Component> args = new ArrayList<>();
        while (arguments.hasNext()) {
            args.add(Component.text(arguments.pop().value()));
        }
        if (!isVanillaKey(key) && env.warned().add(type.id() + "|" + key)) {
            env.warn().accept("[dialogs/" + type.id() + ".yml] <lang:" + key + "> is not in the language files");
        }
        return Component.translatable(key, args);
    }

    static boolean isVanillaKey(final String key) {
        final int dot = key.indexOf('.');
        if (dot < 0) {
            return false;
        }
        return switch (key.substring(0, dot)) {
            case "block", "item", "entity", "effect", "enchantment", "biome", "container", "gui", "key",
                    "options", "attribute", "advancements", "stat" -> true;
            default -> false;
        };
    }

    /** The placeholders of one rendering: {@code <player>} and the registered ones, each value asked once. */
    private final class Pass {

        private final Map<String, Component> values = new HashMap<>();
        /** The placeholders alone: what a language file message may use (no {@code <lang>} inside a message). */
        private final TagResolver placeholders = new TagResolver() {
            @Override
            public @Nullable Tag resolve(final String name, final ArgumentQueue arguments, final Context ctx) {
                final Component value = value(name);
                return value == null ? null : Tag.selfClosingInserting(value);
            }

            @Override
            public boolean has(final String name) {
                return Registrations.isBuiltInPlaceholder(name) || registered.placeholders().containsKey(name);
            }
        };
        /** The placeholders and {@code <lang:key>}, which reads the viewer's language file. */
        private final TagResolver resolver = withExtra(TagResolver.empty());

        /** Like {@link #resolver()}, but a {@code <lang:key>} message may also use {@code extra} (an error line's values). */
        TagResolver withExtra(final TagResolver extra) {
            final TagResolver inner = TagResolver.resolver(placeholders, extra);
            return TagResolver.resolver(placeholders, new TagResolver() {
                @Override
                public @Nullable Tag resolve(final String name, final ArgumentQueue arguments, final Context ctx) {
                    final DialogMessages source = env.messages().get();
                    if (source == null || !name.equals("lang") || !arguments.hasNext()) {
                        return null; // no language files: MiniMessage's own <lang> (a translatable) stays
                    }
                    final String key = arguments.pop().value();
                    final Component message = source.get(view.playerOrNull(), key, inner);
                    return Tag.selfClosingInserting(message != null ? message : translatable(key, arguments));
                }

                @Override
                public boolean has(final String name) {
                    return name.equals("lang") && env.messages().get() != null;
                }
            });
        }

        TagResolver placeholders() {
            return placeholders;
        }

        TagResolver resolver() {
            return resolver;
        }

        Component parse(final String markup) {
            return env.text().parse(markup, resolver);
        }

        private @Nullable Component value(final String name) {
            if (name.equals("player")) {
                return Component.text(view.playerName());
            }
            final var function = registered.placeholders().get(name);
            if (function == null) {
                return null;
            }
            return values.computeIfAbsent(name, key -> {
                try {
                    final Component value = function.apply(view);
                    return value == null ? Component.empty() : value;
                } catch (final RuntimeException e) {
                    env.warn().accept("[dialogs/" + type.id() + ".yml] placeholder <" + key + "> threw " + e);
                    return Component.empty();
                }
            });
        }
    }
}
