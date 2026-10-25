package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.config.codec.Problem;
import com.github.dewarepk.synthetixlib.core.text.TextService;
import com.github.dewarepk.synthetixlib.dialog.file.ClickAction;
import com.github.dewarepk.synthetixlib.dialog.file.DialogBody;
import com.github.dewarepk.synthetixlib.dialog.file.DialogButton;
import com.github.dewarepk.synthetixlib.dialog.file.DialogFile;
import com.github.dewarepk.synthetixlib.dialog.file.DialogInput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares a parsed dialog with what code registered: an {@code action:} that code does not know, or a {@code submit}
 * whose record does not fit the inputs, makes the buttons that use it inert, and is reported at startup instead of on a
 * click. A placeholder that code did not register only warns: the text shows the tag as it is.
 */
final class DialogChecker {

    /**
     * @param inert    paths of the buttons ({@code buttons.yes}) that must not run anything
     * @param warnings one per problem, with the path of the key
     */
    record Checked(Set<String> inert, List<Problem> warnings) {
    }

    /** A tag without arguments, like {@code <fee>}; closing tags, {@code <!x>}, {@code <#ff0000>} and {@code <x:y>} do not match. */
    private static final Pattern BARE_TAG = Pattern.compile("<([A-Za-z][A-Za-z0-9_-]*)>");

    private DialogChecker() {
    }

    /**
     * @param text a MiniMessage text from a dialog file
     * @return the names (lower case) of the bare tags in it that are placeholders: neither a MiniMessage tag nor a tag
     *         of {@code lib.text()}
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

    static Checked check(final DialogFile file, final Registrations<?> registered, final TextService tags,
                         final Codecs codecs) {
        final Set<String> inert = new LinkedHashSet<>();
        final List<Problem> warnings = new ArrayList<>();

        final Set<String> badSubmits = new LinkedHashSet<>();
        registered.submits().forEach((name, submit) -> {
            final List<Problem> problems = FormCheck.check(codecs, name, submit.form(), file.inputs());
            if (!problems.isEmpty()) {
                badSubmits.add(name);
                problems.forEach(problem -> warnings.add(new Problem(problem.path(),
                        problem.message() + ", the buttons that use it are inert")));
            }
        });

        for (final Map.Entry<String, DialogButton> entry : file.buttons().byPath().entrySet()) {
            final Set<String> problems = new LinkedHashSet<>();
            for (final ClickAction action : entry.getValue().clicks().actions()) {
                if (action instanceof ClickAction.Run run) {
                    if (!registered.hasAction(run.name())) {
                        problems.add("action '" + run.name() + "' is not registered in code");
                    } else if (badSubmits.contains(run.name())) {
                        inert.add(entry.getKey());
                    }
                }
            }
            final List<ClickAction> steps = entry.getValue().clicks().actions();
            if (steps.size() > 1 && steps.stream().anyMatch(action -> action instanceof ClickAction.Url
                    || action instanceof ClickAction.Copy)) {
                problems.add("url and copy only work as the only step of a button");
            }
            if (!problems.isEmpty()) {
                inert.add(entry.getKey());
                problems.forEach(problem -> warnings.add(new Problem(entry.getKey() + ".click",
                        problem + ", the button is inert")));
            }
        }

        for (int i = 0; i < file.body().size(); i++) {
            if (file.body().get(i) instanceof DialogBody.Item item && item.item().placeholder().isPresent()
                    && !registered.items().containsKey(item.item().placeholder().get())) {
                warnings.add(new Problem("body[" + i + "].item.placeholder", "item '" + item.item().placeholder().get()
                        + "' is not registered in code, a barrier is shown instead"));
            }
        }

        for (final Map.Entry<String, String> text : texts(file).entrySet()) {
            final boolean errorLine = text.getKey().startsWith("errors.");
            for (final String name : new LinkedHashSet<>(placeholderNames(text.getValue(), tags))) {
                final boolean known = Registrations.isBuiltInPlaceholder(name)
                        || (errorLine && (name.equals("min") || name.equals("max") || name.equals("value")))
                        || registered.placeholders().containsKey(name);
                if (!known) {
                    warnings.add(new Problem(text.getKey(), "placeholder <" + name
                            + "> is not registered in code, it is shown as text"));
                }
            }
        }
        for (final String key : registered.inputs().keySet()) {
            if (!file.inputs().containsKey(key)) {
                warnings.add(new Problem("inputs." + key, "input('" + key + "') is registered but the file has no input"
                        + " with this key, the value is never used"));
            }
        }
        for (final String key : registered.ranges().keySet()) {
            final DialogInput input = file.inputs().get(key);
            if (!(input instanceof DialogInput.NumberInput || input instanceof DialogInput.NumberFieldInput)) {
                warnings.add(new Problem("inputs." + key, "inputRange('" + key + "') is registered but the file has no"
                        + " number or number-field input with this key, the range is never used"));
            }
        }
        for (final String key : registered.maxLengths().keySet()) {
            final DialogInput input = file.inputs().get(key);
            if (!(input instanceof DialogInput.TextInput || input instanceof DialogInput.NumberFieldInput)) {
                warnings.add(new Problem("inputs." + key, "maxLength('" + key + "') is registered but the file has no"
                        + " text or number-field input with this key, the length is never used"));
            }
        }
        return new Checked(inert, warnings);
    }

    /** Every MiniMessage text of the file by its path. */
    static Map<String, String> texts(final DialogFile file) {
        final Map<String, String> out = new LinkedHashMap<>();
        out.put("title", file.title());
        file.externalTitle().ifPresent(text -> out.put("external-title", text));
        for (int i = 0; i < file.body().size(); i++) {
            final String path = "body[" + i + "]";
            switch (file.body().get(i)) {
                case DialogBody.Message message -> out.put(path + ".message", message.text());
                case DialogBody.Item item -> item.description().ifPresent(text -> out.put(path + ".description", text));
            }
        }
        file.inputs().forEach((key, input) -> {
            out.put("inputs." + key + ".label", input.label());
            if (input instanceof DialogInput.OptionInput option) {
                option.options().forEach((id, entry) -> entry.display()
                        .ifPresent(text -> out.put("inputs." + key + ".options." + id + ".display", text)));
            }
        });
        file.buttons().byPath().forEach((path, button) -> {
            out.put(path + ".label", button.label());
            button.tooltip().ifPresent(text -> out.put(path + ".tooltip", text));
            for (final ClickAction action : button.clicks().actions()) {
                if (action instanceof ClickAction.Message message) {
                    out.put(path + ".click", message.text());
                }
            }
        });
        file.errors().forEach((key, text) -> out.put("errors." + key, text));
        return out;
    }
}
