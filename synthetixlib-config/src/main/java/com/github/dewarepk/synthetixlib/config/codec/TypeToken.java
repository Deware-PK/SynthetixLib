package com.github.dewarepk.synthetixlib.config.codec;

import com.github.dewarepk.synthetixlib.config.mapping.TypeRef;

/**
 * Captures a generic type for {@link Codecs}, which otherwise only take plain classes. Create an anonymous
 * subclass; the type argument is the type.
 *
 * <pre>{@code
 * List<Map<String, Zone>> zones = codecs.decode(tree, new TypeToken<List<Map<String, Zone>>>() {});
 * }</pre>
 *
 * @param <T> the captured type
 */
public abstract class TypeToken<T> {

    private final TypeRef ref;

    /**
     * @throws IllegalArgumentException if the subclass does not give a type argument directly, i.e. is not
     *                                  written as {@code new TypeToken<Something>() {}}
     */
    protected TypeToken() {
        this.ref = TypeRef.capture(getClass(), TypeToken.class);
    }

    TypeRef ref() {
        return ref;
    }

    @Override
    public String toString() {
        return "TypeToken<" + ref + ">";
    }
}
