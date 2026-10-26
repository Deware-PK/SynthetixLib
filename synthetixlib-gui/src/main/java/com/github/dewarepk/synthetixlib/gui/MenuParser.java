package com.github.dewarepk.synthetixlib.gui;

import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.config.codec.DecodeContext;
import com.github.dewarepk.synthetixlib.config.codec.DecodeException;
import com.github.dewarepk.synthetixlib.config.codec.Problem;
import com.github.dewarepk.synthetixlib.config.codec.TypeToken;
import com.github.dewarepk.synthetixlib.config.yaml.YamlTrees;
import com.github.dewarepk.synthetixlib.gui.file.MenuFile;
import com.github.dewarepk.synthetixlib.gui.file.MenuItem;
import com.github.dewarepk.synthetixlib.gui.file.MenuKind;
import com.github.dewarepk.synthetixlib.gui.file.Ticks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.InvalidConfigurationException;
import org.jspecify.annotations.Nullable;

/**
 * Turns the text of a menu file into a {@link MenuFile} using the config codecs. Everything wrong is reported and
 * replaced by something safe, so a damaged file still gives a menu that opens: an item that cannot be read leaves its
 * slots empty, a layout of the wrong size is cut or padded with empty slots. Only text that is not a YAML section
 * gives no menu. Unknown keys are errors, like every other mistake in the file.
 */
final class MenuParser {

    /**
     * @param file     the menu, or {@code null} if the text is not a YAML section
     * @param errors   what was wrong
     * @param warnings things that change nothing
     */
    record Parsed(@Nullable MenuFile file, List<Problem> errors, List<Problem> warnings) {
    }

    private static final String VERSION_KEY = "config-version";
    private static final int DEFAULT_ROWS = 3;

    private final Codecs codecs;

