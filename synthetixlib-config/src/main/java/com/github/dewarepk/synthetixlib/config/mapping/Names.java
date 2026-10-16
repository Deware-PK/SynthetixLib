package com.github.dewarepk.synthetixlib.config.mapping;

/** Naming rules for keys and discriminator values. */
final class Names {

    private Names() {
    }

    /** {@code minBid} to {@code min-bid}, {@code HTTPRequest} to {@code http-request}. */
    static String kebab(final String name) {
        final StringBuilder out = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                final char previous = name.charAt(i - 1);
                final boolean wordStart = Character.isLowerCase(previous) || Character.isDigit(previous);
                final boolean endOfAcronym = Character.isUpperCase(previous)
                        && i + 1 < name.length() && Character.isLowerCase(name.charAt(i + 1));
                if (wordStart || endOfAcronym) {
                    out.append('-');
                }
            }
            out.append(Character.toLowerCase(c));
        }
        return out.toString();
    }
}
