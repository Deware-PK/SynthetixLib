package com.github.dewarepk.synthetixlib.config.codec;

import java.util.Objects;
import java.util.function.Function;

/**
 * Converts one type to and from the data tree ({@code Map<String,Object>}, {@code List<Object>}, {@code String},
 * {@code Number}, {@code Boolean}). Contract: {@code decode(encode(x)).equals(x)}.
 * {@code decode} is never called with {@code null}; signal bad data with {@code throw ctx.fail(...)}.
 *
 * <pre>{@code
 * codecs.register(Money.class, Codec.of(
 *         raw -> Money.parse((String) raw),      // decode: runtime exceptions become decode problems
 *         money -> money.toString()));           // encode
 * codecs.register(Region.class, Codec.string(Region::parse, Region::serialize));
 * }</pre>
 *
 * @param <T> the Java type
 */
public interface Codec<T> {

    /**
     * @param raw a non-null value from the data tree
     * @param ctx path and warning collector
     * @return the decoded value, never {@code null}
     * @throws DecodeException if {@code raw} is not valid
     */
    T decode(Object raw, DecodeContext ctx);

    /**
     * @param value a non-null value
     * @return a data tree node (see class description)
     */
    Object encode(T value);

    /**
     * Codec from two functions. Runtime exceptions thrown by {@code decode} (parse errors, bad casts) are
     * reported as decode problems at the current path.
     */
    static <T> Codec<T> of(final Function<Object, T> decode, final Function<T, ?> encode) {
        Objects.requireNonNull(decode, "decode");
        return new FunctionCodec<>((raw, ctx) -> decode.apply(raw), encode);
    }

    /**
     * Codec for a type stored as a string. Numbers and booleans in the data are accepted as their text form.
     * Runtime exceptions thrown by {@code parse} are reported as decode problems.
     */
    static <T> Codec<T> string(final Function<String, T> parse, final Function<T, String> format) {
        Objects.requireNonNull(parse, "parse");
        return new FunctionCodec<>((raw, ctx) -> parse.apply(ScalarCodecs.text(raw, ctx)), format);
    }
}
