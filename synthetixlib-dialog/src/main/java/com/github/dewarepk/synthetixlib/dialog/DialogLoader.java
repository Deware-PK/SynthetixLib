package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.config.ReloadResult;
import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.config.codec.Problem;
import com.github.dewarepk.synthetixlib.core.text.TextService;
import com.github.dewarepk.synthetixlib.dialog.file.DialogFile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Reads, parses and checks one dialog file against what code registered; stateless, shared by every dialog. */
final class DialogLoader {

    /**
     * A dialog as views see it.
     *
     * @param file  the parsed file
     * @param inert paths of the buttons that must not run anything because the file uses something code did not
     *              register or a form that does not fit
     */
    record Resolved(DialogFile file, Set<String> inert) {

        Resolved {
            inert = Set.copyOf(inert);
        }

        static Resolved empty() {
            return new Resolved(DialogFile.empty(), Set.of());
        }
    }

    /**
     * @param resolved the dialog, or {@code null} if the file could not be used and the caller keeps what it has
     * @param report   everything found
     */
    record Loaded(@Nullable Resolved resolved, ReloadResult report) {
    }

    private final DialogStore store;
    private final DialogParser parser;
    private final TextService tags;
    private final Codecs codecs;

    /** @param text tells the placeholders of a text from the MiniMessage tags it uses */
    DialogLoader(final DialogStore store, final DialogParser parser, final TextService text, final Codecs codecs) {
        this.store = store;
        this.parser = parser;
        this.tags = text;
        this.codecs = codecs;
    }

    /**
     * @param before the dialog that is in use now, to tell whether the file changed it
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
        final DialogParser.Parsed parsed = parser.parse(text);
        final DialogFile file = parsed.file();
        if (file == null) {
            return new Loaded(null, new ReloadResult(strings(parsed.errors()), strings(parsed.warnings()), false));
        }
        final DialogChecker.Checked checked = DialogChecker.check(file, registered, tags, codecs);
        final List<Problem> warnings = new ArrayList<>(parsed.warnings());
        warnings.addAll(checked.warnings());
        final Resolved resolved = new Resolved(file, checked.inert());
        return new Loaded(resolved, new ReloadResult(strings(parsed.errors()), strings(warnings),
                !resolved.equals(before)));
    }

    private static List<String> strings(final List<Problem> problems) {
        return problems.stream().map(Problem::toString).toList();
    }
}
