package com.github.dewarepk.synthetixlib.config.mapping;

import com.github.dewarepk.synthetixlib.config.codec.Codec;
import com.github.dewarepk.synthetixlib.config.codec.DecodeContext;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Enum by constant name. Decoding ignores case and treats {@code -} and spaces as {@code _}
 * ({@code dark-oak} finds {@code DARK_OAK}); encoding writes the exact constant name.
 */
final class EnumCodec<E extends Enum<E>> implements Codec<E> {

    private final E[] constants;

    private EnumCodec(final Class<E> type) {
        this.constants = type.getEnumConstants();
    }

    /** @param type a class for which {@code isEnum()} is true */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static EnumCodec<?> of(final Class<?> type) {
        return new EnumCodec((Class) type);
    }

    @Override
    public E decode(final Object raw, final DecodeContext ctx) {
        if (!(raw instanceof CharSequence sequence)) {
            throw ctx.expected("one of " + names(), raw);
        }
        final String text = sequence.toString().strip();
        for (final E constant : constants) {
            if (constant.name().equals(text)) {
                return constant;
            }
        }
        final String normalized = text.replace('-', '_').replace(' ', '_');
        for (final E constant : constants) {
            if (constant.name().equalsIgnoreCase(normalized)) {
                return constant;
            }
        }
        throw ctx.fail("unknown value '" + text + "', valid values: " + names());
    }

    @Override
    public Object encode(final E value) {
        return value.name();
    }

    private String names() {
        return Arrays.stream(constants).map(Enum::name).collect(Collectors.joining(", "));
    }
}
