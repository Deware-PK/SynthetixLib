package com.github.dewarepk.synthetixlib.config.mapping;

import java.util.Collection;
import java.util.Map;
import java.util.StringJoiner;

/** Short single-line text of a data tree node, for error messages such as {@code -> using 8}. */
final class Preview {

    private static final int LIMIT = 48;

    private Preview() {
    }

    static String of(final Object tree) {
        final String text = format(tree);
        return text.length() > LIMIT ? text.substring(0, LIMIT - 3) + "..." : text;
    }

    private static String format(final Object tree) {
        if (tree == null) {
            return "nothing";
        }
        if (tree instanceof Map<?, ?> map) {
            final StringJoiner joiner = new StringJoiner(", ", "{", "}");
            map.forEach((key, value) -> joiner.add(key + ": " + format(value)));
            return joiner.toString();
        }
        if (tree instanceof Collection<?> items) {
            final StringJoiner joiner = new StringJoiner(", ", "[", "]");
            items.forEach(item -> joiner.add(format(item)));
            return joiner.toString();
        }
        return String.valueOf(tree).replace('\n', ' ');
    }
}
