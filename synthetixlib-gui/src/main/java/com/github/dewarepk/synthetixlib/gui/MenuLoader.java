package com.github.dewarepk.synthetixlib.gui;

import com.github.dewarepk.synthetixlib.config.ReloadResult;
import com.github.dewarepk.synthetixlib.config.codec.Problem;
import com.github.dewarepk.synthetixlib.core.text.TextService;
import com.github.dewarepk.synthetixlib.gui.file.MenuFile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/** Reads, parses and checks one menu file against what code registered; stateless, shared by every menu. */
final class MenuLoader {

    /**
     * A menu as views see it.
     *
     * @param file  the parsed file
     * @param inert layout characters whose items are inert because the file uses something code did not register
     */
    record Resolved(MenuFile file, Set<Character> inert) {

        Resolved {
            inert = Set.copyOf(inert);
        }

        static Resolved empty() {
            return new Resolved(MenuFile.empty(), Set.of());
        }
    }

    /**
     * @param resolved the menu, or {@code null} if the file could not be used and the caller keeps what it has
     * @param report   everything found
     */
    record Loaded(@Nullable Resolved resolved, ReloadResult report) {
    }

    private final MenuStore store;
    private final MenuParser parser;
    private final TextService tags;
    private final Predicate<String> linked;

    /**
     * @param text   tells the placeholders of a text from the MiniMessage tags it uses
     * @param linked tells whether a link prefix ('dialog') has a screen opener
     */
    MenuLoader(final MenuStore store, final MenuParser parser, final TextService text, final Predicate<String> linked) {
        this.store = store;
        this.parser = parser;
        this.tags = text;
        this.linked = linked;
    }

    /**
     * @param before the menu that is in use now, to tell whether the file changed it
     * @throws IllegalStateException if there is no file and no bundled one
     */
    Loaded load(final String id, final Registrations<?> registered, final Resolved before) {
        final String text;
        try {
            text = store.read(id);
        } catch (final IOException e) {
            return new Loaded(null, new ReloadResult(List.of("cannot read " + store.file(id).getFileName() + ": " + e),
                    List.of(), false));
        }
        final MenuParser.Parsed parsed = parser.parse(text);
        final MenuFile file = parsed.file();
        if (file == null) {
            return new Loaded(null, new ReloadResult(strings(parsed.errors()), strings(parsed.warnings()), false));
        }
        final MenuChecker.Checked checked = MenuChecker.check(file, registered, tags, linked);
        final List<Problem> warnings = new ArrayList<>(parsed.warnings());
        final String outdated = store.outdated(id, text);
        if (outdated != null) {
            warnings.add(0, new Problem("", outdated));
        }
        warnings.addAll(checked.warnings());
        final Resolved resolved = new Resolved(file, checked.inert());
        return new Loaded(resolved, new ReloadResult(strings(parsed.errors()), strings(warnings),
                !resolved.equals(before)));
    }

    private static List<String> strings(final List<Problem> problems) {
        return problems.stream().map(Problem::toString).toList();
    }
}
