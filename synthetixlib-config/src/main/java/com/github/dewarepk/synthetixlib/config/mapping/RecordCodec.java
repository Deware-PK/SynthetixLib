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
final class RecordCodec<T> implements Codec<T>, LenientCodec {

    private record Slot(RecordMeta.Component component, Codec<Object> codec, boolean optional) {
    }

    private final RecordMeta meta;
    private final TypeRef ref;
    private List<Slot> slots = List.of();
    private Set<String> keys = Set.of();
    private String sealedName;   // type name when this record is a leaf of a sealed type; set in link

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
        // A sealed leaf may be decoded directly, so a `type` key equal to its own name is not an unknown key.
        this.sealedName = SealedCodec.hasSealedAncestor(meta.type()) ? SealedCodec.nameOf(meta.type()) : null;
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
        warnUnknownKeys(map, ctx);
        if (!problems.isEmpty()) {
            throw new DecodeException(problems);
        }
        return construct(values, ctx);
    }

    /**
     * Like {@link #decode} but a missing or invalid component takes the value from {@code defaults} (a record of
     * this type) instead of failing; nested records and maps recurse. Missing keys are reported so the YAML layer
     * can add them to the file.
     */
    @Override
    public Object decodeLenient(final Object raw, final Object defaults, final DecodeContext ctx,
                                final LenientReport report) {
        if (!(raw instanceof Map<?, ?> map)) {
            report.invalid(ctx.expected("a section", raw), "using default");
            return defaults;
        }
        final Object[] values = new Object[slots.size()];
        for (int i = 0; i < values.length; i++) {
            final Slot slot = slots.get(i);
            final Object fallback = read(slot.component(), defaults);
            report.enter(slot.component().key());
            try {
                values[i] = decodeSlotLenient(slot, map, ctx.at(slot.component().key()), fallback, report);
            } catch (final DecodeException e) {
                report.invalid(e, using(slot, fallback));
                values[i] = fallback;
            } finally {
                report.exit();
            }
        }
        warnUnknownKeys(map, ctx);
        try {
            return construct(values, ctx);
        } catch (final DecodeException e) {
            report.invalid(e, "using default");
            return defaults;
        }
    }

    private Object decodeSlotLenient(final Slot slot, final Map<?, ?> map, final DecodeContext at,
                                     final Object fallback, final LenientReport report) {
        final RecordMeta.Component component = slot.component();
        if (!map.containsKey(component.key())) {
            final Object encoded = fallback == null ? null : encodeSlot(slot, fallback);
            if (encoded != null) {
                report.missing(encoded);
            }
            return fallback;
        }
        if (map.get(component.key()) == null) {
            return decodeSlot(slot, map, at);   // empty value: Optional / @Nullable accept it, others fail
        }
        final Object raw = map.get(component.key());
        final Object value = fallback != null && slot.codec() instanceof LenientCodec lenient
                ? lenient.decodeLenient(raw, fallback, at, report)
                : slot.codec().decode(raw, at);
        validate(component, value, at);
        return value;
    }

    private String using(final Slot slot, final Object fallback) {
        return "using " + (fallback == null ? Preview.of(null) : Preview.of(encodeSlot(slot, fallback)));
    }

    private Object encodeSlot(final Slot slot, final Object value) {
        try {
            return slot.codec().encode(value);
        } catch (final IllegalArgumentException e) {
            return String.valueOf(value);
        }
    }

    private void warnUnknownKeys(final Map<?, ?> map, final DecodeContext ctx) {
        for (final Object key : map.keySet()) {
            final String name = String.valueOf(key);
            if (!keys.contains(name) && !isOwnDiscriminator(name, map.get(key))) {
                ctx.at(name).warn("unknown key");
            }
        }
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
