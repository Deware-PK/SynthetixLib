package com.github.dewarepk.synthetixlib.config.mapping;

import com.github.dewarepk.synthetixlib.config.codec.Codec;
import com.github.dewarepk.synthetixlib.config.codec.DecodeContext;
import java.util.Optional;

/** {@code Optional<T>}: {@code null} / missing decodes to empty; empty encodes to {@code null}. */
final class OptionalCodec implements Codec<Optional<Object>> {

    private final Codec<Object> inner;

    OptionalCodec(final Codec<?> inner) {
        this.inner = Nulls.any(inner);
    }

    @Override
    public Optional<Object> decode(final Object raw, final DecodeContext ctx) {
        return raw == null ? Optional.empty() : Optional.of(inner.decode(raw, ctx));
    }

    @Override
    public Object encode(final Optional<Object> value) {
        return value.isPresent() ? inner.encode(value.get()) : null;
    }
}
