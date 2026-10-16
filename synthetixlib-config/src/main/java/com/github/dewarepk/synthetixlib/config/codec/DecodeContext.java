package com.github.dewarepk.synthetixlib.config.codec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Where a {@link Codec} is decoding: the current path, plus a collector for warnings that do not stop decoding
 * (e.g. unknown keys). Child contexts from {@link #at(String)} / {@link #at(int)} share the collector.
 * Not thread-safe: one context per decode call; create it with {@link Codecs#newContext()}.
 *
 * <pre>{@code
 * Codec<Money> money = Codec.of(raw -> Money.parse((String) raw), Money::toString);
 * // inside a custom codec:
 * throw ctx.fail("must be positive");
 * }</pre>
 */
public final class DecodeContext {

    private static final int PREVIEW_LENGTH = 24;

    private final Codecs codecs;
    private final String path;
    private final List<Problem> warnings;

    DecodeContext(final Codecs codecs) {
        this(codecs, "", new ArrayList<>());
    }

    private DecodeContext(final Codecs codecs, final String path, final List<Problem> warnings) {
        this.codecs = codecs;
        this.path = path;
        this.warnings = warnings;
    }

    /** @return the current path, empty at the root, e.g. {@code rewards[1].chance} */
    public String path() {
        return path;
    }

    /** @return a child context for a map key */
    public DecodeContext at(final String key) {
        Objects.requireNonNull(key, "key");
        return new DecodeContext(codecs, path.isEmpty() ? key : path + "." + key, warnings);
    }

    /** @return a child context for a list index */
    public DecodeContext at(final int index) {
        return new DecodeContext(codecs, path + "[" + index + "]", warnings);
    }

    /** Records a warning at the current path; decoding continues. */
    public void warn(final String message) {
        warnings.add(new Problem(path, Objects.requireNonNull(message, "message")));
    }

    /** @return a snapshot of the warnings collected so far (shared by all child contexts) */
    public List<Problem> warnings() {
        return List.copyOf(warnings);
    }

    /**
     * Creates the exception for a problem at the current path; use as {@code throw ctx.fail("...")}.
     *
     * @return a new exception, never {@code null}
     */
    public DecodeException fail(final String message) {
        return new DecodeException(path, message);
    }

    /**
     * Creates the exception for a value of the wrong kind: {@code expected an integer, got string "abc"}.
     *
     * @param what what was expected, e.g. {@code "an integer"}
     * @param raw  the offending value
     */
    public DecodeException expected(final String what, final Object raw) {
        return fail("expected " + what + ", got " + describe(raw));
    }

    /** Decodes a nested value at the current path, e.g. from inside a custom codec. */
    public <V> V decode(final Object raw, final Class<V> type) {
        return codecs.decode(raw, type, this);
    }

    /** Decodes a nested generic value at the current path. */
    public <V> V decode(final Object raw, final TypeToken<V> type) {
        return codecs.decode(raw, type, this);
    }

    static String describe(final Object raw) {
        if (raw == null) {
            return "null";
        }
        if (raw instanceof CharSequence text) {
            final String s = text.toString();
            return "string \"" + (s.length() > PREVIEW_LENGTH ? s.substring(0, PREVIEW_LENGTH) + "..." : s) + "\"";
        }
        if (raw instanceof Number) {
            return "number " + raw;
        }
        if (raw instanceof Boolean) {
            return "boolean " + raw;
        }
        if (raw instanceof Map) {
            return "a section";
        }
        if (raw instanceof List) {
            return "a list";
        }
        return raw.getClass().getSimpleName();
    }
}
