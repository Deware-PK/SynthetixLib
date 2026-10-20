package com.github.dewarepk.synthetixlib.gui.file;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The click types a menu file can tell apart, written in lower case with a dash: {@code left}, {@code shift-left}.
 *
 * <pre>{@code
 * click:
 *   left:  [open:my-listings]
 *   right: [close, 'command:auction sell']
 * }</pre>
 */
public enum ClickKind {

    LEFT,
    RIGHT,
    SHIFT_LEFT,
    SHIFT_RIGHT,
    MIDDLE,
    DROP;

    /** @return the name used in menu files, e.g. {@code shift-left} */
    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * @param key a name as written in a menu file, in any case
     * @return the kind, or {@code null} if there is no such click type
     */
    public static @Nullable ClickKind of(final String key) {
        final String normalized = key.strip().toUpperCase(Locale.ROOT).replace('-', '_');
        for (final ClickKind kind : values()) {
            if (kind.name().equals(normalized)) {
                return kind;
            }
        }
        return null;
    }
}
