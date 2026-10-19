package com.github.dewarepk.synthetixlib.command;

import java.util.Objects;
import java.util.regex.Pattern;

/** Validation of literal and argument names. */
final class Names {

    private static final Pattern VALID = Pattern.compile("[a-z0-9_-]+");

    private Names() {
    }

    static String check(final String name, final String what) {
        Objects.requireNonNull(name, what);
        if (!VALID.matcher(name).matches()) {
            throw new IllegalArgumentException("\"" + name + "\" is not a valid " + what + " (allowed: a-z 0-9 _ -)");
        }
        return name;
    }
}
