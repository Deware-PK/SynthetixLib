package com.github.dewarepk.synthetixlib.config.mapping;

import com.github.dewarepk.synthetixlib.config.annotation.Range;
import com.github.dewarepk.synthetixlib.config.codec.Codec;
import com.github.dewarepk.synthetixlib.config.codec.DecodeContext;
import com.github.dewarepk.synthetixlib.config.codec.DecodeException;
import com.github.dewarepk.synthetixlib.config.codec.Problem;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * A record as a data section: one key per component. Collects every problem (missing, invalid, out of range)
 * before failing, and warns about unknown keys.
 */
final class RecordCodec<T> implements Codec<T> {

    private record Slot(RecordMeta.Component component, Codec<Object> codec, boolean optional) {
    }

    private final RecordMeta meta;
    private final TypeRef ref;
    private List<Slot> slots = List.of();
    private Set<String> keys = Set.of();
    private volatile String sealedName;   // type name when this record is a leaf of a sealed type

    RecordCodec(final RecordMeta meta, final TypeRef ref) {
        this.meta = meta;
        this.ref = ref;
    }

    /** Resolves the component codecs. Two steps (construct, then link) so records may refer to themselves. */
    void link(final Function<TypeRef, Codec<?>> resolve) {
        final List<Slot> linked = new ArrayList<>();
        final Set<String> linkedKeys = new HashSet<>();
        for (final RecordMeta.Component component : meta.components()) {
            final TypeRef componentRef = ref.member(component.genericType());
            final boolean optional = componentRef.raw() == Optional.class;
            final Class<?> valueType = optional ? componentRef.argument(0, "Optional").raw() : componentRef.raw();
            checkAnnotations(component, valueType);
            linked.add(new Slot(component, Nulls.any(resolve.apply(componentRef)), optional));
            linkedKeys.add(component.key());
        }
        this.slots = List.copyOf(linked);
        this.keys = Set.copyOf(linkedKeys);
    }

    /** Marks this record as a sealed leaf, so a {@code type} key equal to its own name is not an unknown key. */
    void markSealedLeaf(final String name) {
        this.sealedName = name;
    }

    boolean hasKey(final String key) {
        return keys.contains(key);
    }

    private void checkAnnotations(final RecordMeta.Component component, final Class<?> valueType) {
        final String where = meta.type().getName() + "." + component.name();
        if (component.range() != null && !isNumeric(valueType)) {
            throw new IllegalArgumentException("@Range on " + where + " needs an int, long, float or double, not "
                    + valueType.getName());
        }
        if (component.notEmpty() && !CharSequence.class.isAssignableFrom(valueType)
                && !Collection.class.isAssignableFrom(valueType) && !Map.class.isAssignableFrom(valueType)) {
            throw new IllegalArgumentException("@NotEmpty on " + where + " needs a String, List, Set or Map, not "
                    + valueType.getName());
        }
    }

    private static boolean isNumeric(final Class<?> type) {
        return type == int.class || type == Integer.class || type == long.class || type == Long.class
                || type == double.class || type == Double.class || type == float.class || type == Float.class;
    }

    @Override
    public T decode(final Object raw, final DecodeContext ctx) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw ctx.expected("a section", raw);
        }
        final Object[] values = new Object[slots.size()];
        final List<Problem> problems = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            final Slot slot = slots.get(i);
            try {
                values[i] = decodeSlot(slot, map, ctx.at(slot.component().key()));
            } catch (final DecodeException e) {
                problems.addAll(e.problems());
            }
        }
        for (final Object key : map.keySet()) {
            final String name = String.valueOf(key);
            if (!keys.contains(name) && !isOwnDiscriminator(name, map.get(key))) {
                ctx.at(name).warn("unknown key");
            }
        }
        if (!problems.isEmpty()) {
            throw new DecodeException(problems);
        }
        return construct(values, ctx);
    }

    private boolean isOwnDiscriminator(final String key, final Object value) {
        return SealedCodec.DISCRIMINATOR.equals(key) && sealedName != null && sealedName.equals(String.valueOf(value));
    }

    private Object decodeSlot(final Slot slot, final Map<?, ?> map, final DecodeContext at) {
        final RecordMeta.Component component = slot.component();
        final Object raw = map.get(component.key());
        if (raw == null) {
            if (slot.optional()) {
                return Optional.empty();
            }
            if (component.nullable()) {
                return null;
            }
            throw at.fail(map.containsKey(component.key()) ? "must not be null" : "missing");
        }
        final Object value = slot.codec().decode(raw, at);
        validate(component, value, at);
        return value;
    }

    private static void validate(final RecordMeta.Component component, final Object decoded, final DecodeContext at) {
        final Object value = decoded instanceof Optional<?> optional ? optional.orElse(null) : decoded;
        if (value == null) {
            return;
        }
        final Range range = component.range();
        if (range != null && value instanceof Number number) {
            final double d = number.doubleValue();
            if (Double.isNaN(d) || d < range.min() || d > range.max()) {
                throw at.fail(rangeMessage(range, number));
            }
        }
        if (component.notEmpty() && isEmpty(value)) {
            throw at.fail("must not be empty");
        }
    }

    private static boolean isEmpty(final Object value) {
        if (value instanceof CharSequence text) {
            return text.length() == 0;
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        return value instanceof Map<?, ?> map && map.isEmpty();
    }

    private static String rangeMessage(final Range range, final Number was) {
        final boolean hasMin = range.min() != Double.NEGATIVE_INFINITY;
        final boolean hasMax = range.max() != Double.POSITIVE_INFINITY;
        final String bounds;
        if (hasMin && hasMax) {
            bounds = "between " + format(range.min()) + " and " + format(range.max());
        } else if (hasMin) {
            bounds = ">= " + format(range.min());
        } else if (hasMax) {
            bounds = "<= " + format(range.max());
        } else {
            bounds = "a valid number";
        }
        return "must be " + bounds + " (was " + was + ")";
    }

    private static String format(final double bound) {
        return bound == Math.rint(bound) && Math.abs(bound) < 1e15 ? Long.toString((long) bound) : Double.toString(bound);
    }

    @SuppressWarnings("unchecked")
    private T construct(final Object[] values, final DecodeContext ctx) {
        try {
            return (T) meta.constructor().newInstance(values);
        } catch (final InvocationTargetException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            throw ctx.fail(cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());
        } catch (final ReflectiveOperationException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot construct record " + meta.type().getName(), e);
        }
    }

    @Override
    public Object encode(final T value) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final Slot slot : slots) {
            final RecordMeta.Component component = slot.component();
            final Object componentValue = read(component, value);
            if (componentValue == null) {
                if (slot.optional() || component.nullable()) {
                    continue;
                }
                throw new IllegalArgumentException(meta.type().getName() + "." + component.name()
                        + " is null; mark it @Nullable or use Optional");
            }
            final Object encoded = slot.codec().encode(componentValue);
            if (encoded != null) {
                out.put(component.key(), encoded);
            }
        }
        return out;
    }

    private Object read(final RecordMeta.Component component, final Object record) {
        try {
            return component.accessor().invoke(record);
        } catch (final InvocationTargetException e) {
            throw new IllegalStateException(meta.type().getName() + "." + component.name() + "() threw", e.getCause());
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read " + meta.type().getName() + "." + component.name(), e);
        }
    }
}
