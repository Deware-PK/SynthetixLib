package com.github.dewarepk.synthetixlib.database;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Text handling of migration scripts: line endings, checksum and splitting into statements. */
final class SqlScript {

    private SqlScript() {
    }

    /** @return {@code text} without a byte order mark and with {@code \n} as the only line ending */
    static String normalize(final String text) {
        final String withoutMark = text.startsWith("﻿") ? text.substring(1) : text;
        return withoutMark.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** @return the SHA-256 of {@code text} as lower-case hex */
    static String sha256(final String text) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }

    /**
     * Splits a script into statements. A statement ends at a {@code ;} that is the last thing on its line (a trailing
     * {@code -- comment} may follow). {@code --} starts a comment to the end of the line. Quotes ({@code '} {@code "}
     * {@code `}) are respected, so neither a {@code ;} nor a {@code --} inside a string counts. Empty statements are
     * dropped; the text of a statement is otherwise kept as written.
     */
    static List<String> split(final String script) {
        final List<String> statements = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        final int length = script.length();
        char quote = 0;
        for (int i = 0; i < length; i++) {
            final char c = script.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == quote) {
                    if (i + 1 < length && script.charAt(i + 1) == quote) {
                        current.append(c);   // doubled quote: stays inside the string
                        i++;
                    } else {
                        quote = 0;
                    }
                }
            } else if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                current.append(c);
            } else if (c == '-' && i + 1 < length && script.charAt(i + 1) == '-') {
                while (i + 1 < length && script.charAt(i + 1) != '\n' && script.charAt(i + 1) != '\r') {
                    i++;
                }
            } else if (c == ';' && endsLine(script, i + 1)) {
                add(statements, current);
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        add(statements, current);
        return statements;
    }

    private static boolean endsLine(final String script, final int from) {
        final int length = script.length();
        for (int i = from; i < length; i++) {
            final char c = script.charAt(i);
            if (c == '\n' || c == '\r') {
                return true;
            }
            if (c == '-' && i + 1 < length && script.charAt(i + 1) == '-') {
                return true;
            }
            if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }

    private static void add(final List<String> statements, final StringBuilder statement) {
        final String text = statement.toString().strip();
        if (!text.isEmpty()) {
            statements.add(text);
        }
    }
}
