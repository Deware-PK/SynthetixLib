package com.github.dewarepk.synthetixlib.config.mapping;

import com.github.dewarepk.synthetixlib.config.annotation.TypeName;
import com.github.dewarepk.synthetixlib.config.codec.Codec;
import com.github.dewarepk.synthetixlib.config.codec.DecodeContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A sealed interface (or class) of records as a section with a {@code type} key naming the record:
 * {@code {type: item, item: ..., chance: 50}}. Nested sealed interfaces are flattened to their records.
 */
final class SealedCodec implements Codec<Object> {

    static final String DISCRIMINATOR = "type";

    private final Class<?> type;
    private final Map<String, Class<?>> leaves = new LinkedHashMap<>();
    private final Map<Class<?>, String> names = new LinkedHashMap<>();
    private Map<String, Codec<Object>> codecs = Map.of();

    /** @throws IllegalArgumentException if a permitted subtype is not a record, or two share a name */
    SealedCodec(final Class<?> type) {
        this.type = type;
        for (final Class<?> leaf : leavesOf(type, new LinkedHashSet<>())) {
            final String name = nameOf(leaf);
            final Class<?> previous = leaves.put(name, leaf);
            if (previous != null) {
                throw new IllegalArgumentException("Subtypes " + previous.getName() + " and " + leaf.getName()
                        + " of " + type.getName() + " both map to type '" + name + "'; give one a @TypeName");
            }
            names.put(leaf, name);
        }
    }

    /** Resolves the codec of every permitted record. */
    void link(final Function<TypeRef, Codec<?>> resolve) {
        final Map<String, Codec<Object>> linked = new LinkedHashMap<>();
        for (final Map.Entry<String, Class<?>> leaf : leaves.entrySet()) {
            final Codec<?> codec = resolve.apply(TypeRef.of(leaf.getValue()));
            if (codec instanceof RecordCodec<?> record && record.hasKey(DISCRIMINATOR)) {
                throw new IllegalArgumentException(leaf.getValue().getName() + " has a component with the key '"
                        + DISCRIMINATOR + "', which is the discriminator of " + type.getName()
                        + "; rename it with @Key");
            }
            if (codec instanceof RecordCodec<?> record) {
                record.markSealedLeaf(leaf.getKey());
            }
            linked.put(leaf.getKey(), Nulls.any(codec));
        }
        this.codecs = Collections.unmodifiableMap(linked);
    }

    private static Set<Class<?>> leavesOf(final Class<?> sealed, final Set<Class<?>> into) {
        final Class<?>[] permitted = sealed.getPermittedSubclasses();
        if (permitted == null || permitted.length == 0) {
            throw new IllegalArgumentException(sealed.getName() + " has no permitted subtypes");
        }
        for (final Class<?> sub : permitted) {
            if (sub.isRecord()) {
                into.add(sub);
            } else if (sub.isSealed()) {
                leavesOf(sub, into);
            } else {
                throw new IllegalArgumentException("Permitted subtype " + sub.getName() + " of " + sealed.getName()
                        + " must be a record or a sealed interface");
            }
        }
        return into;
    }

    /**
     * The type name of a leaf, independent of the root it is reached from: {@code @TypeName}, else the simple name
     * without the simple name of the nearest sealed ancestor that is a suffix of it (direct parent first), in
     * kebab-case.
     */
    static String nameOf(final Class<?> leaf) {
        final TypeName override = leaf.getAnnotation(TypeName.class);
        if (override != null) {
            if (override.value().isBlank()) {
                throw new IllegalArgumentException("@TypeName on " + leaf.getName() + " must not be blank");
            }
            return override.value();
        }
        final String simple = leaf.getSimpleName();
        Set<Class<?>> level = Set.of(leaf);
        while (!level.isEmpty()) {
            final Set<Class<?>> next = new LinkedHashSet<>();
            for (final Class<?> type : level) {
                final List<Class<?>> parents = new ArrayList<>(List.of(type.getInterfaces()));
                if (type.getSuperclass() != null) {
                    parents.add(type.getSuperclass());
                }
                for (final Class<?> parent : parents) {
                    if (!parent.isSealed()) {
                        continue;
                    }
                    final String suffix = parent.getSimpleName();
                    if (simple.length() > suffix.length() && simple.endsWith(suffix)) {
                        return Names.kebab(simple.substring(0, simple.length() - suffix.length()));
                    }
                    next.add(parent);
                }
            }
            level = next;
        }
        return Names.kebab(simple);
    }

    @Override
    public Object decode(final Object raw, final DecodeContext ctx) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw ctx.expected("a section", raw);
        }
        final DecodeContext at = ctx.at(DISCRIMINATOR);
        final Object tag = map.get(DISCRIMINATOR);
        if (tag == null) {
            throw at.fail("missing (one of: " + String.join(", ", codecs.keySet()) + ")");
        }
        if (!(tag instanceof CharSequence)) {
            throw at.expected("a string", tag);
        }
        final Codec<Object> codec = codecs.get(tag.toString());
        if (codec == null) {
            throw at.fail("unknown type '" + tag + "', valid types: " + String.join(", ", codecs.keySet()));
        }
        final Map<Object, Object> rest = new LinkedHashMap<>(map);
        rest.remove(DISCRIMINATOR);
        return codec.decode(rest, ctx);
    }

    @Override
    public Object encode(final Object value) {
        final String name = names.get(value.getClass());
        if (name == null) {
            throw new IllegalArgumentException(value.getClass().getName() + " is not a permitted subtype of "
                    + type.getName());
        }
        final Object encoded = codecs.get(name).encode(value);
        if (!(encoded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("The codec of " + value.getClass().getName()
                    + " must encode to a map to be used in a sealed type");
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put(DISCRIMINATOR, name);
        for (final Map.Entry<?, ?> entry : map.entrySet()) {
            out.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return out;
    }
}
