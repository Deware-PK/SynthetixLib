package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.config.codec.DecodeContext;
import com.github.dewarepk.synthetixlib.config.codec.DecodeException;
import com.github.dewarepk.synthetixlib.config.codec.Problem;
import com.github.dewarepk.synthetixlib.config.codec.TypeToken;
import com.github.dewarepk.synthetixlib.config.yaml.YamlTrees;
import com.github.dewarepk.synthetixlib.dialog.file.BodyEntry;
import com.github.dewarepk.synthetixlib.dialog.file.ClickAction;
import com.github.dewarepk.synthetixlib.dialog.file.DialogBody;
import com.github.dewarepk.synthetixlib.dialog.file.DialogButton;
import com.github.dewarepk.synthetixlib.dialog.file.DialogButtons;
import com.github.dewarepk.synthetixlib.dialog.file.DialogFile;
import com.github.dewarepk.synthetixlib.dialog.file.DialogInput;
import com.github.dewarepk.synthetixlib.dialog.file.DialogKind;
import com.github.dewarepk.synthetixlib.dialog.file.ItemSpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.configuration.InvalidConfigurationException;
import org.jspecify.annotations.Nullable;

/**
 * Turns the text of a dialog file into a {@link DialogFile} using the config codecs. Everything wrong is reported and
 * replaced by something safe, so a damaged file still gives a dialog: an element that cannot be read is dropped (a
 * body entry, an input, a button, which then falls back to the default one where the kind needs it), a value out of
 * range is clamped. Only text that is not a YAML section gives no dialog. Unknown keys are errors, like every other
 * mistake in the file.
 */
final class DialogParser {

    /**
     * @param file     the dialog, or {@code null} if the text is not a YAML section
     * @param errors   what was wrong
     * @param warnings things that change nothing
     */
    record Parsed(@Nullable DialogFile file, List<Problem> errors, List<Problem> warnings) {
    }

    static final int MAX_WIDTH = 1024;
    static final int MAX_ITEM_SIZE = 256;
    /** What the client uses when a text input has no {@code max-length}. */
    static final int DEFAULT_TEXT_LENGTH = 32;
    /** Most digits after the dot a {@code number-field} may be limited to. */
    static final int MAX_DECIMALS = 10;

    /** Most characters a {@code number-field} takes when the file gives no {@code max-length}. */
    static final int DEFAULT_NUMBER_LENGTH = 18;

    private static final Pattern NAME = Pattern.compile(ClickAction.NAME_PATTERN);
    private static final String VERSION_KEY = "config-version";

    private final Codecs codecs;

    DialogParser(final Codecs codecs) {
        this.codecs = codecs;
    }

    Parsed parse(final String text) {
        final List<Problem> errors = new ArrayList<>();
        final List<Problem> warnings = new ArrayList<>();
        final Map<String, Object> data;
        try {
            data = YamlTrees.toPlain(YamlTrees.parse(text));
        } catch (final InvalidConfigurationException e) {
            errors.add(new Problem("", "invalid YAML, the file is not used: " + firstLine(e.getMessage())));
            return new Parsed(null, errors, warnings);
        }
        final DecodeContext ctx = codecs.newContext();
        for (final String key : data.keySet()) {
            switch (key) {
                case VERSION_KEY, "title", "external-title", "type", "can-close-with-escape", "pause", "body",
                     "inputs", "buttons", "errors" -> { }
                default -> errors.add(new Problem(key, "unknown key"));
            }
        }

        String title = read(data, "title", String.class, null, ctx, errors);
        if (title == null) {
            if (!data.containsKey("title")) {
                errors.add(new Problem("title", "missing"));
            }
            title = "";
        }
        final Optional<String> externalTitle = Optional.ofNullable(
                read(data, "external-title", String.class, null, ctx, errors));
        DialogKind kind = read(data, "type", DialogKind.class, null, ctx, errors);
        if (kind == null) {
            if (!data.containsKey("type")) {
                errors.add(new Problem("type", "missing, using notice"));
            } else {
                errors.add(new Problem("type", "using notice"));
            }
            kind = DialogKind.NOTICE;
        }
        final boolean escape = read(data, "can-close-with-escape", Boolean.class, Boolean.TRUE, ctx, errors);
        final boolean pause = read(data, "pause", Boolean.class, Boolean.FALSE, ctx, errors);

        final List<DialogBody> body = body(data, ctx, errors);
        final Map<String, DialogInput> inputs = inputs(data, ctx, errors);
        final DialogButtons buttons = buttons(kind, data, ctx, errors);
        final Map<String, String> messages = errorLines(data, ctx, inputs, errors);

        errors.addAll(ctx.warnings());
        return new Parsed(new DialogFile(title, externalTitle, kind, escape, pause, body, inputs, buttons, messages),
                errors, warnings);
    }

