package com.github.dewarepk.synthetixlib.gui;

import com.github.dewarepk.synthetixlib.config.codec.Problem;
import com.github.dewarepk.synthetixlib.core.text.TextService;
import com.github.dewarepk.synthetixlib.gui.file.ClickAction;
import com.github.dewarepk.synthetixlib.gui.file.MenuFile;
import com.github.dewarepk.synthetixlib.gui.file.MenuItem;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares a parsed menu with what code registered: an {@code action:}, {@code list:} or {@code state:} that the file
 * uses and code does not know makes the item that uses it inert, and is reported once per item, so a typo shows at
 * startup instead of on a click. A placeholder that code did not register only warns: the item keeps its clicks and
 * shows the tag as plain text.
 */
final class MenuChecker {

    /**
     * @param inert    layout characters whose items must not be rendered with behaviour or clicked
     * @param warnings one per problem, with the path {@code items.<char>} or {@code title}
     */
    record Checked(Set<Character> inert, List<Problem> warnings) {
    }

    /** A tag without arguments, like {@code <balance>}; closing tags, {@code <!x>}, {@code <#ff0000>} and {@code <x:y>} do not match. */
    private static final Pattern BARE_TAG = Pattern.compile("<([A-Za-z][A-Za-z0-9_-]*)>");

    private MenuChecker() {
    }

    /**
     * @param text a MiniMessage text from a menu file
     * @return the names (lower case) of the bare tags in it that are placeholders: neither a MiniMessage tag nor a
     *         tag of {@code lib.text()}; {@code player}, {@code page} and {@code pages} included
     */
    static List<String> placeholderNames(final String text, final TextService tags) {
        final List<String> names = new ArrayList<>();
        final Matcher matcher = BARE_TAG.matcher(text);
        while (matcher.find()) {
            final String name = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!tags.hasTag(name)) {
                names.add(name);
            }
        }
        return names;
    }

    static Checked check(final MenuFile file, final Registrations<?> registered, final TextService tags) {
        final Set<Character> inert = new LinkedHashSet<>();
        final List<Problem> warnings = new ArrayList<>();
        final List<String> titleWarnings = new ArrayList<>();
        placeholders(file.title(), registered, tags, titleWarnings);
        titleWarnings.forEach(message -> warnings.add(new Problem("title", message)));

        for (final Map.Entry<Character, MenuItem> entry : file.items().entrySet()) {
            final List<String> problems = new ArrayList<>();
            final List<String> itemWarnings = new ArrayList<>();
            item(entry.getValue(), registered, tags, problems, itemWarnings);
            final String path = "items." + entry.getKey();
            if (!problems.isEmpty()) {
                inert.add(entry.getKey());
                for (final String problem : new LinkedHashSet<>(problems)) {
                    warnings.add(new Problem(path, problem + ", the item is inert"));
                }
            }
            for (final String warning : new LinkedHashSet<>(itemWarnings)) {
                warnings.add(new Problem(path, warning));
            }
        }
        for (final Character c : registered.inputs().keySet()) {
            final MenuItem item = file.item(c);
            if (item == null || !item.isInput()) {
                warnings.add(new Problem("items." + c, "onInputChange('" + c + "') is registered but the file has no input"
                        + " slot with this character, the handler is never called"));
            }
        }
        return new Checked(inert, warnings);
    }

    private static void item(final MenuItem item, final Registrations<?> registered, final TextService tags,
                             final List<String> problems, final List<String> warnings) {
        item.list().ifPresent(id -> {
            if (!registered.lists().containsKey(id)) {
                problems.add("list '" + id + "' is not registered in code");
            }
        });
        item.state().ifPresent(id -> {
            if (!registered.states().containsKey(id)) {
                problems.add("state '" + id + "' is not registered in code");
            }
        });
        for (final ClickAction action : item.clicks().all()) {
            switch (action) {
                case ClickAction.Run run -> {
                    if (!registered.actions().containsKey(run.name())) {
                        problems.add("action '" + run.name() + "' is not registered in code");
                    }
                }
                case ClickAction.Message message -> placeholders(message.text(), registered, tags, warnings);
                default -> { }
            }
        }
        item.name().ifPresent(name -> placeholders(name, registered, tags, warnings));
        item.lore().forEach(line -> placeholders(line, registered, tags, warnings));
        item.empty().ifPresent(inner -> item(inner, registered, tags, problems, warnings));
        item.loading().ifPresent(inner -> item(inner, registered, tags, problems, warnings));
        item.states().values().forEach(inner -> item(inner, registered, tags, problems, warnings));
    }

    private static void placeholders(final String text, final Registrations<?> registered, final TextService tags,
                                     final List<String> warnings) {
        for (final String name : placeholderNames(text, tags)) {
            if (!Registrations.isBuiltInPlaceholder(name) && !registered.placeholders().containsKey(name)) {
                warnings.add("placeholder <" + name + "> is not registered in code, it is shown as text");
            }
        }
    }
}
