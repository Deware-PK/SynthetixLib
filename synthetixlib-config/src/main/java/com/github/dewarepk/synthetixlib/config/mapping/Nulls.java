package com.github.dewarepk.synthetixlib.config.mapping;

import com.github.dewarepk.synthetixlib.config.codec.Codec;
import com.github.dewarepk.synthetixlib.config.codec.DecodeContext;

/** The one place that decides what {@code null} means: only {@code Optional} accepts it. */
final class Nulls {

    private Nulls() {
    }

    /** Decodes a value that may be {@code null}. */
    static Object decode(final Codec<?> codec, final Object raw, final DecodeContext ctx) {
        if (raw == null && !(codec instanceof OptionalCodec)) {
            throw ctx.fail("must not be null");
        }
        return codec.decode(raw, ctx);
    }

    /** Encodes a non-null value. */
    static Object encode(final Codec<?> codec, final Object value) {
        if (value == null) {
            throw new IllegalArgumentException("null cannot be encoded here; use Optional or @Nullable");
        }
        return any(codec).encode(value);
    }

    @SuppressWarnings("unchecked")
    static Codec<Object> any(final Codec<?> codec) {
        return (Codec<Object>) codec;
    }
}
