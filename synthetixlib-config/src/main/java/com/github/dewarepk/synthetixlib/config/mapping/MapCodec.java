package com.github.dewarepk.synthetixlib.config.mapping;

import com.github.dewarepk.synthetixlib.config.codec.Codec;
import com.github.dewarepk.synthetixlib.config.codec.DecodeContext;
import com.github.dewarepk.synthetixlib.config.codec.DecodeException;
import com.github.dewarepk.synthetixlib.config.codec.Problem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code Map<String, T>} (a data section). Keys keep their order; decoded maps are unmodifiable. */
final class MapCodec implements Codec<Map<String, Object>>, LenientCodec {

    private final Codec<Object> value;

    MapCodec(final Codec<?> value) {
        this.value = Nulls.any(value);
    }

    @Override
    public Map<String, Object> decode(final Object raw, final DecodeContext ctx) {
        if (!(raw instanceof Map<?, ?> entries)) {
            throw ctx.expected("a section", raw);
        }
        final Map<String, Object> values = new LinkedHashMap<>();
        final List<Problem> problems = new ArrayList<>();
        for (final Map.Entry<?, ?> entry : entries.entrySet()) {
            final String key = String.valueOf(entry.getKey());
            try {
                values.put(key, Nulls.decode(value, entry.getValue(), ctx.at(key)));
            } catch (final DecodeException e) {
                problems.addAll(e.problems());
            }
        }
        if (!problems.isEmpty()) {
            throw new DecodeException(problems);
        }
        return Collections.unmodifiableMap(values);
    }

    /**
     * Entries with a counterpart in {@code defaults} (same key) fall back to it when invalid; entries without one
     * are left out. Keys missing from the data are not added back, a map's entries are the user's to remove, unless the
     * report asks for it ({@link LenientReport#fillMaps()}, locale files): then each default entry the data lacks is
     * added, with the text of the report's supplement where it has one.
     */
    @Override
    public Object decodeLenient(final Object raw, final Object defaults, final DecodeContext ctx,
                                final LenientReport report) {
        final Map<?, ?> defaultEntries = (Map<?, ?>) defaults;
        if (!(raw instanceof Map<?, ?> entries)) {
            report.invalid(ctx.expected("a section", raw), "using default");
            return defaults;
        }
        final Map<String, Object> values = new LinkedHashMap<>();
        for (final Map.Entry<?, ?> entry : entries.entrySet()) {
            final String key = String.valueOf(entry.getKey());
            final Object fallback = defaultEntries.get(key);
            report.enter(key);
            try {
                if (fallback != null && entry.getValue() != null && value instanceof LenientCodec lenient) {
                    values.put(key, lenient.decodeLenient(entry.getValue(), fallback, ctx.at(key), report));
                } else {
                    values.put(key, Nulls.decode(value, entry.getValue(), ctx.at(key)));
                }
            } catch (final DecodeException e) {
                if (fallback != null) {
                    report.invalid(e, "using " + Preview.of(value.encode(fallback)));
                    values.put(key, fallback);
                } else {
                    report.invalid(e, "entry ignored");
                }
            } finally {
                report.exit();
            }
        }
        if (report.fillMaps()) {
            for (final Map.Entry<?, ?> entry : defaultEntries.entrySet()) {
                final String key = String.valueOf(entry.getKey());
                if (!entries.containsKey(key) && entry.getValue() != null) {
                    report.enter(key);
                    try {
                        values.put(key, fill(key, entry.getValue(), ctx, report));
                    } finally {
                        report.exit();
                    }
                }
            }
        }
        return Collections.unmodifiableMap(values);
    }

    /** The value of an entry the data lacks: the supplement's if it decodes, else the default; reported as missing. */
    private Object fill(final String key, final Object fallback, final DecodeContext ctx, final LenientReport report) {
        final Object supplied = report.supplementedHere();
        if (supplied != null) {
            try {
                final Object decoded = Nulls.decode(value, supplied, ctx.at(key));
                report.missing(value.encode(decoded));
                return decoded;
            } catch (final DecodeException e) {
                // the bundled text is not usable either: fall through to the default
            }
        }
        report.missing(value.encode(fallback));
        return fallback;
    }

    @Override
    public Object encode(final Map<String, Object> map) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final Map.Entry<String, Object> entry : map.entrySet()) {
            out.put(entry.getKey(), Nulls.encode(value, entry.getValue()));
        }
        return out;
    }
}