    MenuParser(final Codecs codecs) {
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
                case VERSION_KEY, "title", "type", "rows", "update-every", "layout", "items" -> { }
                default -> errors.add(new Problem(key, "unknown key"));
            }
        }

        final String title = read(data, "title", String.class, "", ctx, errors);
        final MenuKind type = read(data, "type", MenuKind.class, MenuKind.CHEST, ctx, errors);
        final Integer declaredRows = read(data, "rows", Integer.class, null, ctx, errors);
        final int updateTicks = updateTicks(data, ctx, errors);
        final List<String> rawLayout = layoutLines(data, ctx, errors);

        final int rows = rows(type, declaredRows, rawLayout, errors, warnings);
        final List<String> layout = layout(type, rows, rawLayout, errors);

        final Set<Character> failed = new LinkedHashSet<>();
        final Map<Character, MenuItem> items = items(data, ctx, errors, failed);
        crossCheck(layout, items, failed, errors, warnings);

        for (final Problem warning : ctx.warnings()) {
            errors.add(warning);
        }
        return new Parsed(new MenuFile(title, type, rows, updateTicks, layout, items), errors, warnings);
    }

    // --- top level keys -----------------------------------------------------------------------------------------

    private <T> @Nullable T read(final Map<String, Object> data, final String key, final Class<T> type,
                                 final @Nullable T fallback, final DecodeContext ctx, final List<Problem> errors) {
        final Object raw = data.get(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return codecs.decode(raw, type, ctx.at(key));
        } catch (final DecodeException e) {
            errors.addAll(e.problems());
            return fallback;
        }
    }

    private int updateTicks(final Map<String, Object> data, final DecodeContext ctx, final List<Problem> errors) {
        final String raw = read(data, "update-every", String.class, null, ctx, errors);
        if (raw == null) {
            return 0;
        }
        try {
            return Ticks.parse(raw);
        } catch (final IllegalArgumentException e) {
            errors.add(new Problem("update-every", e.getMessage()));
            return 0;
        }
    }

    private List<String> layoutLines(final Map<String, Object> data, final DecodeContext ctx,
                                     final List<Problem> errors) {
        final Object raw = data.get("layout");
        if (raw == null) {
            errors.add(new Problem("layout", "missing, every slot stays empty"));
            return List.of();
        }
        try {
            return codecs.decode(raw, new TypeToken<List<String>>() { }, ctx.at("layout"));
        } catch (final DecodeException e) {
            errors.addAll(e.problems());
            return List.of();
        }
    }

    private static int rows(final MenuKind type, final @Nullable Integer declared, final List<String> layout,
                            final List<Problem> errors, final List<Problem> warnings) {
        if (type != MenuKind.CHEST) {
            if (declared != null && declared != type.minRows()) {
                warnings.add(new Problem("rows", "ignored, a " + type.name().toLowerCase(Locale.ROOT)
                        + " always has " + type.minRows() + " row" + (type.minRows() == 1 ? "" : "s")));
            }
            return type.minRows();
        }
        if (declared != null) {
            if (type.allows(declared)) {
                return declared;
            }
            errors.add(new Problem("rows", "must be between " + type.minRows() + " and " + type.maxRows()
                    + " (was " + declared + ")"));
        }
        return type.allows(layout.size()) ? layout.size() : DEFAULT_ROWS;
    }

    /** Exactly {@code rows} lines of the kind's width: lines are padded or cut, missing lines are empty. */
    private static List<String> layout(final MenuKind type, final int rows, final List<String> raw,
                                       final List<Problem> errors) {
        if (!raw.isEmpty() && raw.size() != rows) {
            errors.add(new Problem("layout", "has " + raw.size() + " lines, " + describe(type, rows) + " needs "
                    + rows + (raw.size() > rows ? " (extra lines are ignored)" : " (missing lines are empty)")));
        }
        final List<String> out = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            final String line = i < raw.size() ? raw.get(i) : "";
            final StringBuilder fixed = new StringBuilder(type.width());
            for (int c = 0; c < type.width(); c++) {
                final char ch = c < line.length() ? line.charAt(c) : ' ';
                fixed.append(Character.isSurrogate(ch) || Character.isISOControl(ch) ? ' ' : ch);
            }
            if (i < raw.size() && line.length() != type.width()) {
                errors.add(new Problem("layout[" + i + "]", "has " + line.length() + " characters, "
                        + describe(type, rows) + " needs " + type.width()
                        + (line.length() > type.width() ? " (the rest is ignored)" : " (the rest is empty)")));
            }
            if (i < raw.size() && line.chars().anyMatch(ch -> Character.isSurrogate((char) ch)
                    || Character.isISOControl((char) ch))) {
                errors.add(new Problem("layout[" + i + "]", "contains a character that cannot be used as an item key"
                        + " (emoji and control characters), those slots are empty"));
            }
            out.add(fixed.toString());
        }
        return out;
    }

    private static String describe(final MenuKind type, final int rows) {
        return type == MenuKind.CHEST ? rows + "-row chest" : type.name().toLowerCase(Locale.ROOT);
    }

    // --- items --------------------------------------------------------------------------------------------------

    private Map<Character, MenuItem> items(final Map<String, Object> data, final DecodeContext ctx,
                                           final List<Problem> errors, final Set<Character> failed) {
        final Map<Character, MenuItem> out = new LinkedHashMap<>();
        final Object raw = data.get("items");
        if (raw == null) {
            errors.add(new Problem("items", "missing"));
            return out;
        }
        if (!(raw instanceof Map<?, ?> section)) {
            errors.add(new Problem("items", "expected a section, got " + raw.getClass().getSimpleName()));
            return out;
        }
        final DecodeContext itemsCtx = ctx.at("items");
        for (final Map.Entry<?, ?> entry : section.entrySet()) {
            final String key = String.valueOf(entry.getKey());
            if (key.length() != 1 || Character.isSurrogate(key.charAt(0)) || Character.isISOControl(key.charAt(0))) {
                errors.add(new Problem("items." + key, "an item key must be exactly one character"));
                continue;
            }
            final char c = key.charAt(0);
            if (c == ' ') {
                errors.add(new Problem("items. ", "a space is always an empty slot and cannot have an item"));
                continue;
            }
            try {
                final Object value = entry.getValue();
                if (value == null) {
                    throw itemsCtx.at(key).fail("must not be empty");
                }
                final MenuItem item = codecs.decode(value, MenuItem.class, itemsCtx.at(key));
                checkItem(item, "items." + key, false, errors);
                out.put(c, item);
            } catch (final DecodeException e) {
                errors.addAll(e.problems());
                failed.add(c);
            }
        }
        return out;
    }

    /** Rules between keys of one item. The item is kept either way: its slots show what could be read. */
    private static void checkItem(final MenuItem item, final String path, final boolean nested,
                                  final List<Problem> errors) {
        final boolean dynamic = item.list().isPresent() || item.state().isPresent();
        if (item.material().isEmpty() && item.base64().isEmpty() && !dynamic && !item.isInput()) {
            errors.add(new Problem(path, "material is required (use material: air for an invisible click area)"));
        }
        checkInput(item, path, nested, dynamic, errors);
        if (item.list().isPresent() && item.state().isPresent()) {
            errors.add(new Problem(path, "list and state cannot be used on the same item"));
        }
        if (item.state().isPresent() && item.states().isEmpty()) {
            errors.add(new Problem(path + ".states", "state needs at least one entry in states"));
        }
        if (item.state().isEmpty() && !item.states().isEmpty()) {
            errors.add(new Problem(path + ".states", "states needs state: <name> to pick one"));
        }
        if (item.list().isEmpty() && (item.empty().isPresent() || item.loading().isPresent())) {
            errors.add(new Problem(path, "empty and loading belong to an item with list"));
        }
        if (nested && (dynamic || !item.states().isEmpty() || item.empty().isPresent()
                || item.loading().isPresent())) {
            errors.add(new Problem(path, "list, state, states, empty and loading are not allowed here"));
        }
        item.empty().ifPresent(inner -> checkItem(inner, path + ".empty", true, errors));
        item.loading().ifPresent(inner -> checkItem(inner, path + ".loading", true, errors));
        if (item.busy().isPresent() && (item.isInput() || item.list().isPresent())) {
            errors.add(new Problem(path, "busy belongs to an item with a click, not to a list or an input slot"));
        }
        item.busy().ifPresent(inner -> {
            if (inner.busy().isPresent()) {
                errors.add(new Problem(path + ".busy", "busy is not allowed here"));
            }
            checkItem(inner, path + ".busy", true, errors);
        });
        item.states().forEach((name, inner) -> checkItem(inner, path + ".states." + name, true, errors));
    }

    /** {@code input}, {@code accept} and {@code max-amount}: an input slot shows nothing of its own and takes only what it accepts. */
    private static void checkInput(final MenuItem item, final String path, final boolean nested,
                                   final boolean dynamic, final List<Problem> errors) {
        if (!item.isInput()) {
            if (!item.accept().isEmpty() || item.maxAmount().isPresent()) {
                errors.add(new Problem(path, "accept and max-amount belong to an item with input: true"));
            }
            return;
        }
        if (nested) {
            errors.add(new Problem(path, "input is not allowed here"));
            return;
        }
        if (dynamic) {
            errors.add(new Problem(path, "input cannot be combined with list or state"));
        }
        final List<String> ignored = new ArrayList<>();
        if (item.material().isPresent()) {
            ignored.add("material");
        }
        if (item.base64().isPresent()) {
            ignored.add("base64");
        }
        if (item.amount().isPresent()) {
            ignored.add("amount");
        }
        if (item.name().isPresent()) {
            ignored.add("name");
        }
        if (!item.lore().isEmpty()) {
            ignored.add("lore");
        }
        if (item.itemModel().isPresent()) {
            ignored.add("item-model");
        }
        if (item.glint().isPresent()) {
            ignored.add("glint");
        }
        if (item.hideTooltip().isPresent()) {
            ignored.add("hide-tooltip");
        }
        if (item.tooltipStyle().isPresent()) {
            ignored.add("tooltip-style");
        }
        if (item.hideWhen().isPresent()) {
            ignored.add("hide-when");
        }
        if (item.sound().isPresent()) {
            ignored.add("sound");
        }
        if (item.click().isPresent()) {
            ignored.add("click");
        }
        if (!ignored.isEmpty()) {
            errors.add(new Problem(path, "an input slot shows nothing of its own and cannot be clicked, these keys have no"
                    + " effect: " + String.join(", ", ignored)));
        }
        for (final String problem : AcceptFilter.parse(item.accept()).problems()) {
            errors.add(new Problem(path + ".accept", problem));
        }
    }

    private static void crossCheck(final List<String> layout, final Map<Character, MenuItem> items,
                                   final Set<Character> failed, final List<Problem> errors,
                                   final List<Problem> warnings) {
        final Set<Character> used = new LinkedHashSet<>();
        final Set<Character> reported = new LinkedHashSet<>();
        for (int row = 0; row < layout.size(); row++) {
            final String line = layout.get(row);
            for (int col = 0; col < line.length(); col++) {
                final char c = line.charAt(col);
                if (c == ' ') {
                    continue;
                }
                used.add(c);
                if (!items.containsKey(c) && !failed.contains(c) && reported.add(c)) {
                    errors.add(new Problem("layout[" + row + "]", "character '" + c + "' has no item in items, "
                            + "its slots stay empty"));
                }
            }
        }
        for (final Character c : items.keySet()) {
            if (!used.contains(c)) {
                warnings.add(new Problem("items." + c, "not used in layout"));
            }
        }
    }

    private static String firstLine(final @Nullable String message) {
        if (message == null) {
            return "unknown error";
        }
        final int end = message.indexOf('\n');
        return end < 0 ? message : message.substring(0, end);
    }
}
