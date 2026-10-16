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
final class MapCodec implements Codec<Map<String, Object>> {

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

    @Override
    public Object encode(final Map<String, Object> map) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final Map.Entry<String, Object> entry : map.entrySet()) {
            out.put(entry.getKey(), Nulls.encode(value, entry.getValue()));
        }
        return out;
    }
}
