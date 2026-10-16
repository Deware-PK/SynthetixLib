package com.github.dewarepk.synthetixlib.config.codec;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Codec built from functions; turns unexpected runtime exceptions in {@code decode} into problems. */
final class FunctionCodec<T> implements Codec<T> {

    private final BiFunction<Object, DecodeContext, ? extends T> decoder;
    private final Function<? super T, ?> encoder;

    FunctionCodec(final BiFunction<Object, DecodeContext, ? extends T> decoder, final Function<? super T, ?> encoder) {
        this.decoder = decoder;
        this.encoder = Objects.requireNonNull(encoder, "encode");
    }

    @Override
    public T decode(final Object raw, final DecodeContext ctx) {
        final T value;
        try {
            value = decoder.apply(raw, ctx);
        } catch (final DecodeException e) {
            throw e;
        } catch (final ClassCastException e) {
            throw ctx.fail("unexpected " + DecodeContext.describe(raw));
        } catch (final RuntimeException e) {
            throw ctx.fail(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        if (value == null) {
            throw ctx.fail("decoded to null");
        }
        return value;
    }

    @Override
    public Object encode(final T value) {
        return encoder.apply(value);
    }
}