    // --- top level keys -----------------------------------------------------------------------------------------

    private <T> @Nullable T read(final Map<String, Object> data, final String key, final Class<T> type,
                                 final @Nullable T fallback, final DecodeContext ctx, final List<Problem> errors) {
        final Object raw = data.get(key);
        if (raw == null) {
            return fallback;
        }
        return decode(raw, type, ctx.at(key), errors, fallback);
    }

    private <T> @Nullable T decode(final Object raw, final Class<T> type, final DecodeContext at,
                                   final List<Problem> errors, final @Nullable T fallback) {
        try {
            return codecs.decode(raw, type, at);
        } catch (final DecodeException e) {
            errors.addAll(e.problems());
            return fallback;
        }
    }

    // --- body ---------------------------------------------------------------------------------------------------

    private List<DialogBody> body(final Map<String, Object> data, final DecodeContext ctx, final List<Problem> errors) {
        final Object raw = data.get("body");
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            errors.add(new Problem("body", "expected a list of entries, got " + raw.getClass().getSimpleName()));
            return List.of();
        }
        final List<DialogBody> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            final String path = "body[" + i + "]";
            final Object entry = list.get(i);
            if (entry == null) {
                errors.add(new Problem(path, "must not be empty, the entry is dropped"));
                continue;
            }
            final BodyEntry decoded = decode(entry, BodyEntry.class, ctx.at("body").at(i), errors, null);
            if (decoded == null) {
                continue;
            }
            final DialogBody parsed = bodyEntry(decoded, path, errors);
            if (parsed != null) {
                out.add(parsed);
            }
        }
        return out;
    }

    private @Nullable DialogBody bodyEntry(final BodyEntry entry, final String path, final List<Problem> errors) {
        if (entry.message().isPresent() == entry.item().isPresent()) {
            errors.add(new Problem(path, (entry.message().isPresent() ? "message and item cannot be used together"
                    : "needs a message or an item") + ", the entry is dropped"));
            return null;
        }
        if (entry.message().isPresent()) {
            final List<String> ignored = new ArrayList<>();
            addIf(ignored, "description", entry.description().isPresent());
            addIf(ignored, "show-decorations", entry.showDecorations().isPresent());
            addIf(ignored, "show-tooltip", entry.showTooltip().isPresent());
            addIf(ignored, "height", entry.height().isPresent());
            if (!ignored.isEmpty()) {
                errors.add(new Problem(path, "a message has no " + String.join(", ", ignored) + ", ignored"));
            }
            return new DialogBody.Message(entry.message().get(), range(entry.width(), 1, MAX_WIDTH, path + ".width",
                    errors));
        }
        final ItemSpec item = entry.item().get();
        if (item.sources() != 1) {
            errors.add(new Problem(path + ".item", (item.sources() == 0
                    ? "needs one of slot, placeholder or material" : "slot, placeholder and material exclude each other")
                    + ", the entry is dropped"));
            return null;
        }
        if (item.material().isEmpty() && item.hasMaterialKeys()) {
            errors.add(new Problem(path + ".item", "amount, name, lore, item-model and glint belong to an item with"
                    + " material, ignored"));
        }
        if (item.placeholder().isPresent() && !NAME.matcher(item.placeholder().get()).matches()) {
            errors.add(new Problem(path + ".item.placeholder", "'" + item.placeholder().get()
                    + "' is not a name (lower case letters, digits, - and _, at most 64), the entry is dropped"));
            return null;
        }
        return new DialogBody.Item(item, entry.description(), entry.showDecorations().orElse(true),
                entry.showTooltip().orElse(true), range(entry.width(), 1, MAX_ITEM_SIZE, path + ".width", errors),
                range(entry.height(), 1, MAX_ITEM_SIZE, path + ".height", errors));
    }

    private static void addIf(final List<String> out, final String name, final boolean condition) {
        if (condition) {
            out.add(name);
        }
    }

    // --- inputs -------------------------------------------------------------------------------------------------

    private Map<String, DialogInput> inputs(final Map<String, Object> data, final DecodeContext ctx,
                                            final List<Problem> errors) {
        final Map<String, DialogInput> out = new LinkedHashMap<>();
        final Object raw = data.get("inputs");
        if (raw == null) {
            return out;
        }
        if (!(raw instanceof Map<?, ?> section)) {
            errors.add(new Problem("inputs", "expected a section, got " + raw.getClass().getSimpleName()));
            return out;
        }
        final Map<String, String> wireKeys = new HashMap<>();
        for (final Map.Entry<?, ?> entry : section.entrySet()) {
            final String key = String.valueOf(entry.getKey());
            final String path = "inputs." + key;
            if (!NAME.matcher(key).matches()) {
                errors.add(new Problem(path, "an input key uses lower case letters, digits, - and _ (at most 64),"
                        + " the input is dropped"));
                continue;
            }
            final String other = wireKeys.get(InputKeys.wire(key));
            if (other != null) {
                errors.add(new Problem(path, "the client gets '-' as '_' in input keys, so this key is the same as '"
                        + other + "', the input is dropped"));
                continue;
            }
            if (entry.getValue() == null) {
                errors.add(new Problem(path, "must not be empty, the input is dropped"));
                continue;
            }
            final DialogInput decoded = decode(entry.getValue(), DialogInput.class, ctx.at("inputs").at(key), errors,
                    null);
            if (decoded == null) {
                errors.add(new Problem(path, "the input is dropped"));
                continue;
            }
            final DialogInput fixed = input(decoded, path, errors);
            if (fixed != null) {
                out.put(key, fixed);
                wireKeys.put(InputKeys.wire(key), key);
            }
        }
        return out;
    }

    /** @return the input with its values in range, or {@code null} if it cannot be repaired */
    private @Nullable DialogInput input(final DialogInput input, final String path, final List<Problem> errors) {
        return switch (input) {
            case DialogInput.TextInput text -> text(text, path, errors);
            case DialogInput.BoolInput bool -> bool;
            case DialogInput.OptionInput option -> option(option, path, errors);
            case DialogInput.NumberInput number -> number(number, path, errors);
            case DialogInput.NumberFieldInput field -> numberField(field, path, errors);
        };
    }

    private DialogInput text(final DialogInput.TextInput text, final String path, final List<Problem> errors) {
        final Optional<Integer> maxLength = range(text.maxLength(), 1, Integer.MAX_VALUE, path + ".max-length", errors);
        final int limit = maxLength.orElse(DEFAULT_TEXT_LENGTH);
        Optional<String> initial = text.initial();
        if (initial.isPresent() && initial.get().length() > limit) {
            errors.add(new Problem(path + ".initial", "is longer than max-length " + limit + " (was "
                    + initial.get().length() + " characters), cut"));
            initial = Optional.of(initial.get().substring(0, limit));
        }
        Optional<DialogInput.Multiline> multiline = text.multiline();
        if (multiline.isPresent()) {
            multiline = Optional.of(new DialogInput.Multiline(
                    range(multiline.get().maxLines(), 1, Integer.MAX_VALUE, path + ".multiline.max-lines", errors),
                    range(multiline.get().height(), 1, Integer.MAX_VALUE, path + ".multiline.height", errors)));
        }
        return new DialogInput.TextInput(text.label(), range(text.width(), 1, MAX_WIDTH, path + ".width", errors),
                initial, maxLength, multiline);
    }

    private @Nullable DialogInput option(final DialogInput.OptionInput option, final String path,
                                         final List<Problem> errors) {
        if (option.options().isEmpty()) {
            errors.add(new Problem(path + ".options", "needs at least one option, the input is dropped"));
            return null;
        }
        final Map<String, DialogInput.OptionEntry> fixed = new LinkedHashMap<>();
        String initial = null;
        for (final Map.Entry<String, DialogInput.OptionEntry> entry : option.options().entrySet()) {
            DialogInput.OptionEntry value = entry.getValue();
            if (value.isInitial()) {
                if (initial == null) {
                    initial = entry.getKey();
                } else {
                    errors.add(new Problem(path + ".options." + entry.getKey() + ".initial", "'" + initial
                            + "' is the initial option already, ignored"));
                    value = new DialogInput.OptionEntry(value.display(), Optional.empty());
                }
            }
            fixed.put(entry.getKey(), value);
        }
        return new DialogInput.OptionInput(option.label(), range(option.width(), 1, MAX_WIDTH, path + ".width",
                errors), fixed);
    }

    private @Nullable DialogInput number(final DialogInput.NumberInput number, final String path,
                                         final List<Problem> errors) {
        if (!Double.isFinite(number.min()) || !Double.isFinite(number.max()) || number.min() >= number.max()) {
            errors.add(new Problem(path, "min must be less than max (was " + number.min() + " and " + number.max()
                    + "), the input is dropped"));
            return null;
        }
        Optional<Double> step = number.step();
        if (step.isPresent() && !(step.get() > 0 && Double.isFinite(step.get()))) {
            errors.add(new Problem(path + ".step", "must be greater than 0 (was " + step.get() + "), ignored"));
            step = Optional.empty();
        }
        Optional<Double> initial = number.initial();
        if (initial.isPresent()) {
            final double value = initial.get();
            if (!(value >= number.min() && value <= number.max())) {
                final double clamped = Double.isNaN(value) ? number.min() : Math.max(number.min(),
                        Math.min(number.max(), value));
                errors.add(new Problem(path + ".initial", "must be between " + number.min() + " and " + number.max()
                        + " (was " + value + "), using " + clamped));
                initial = Optional.of(clamped);
            }
        }
        return new DialogInput.NumberInput(number.label(), range(number.width(), 1, MAX_WIDTH, path + ".width",
                errors), number.min(), number.max(), step, initial, number.format());
    }

    private @Nullable DialogInput numberField(final DialogInput.NumberFieldInput field, final String path,
                                              final List<Problem> errors) {
        Optional<Double> min = finite(field.min(), path + ".min", errors);
        Optional<Double> max = finite(field.max(), path + ".max", errors);
        if (min.isPresent() && max.isPresent() && min.get() > max.get()) {
            errors.add(new Problem(path, "min must not be greater than max (was " + min.get() + " and " + max.get()
                    + "), the input is dropped"));
            return null;
        }
        final Optional<Integer> maxLength = range(field.maxLength(), 1, Integer.MAX_VALUE, path + ".max-length", errors);
        Optional<Double> initial = finite(field.initial(), path + ".initial", errors);
        if (initial.isPresent()) {
            final double value = initial.get();
            final double low = min.orElse(Double.NEGATIVE_INFINITY);
            final double high = max.orElse(Double.POSITIVE_INFINITY);
            if (value < low || value > high) {
                final double clamped = Math.max(low, Math.min(high, value));
                errors.add(new Problem(path + ".initial", "must be between " + low + " and " + high + " (was " + value
                        + "), using " + clamped));
                initial = Optional.of(clamped);
            }
        }
        Optional<Integer> decimals = field.decimals();
        if (decimals.isPresent() && field.isInteger()) {
            errors.add(new Problem(path + ".decimals", "decimals is only used with format: decimal, ignored"));
            decimals = Optional.empty();
        } else if (decimals.isPresent() && (decimals.get() < 0 || decimals.get() > MAX_DECIMALS)) {
            errors.add(new Problem(path + ".decimals", "must be between 0 and " + MAX_DECIMALS + " (was "
                    + decimals.get() + "), ignored"));
            decimals = Optional.empty();
        }
        if (decimals.isPresent() && initial.isPresent()) {
            final double rounded = new BigDecimal(Double.toString(initial.get()))
                    .setScale(decimals.get(), RoundingMode.HALF_UP).doubleValue();
            if (rounded != initial.get()) {
                errors.add(new Problem(path + ".initial", "has more than " + decimals.get() + " decimals (was "
                        + initial.get() + "), using " + rounded));
                initial = Optional.of(rounded);
            }
        }
        return new DialogInput.NumberFieldInput(field.label(), range(field.width(), 1, MAX_WIDTH, path + ".width",
                errors), field.format(), min, max, initial, maxLength, decimals);
    }

    /** @return {@code value}, or empty (with an error) if it is NaN or infinite */
    private static Optional<Double> finite(final Optional<Double> value, final String path, final List<Problem> errors) {
        if (value.isPresent() && !Double.isFinite(value.get())) {
            errors.add(new Problem(path, "must be a finite number (was " + value.get() + "), ignored"));
            return Optional.empty();
        }
        return value;
    }

    // --- buttons ------------------------------------------------------------------------------------------------

    private DialogButtons buttons(final DialogKind kind, final Map<String, Object> data, final DecodeContext ctx,
                                  final List<Problem> errors) {
        final DialogButtons defaults = DialogButtons.defaults(kind);
        final Object raw = data.get("buttons");
        if (raw == null) {
            if (kind != DialogKind.NOTICE) {
                errors.add(new Problem("buttons", "missing, " + needs(kind)));
            }
            return defaults;
        }
        if (!(raw instanceof Map<?, ?> section)) {
            errors.add(new Problem("buttons", "expected a section, got " + raw.getClass().getSimpleName()));
            return defaults;
        }
        final DecodeContext at = ctx.at("buttons");
        final Map<String, DialogButton> named = new LinkedHashMap<>();
        DialogButton exit = null;
        Integer columns = null;
        Integer buttonWidth = null;
        List<String> dialogs = List.of();
        final Set<String> seen = new HashSet<>();
        for (final Map.Entry<?, ?> entry : section.entrySet()) {
            // YAML reads an unquoted yes / no as a boolean, so a file that says `yes:` holds the key `true`
            final String key = switch (String.valueOf(entry.getKey())) {
                case "true" -> "yes";
                case "false" -> "no";
                default -> String.valueOf(entry.getKey());
            };
            final String path = "buttons." + key;
            final Object value = entry.getValue();
            seen.add(key);
            switch (kind) {
                case NOTICE -> {
                    if (key.equals("ok")) {
                        put(named, key, value, at.at(key), path, errors);
                    } else {
                        unknown(path, kind, errors);
                    }
                }
                case CONFIRMATION -> {
                    if (key.equals("yes") || key.equals("no")) {
                        put(named, key, value, at.at(key), path, errors);
                    } else {
                        unknown(path, kind, errors);
                    }
                }
                case MULTI_ACTION -> {
                    switch (key) {
                        case "exit" -> exit = button(value, at.at(key), path, errors);
                        case "columns" -> columns = columns(value, at.at(key), path, errors);
                        default -> {
                            if (NAME.matcher(key).matches()) {
                                put(named, key, value, at.at(key), path, errors);
                            } else {
                                errors.add(new Problem(path, "a button name uses lower case letters, digits, - and _"
                                        + " (at most 64), the button is dropped"));
                            }
                        }
                    }
                }
                case LIST, LINKS -> {
                    switch (key) {
                        case "exit" -> exit = button(value, at.at(key), path, errors);
                        case "columns" -> columns = columns(value, at.at(key), path, errors);
                        case "button-width" -> {
                            final Integer width = decode(value, Integer.class, at.at(key), errors, null);
                            buttonWidth = width == null ? null
                                    : range(Optional.of(width), 1, MAX_WIDTH, path, errors).orElse(null);
                        }
                        case "dialogs" -> {
                            if (kind == DialogKind.LIST) {
                                dialogs = dialogIds(value, at.at(key), path, errors);
                            } else {
                                unknown(path, kind, errors);
                            }
                        }
                        default -> unknown(path, kind, errors);
                    }
                }
            }
        }
        if (kind == DialogKind.CONFIRMATION) {
            final Map<String, DialogButton> complete = new LinkedHashMap<>();
            for (final String key : List.of("yes", "no")) {
                DialogButton button = named.get(key);
                if (button == null) {
                    if (!seen.contains(key)) {
                        errors.add(new Problem("buttons." + key, "missing, using the default '" + key + "' button"));
                    } else {
                        errors.add(new Problem("buttons." + key, "using the default '" + key + "' button"));
                    }
                    button = defaults.named().get(key);
                }
                complete.put(key, button);
            }
            named.clear();
            named.putAll(complete);
        } else if (kind == DialogKind.NOTICE && named.isEmpty()) {
            named.putAll(defaults.named());
        } else if (kind == DialogKind.MULTI_ACTION && named.isEmpty()) {
            errors.add(new Problem("buttons", "needs at least one button besides exit and columns"));
        } else if (kind == DialogKind.LIST && dialogs.isEmpty()) {
            errors.add(new Problem("buttons.dialogs", "needs at least one dialog id"));
        }
        return new DialogButtons(named, Optional.ofNullable(exit), Optional.ofNullable(columns),
                Optional.ofNullable(buttonWidth), dialogs);
    }

    private static String needs(final DialogKind kind) {
        return switch (kind) {
            case CONFIRMATION -> "a confirmation needs yes and no";
            case MULTI_ACTION -> "a multi-action dialog needs at least one button";
            case LIST -> "a list needs dialogs: [...]";
            default -> "using the default";
        };
    }

    private static void unknown(final String path, final DialogKind kind, final List<Problem> errors) {
        final String allowed = switch (kind) {
            case NOTICE -> "ok";
            case CONFIRMATION -> "yes, no";
            case MULTI_ACTION -> "any button name, exit, columns";
            case LIST -> "dialogs, exit, columns, button-width";
            case LINKS -> "exit, columns, button-width";
        };
        errors.add(new Problem(path, "unknown key for a " + kind.name().toLowerCase(Locale.ROOT).replace('_', '-')
                + " dialog (allowed: " + allowed + ")"));
    }

    private void put(final Map<String, DialogButton> into, final String key, final @Nullable Object value,
                     final DecodeContext at, final String path, final List<Problem> errors) {
        final DialogButton button = button(value, at, path, errors);
        if (button != null) {
            into.put(key, button);
        }
    }

    private @Nullable DialogButton button(final @Nullable Object value, final DecodeContext at, final String path,
                                          final List<Problem> errors) {
        if (value == null) {
            errors.add(new Problem(path, "must not be empty, the button is dropped"));
            return null;
        }
        final DialogButton decoded = decode(value, DialogButton.class, at, errors, null);
        if (decoded == null) {
            errors.add(new Problem(path, "the button is dropped"));
            return null;
        }
        return new DialogButton(decoded.label(), decoded.tooltip(),
                range(decoded.width(), 1, MAX_WIDTH, path + ".width", errors), decoded.click());
    }

    private @Nullable Integer columns(final Object value, final DecodeContext at, final String path,
                                      final List<Problem> errors) {
        final Integer columns = decode(value, Integer.class, at, errors, null);
        return columns == null ? null : range(Optional.of(columns), 1, Integer.MAX_VALUE, path, errors).orElse(null);
    }

    private List<String> dialogIds(final Object value, final DecodeContext at, final String path,
                                   final List<Problem> errors) {
        final List<String> raw;
        try {
            raw = codecs.decode(value, new TypeToken<List<String>>() { }, at);
        } catch (final DecodeException e) {
            errors.addAll(e.problems());
            return List.of();
        }
        final List<String> out = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            if (NAME.matcher(raw.get(i)).matches()) {
                out.add(raw.get(i));
            } else {
                errors.add(new Problem(path + "[" + i + "]", "'" + raw.get(i) + "' is not a dialog id (lower case"
                        + " letters, digits, - and _, at most 64), dropped"));
            }
        }
        return out;
    }

    // --- errors -------------------------------------------------------------------------------------------------

    private Map<String, String> errorLines(final Map<String, Object> data, final DecodeContext ctx,
                                           final Map<String, DialogInput> inputs, final List<Problem> errors) {
        final Map<String, String> out = new LinkedHashMap<>();
        final Object raw = data.get("errors");
        if (raw == null) {
            return out;
        }
        if (!(raw instanceof Map<?, ?> section)) {
            errors.add(new Problem("errors", "expected a section, got " + raw.getClass().getSimpleName()));
            return out;
        }
        for (final Map.Entry<?, ?> entry : section.entrySet()) {
            final String key = String.valueOf(entry.getKey());
            final String text = entry.getValue() == null ? null
                    : decode(entry.getValue(), String.class, ctx.at("errors").at(key), errors, null);
            if (text == null) {
                continue;
            }
            if (!inputs.containsKey(key)) {
                errors.add(new Problem("errors." + key, "there is no input with this key, never shown"));
            }
            out.put(key, text);
        }
        return out;
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    /** Clamps into {@code min..max}, reporting the change at {@code path}. */
    private static Optional<Integer> range(final Optional<Integer> value, final int min, final int max,
                                           final String path, final List<Problem> errors) {
        if (value.isEmpty() || (value.get() >= min && value.get() <= max)) {
            return value;
        }
        final int clamped = Math.max(min, Math.min(max, value.get()));
        errors.add(new Problem(path, "must be " + (max == Integer.MAX_VALUE ? ">= " + min
                : "between " + min + " and " + max) + " (was " + value.get() + "), using " + clamped));
        return Optional.of(clamped);
    }

    private static String firstLine(final @Nullable String message) {
        if (message == null) {
            return "unknown error";
        }
        final int end = message.indexOf('\n');
        return end < 0 ? message : message.substring(0, end);
    }
}
