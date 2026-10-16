package com.github.dewarepk.synthetixlib.config.mapping;

import com.github.dewarepk.synthetixlib.config.codec.Codec;
import com.github.dewarepk.synthetixlib.config.codec.DecodeContext;
import com.github.dewarepk.synthetixlib.config.codec.DecodeException;
import com.github.dewarepk.synthetixlib.config.codec.Problem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** {@code List<T>} and {@code Set<T>} (a data list; sets keep their order). Decoded values are unmodifiable. */
final class ListCodec implements Codec<Collection<Object>>, LenientCodec {

    private final Codec<Object> element;
    private final boolean set;

    ListCodec(final Codec<?> element, final boolean set) {
        this.element = Nulls.any(element);
        this.set = set;
    }

    @Override
    public Collection<Object> decode(final Object raw, final DecodeContext ctx) {
        if (!(raw instanceof List<?> items)) {
            throw ctx.expected("a list", raw);
        }
        final List<Object> values = new ArrayList<>(items.size());
        final List<Problem> problems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            try {
                values.add(Nulls.decode(element, items.get(i), ctx.at(i)));
            } catch (final DecodeException e) {
                problems.addAll(e.problems());
            }
        }
        if (!problems.isEmpty()) {
            throw new DecodeException(problems);
        }
        return set ? Collections.unmodifiableSet(new LinkedHashSet<>(values)) : Collections.unmodifiableList(values);
    }

    /**
     * Keeps the valid elements in order and drops each invalid one ({@code path[i]: message -> element ignored});
     * elements decode strictly, there is no default per element. Only a value that is not a list at all falls back
     * to {@code defaults}.
     */
    @Override
    public Object decodeLenient(final Object raw, final Object defaults, final DecodeContext ctx,
                                final LenientReport report) {
        if (!(raw instanceof List<?> items)) {
            report.invalid(ctx.expected("a list", raw), "using default");
            return defaults;
        }
        final List<Object> values = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            try {
                values.add(Nulls.decode(element, items.get(i), ctx.at(i)));
            } catch (final DecodeException e) {
                report.invalid(e, "element ignored");
            }
        }
        return set ? Collections.unmodifiableSet(new LinkedHashSet<>(values)) : Collections.unmodifiableList(values);
    }

    @Override
    public Object encode(final Collection<Object> value) {
        final List<Object> out = new ArrayList<>(value.size());
        for (final Object item : value) {
            out.add(Nulls.encode(element, item));
        }
        return out;
    }
}
