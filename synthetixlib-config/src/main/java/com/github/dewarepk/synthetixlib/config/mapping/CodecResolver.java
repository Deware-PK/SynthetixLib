package com.github.dewarepk.synthetixlib.config.mapping;

import com.github.dewarepk.synthetixlib.config.codec.Codec;
import com.github.dewarepk.synthetixlib.config.codec.DecodeContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a {@link TypeRef} into a {@link Codec}: custom/built-in codecs first, then {@code Optional}, {@code List},
 * {@code Set}, {@code Map<String,T>}, enums, records and sealed interfaces of records. Resolved codecs and record
 * metadata are cached in this instance (one per {@code Codecs}, never static). Unsupported types fail here with an
 * {@link IllegalArgumentException} on first use, before any data is touched.
 * Public only for the {@code codec} package; internal, do not use.
 */
@org.jetbrains.annotations.ApiStatus.Internal
public final class CodecResolver {

    private final Map<Class<?>, Codec<?>> registered;
    private final Object lock = new Object();
    private final Map<TypeRef, Codec<?>> ready = new ConcurrentHashMap<>();
    private final Map<TypeRef, Codec<?>> pending = new HashMap<>();   // guarded by lock, only while resolving
    private final Map<Class<?>, RecordMeta> metas = new HashMap<>();  // guarded by lock

    /** @param registered live view of the registered codecs (built-ins and custom), owned by {@code Codecs} */
    public CodecResolver(final Map<Class<?>, Codec<?>> registered) {
        this.registered = registered;
    }

    /** Forgets composite codecs; call after the set of registered codecs changed. */
    public void invalidate() {
        synchronized (lock) {
            ready.clear();
        }
    }

    /** @throws IllegalArgumentException if the type cannot be mapped */
    public Codec<?> resolve(final TypeRef ref) {
        final Codec<?> custom = registered.get(ref.raw());
        if (custom != null) {
            return custom;
        }
        final Codec<?> cached = ready.get(ref);
        if (cached != null) {
            return cached;
        }
        synchronized (lock) {
            try {
                final Codec<?> codec = inner(ref);
                ready.putAll(pending);
                return codec;
            } finally {
                pending.clear();
            }
        }
    }

    /** Decodes {@code raw}; {@code null} is accepted only for {@code Optional}. */
    public Object decode(final TypeRef ref, final Object raw, final DecodeContext ctx) {
        return Nulls.decode(resolve(ref), raw, ctx);
    }

    /** Encodes {@code value} using the codec of the declared type {@code ref}. */
    public Object encode(final TypeRef ref, final Object value) {
        return Nulls.encode(resolve(ref), value);
    }

    /**
     * Encodes by runtime class. Collections, maps and {@code Optional} are encoded element by element. A record
     * with exactly one top-most sealed ancestor is encoded through that ancestor, so it gets its {@code type} key;
     * a record under several unrelated sealed roots is written without {@code type} (encode with the declared type
     * to choose one).
     */
    public Object encodeDynamic(final Object value) {
        Objects.requireNonNull(value, "value");
        final Class<?> runtime = value instanceof Enum<?> constant ? constant.getDeclaringClass() : value.getClass();
        final Codec<?> custom = findRegistered(runtime);
        if (custom != null) {
            return Nulls.any(custom).encode(value);
        }
        if (value instanceof Optional<?> optional) {
            return optional.isPresent() ? encodeDynamic(optional.get()) : null;
        }
        if (value instanceof Collection<?> collection) {
            final List<Object> out = new ArrayList<>(collection.size());
            for (final Object item : collection) {
                out.add(item == null ? null : encodeDynamic(item));
            }
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            final Map<String, Object> out = new LinkedHashMap<>();
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("Only maps with String keys can be encoded, found key "
                            + entry.getKey());
                }
                out.put(key, entry.getValue() == null ? null : encodeDynamic(entry.getValue()));
            }
            return out;
        }
        final Class<?> root = runtime.isRecord() ? soleSealedRoot(runtime) : null;
        return Nulls.encode(resolve(TypeRef.of(root != null ? root : runtime)), value);
    }

    /** @return the only top-most sealed ancestor of {@code type}, or {@code null} if there are none or several */
    private static Class<?> soleSealedRoot(final Class<?> type) {
        final Set<Class<?>> sealed = new java.util.LinkedHashSet<>();
        collectSealed(type, sealed);
        Class<?> root = null;
        for (final Class<?> candidate : sealed) {
            final boolean topMost = sealed.stream().noneMatch(o -> o != candidate && o.isAssignableFrom(candidate));
            if (topMost) {
                if (root != null) {
                    return null;
                }
                root = candidate;
            }
        }
        return root;
    }

    private static void collectSealed(final Class<?> type, final Set<Class<?>> into) {
        final List<Class<?>> parents = new ArrayList<>(List.of(type.getInterfaces()));
        if (type.getSuperclass() != null) {
            parents.add(type.getSuperclass());
        }
        for (final Class<?> parent : parents) {
            if (parent.isSealed()) {
                into.add(parent);
            }
            collectSealed(parent, into);
        }
    }

    private Codec<?> findRegistered(final Class<?> type) {
        final Codec<?> exact = registered.get(type);
        if (exact != null) {
            return exact;
        }
        Class<?> best = null;
        for (final Class<?> key : registered.keySet()) {
            if (key.isAssignableFrom(type) && (best == null || best.isAssignableFrom(key))) {
                best = key;
            }
        }
        return best == null ? null : registered.get(best);
    }

    /** Builds the codec for {@code ref}; the lock is held. */
    private Codec<?> inner(final TypeRef ref) {
        final Class<?> raw = ref.raw();
        final Codec<?> custom = registered.get(raw);
        if (custom != null) {
            return custom;
        }
        final Codec<?> known = ready.getOrDefault(ref, pending.get(ref));
        if (known != null) {
            return known;
        }
        final Codec<?> codec;
        if (raw == Optional.class) {
            codec = new OptionalCodec(inner(ref.argument(0, "Optional")));
        } else if (raw == List.class) {
            codec = new ListCodec(inner(ref.argument(0, "List")), false);
        } else if (raw == Set.class) {
            codec = new ListCodec(inner(ref.argument(0, "Set")), true);
        } else if (raw == Map.class) {
            if (ref.argument(0, "Map").raw() != String.class) {
                throw new IllegalArgumentException("Only Map<String, T> is supported, found " + ref);
            }
            codec = new MapCodec(inner(ref.argument(1, "Map")));
        } else if (raw.isEnum()) {
            codec = EnumCodec.of(raw);
        } else if (raw.isRecord()) {
            final RecordCodec<Object> record = new RecordCodec<>(metas.computeIfAbsent(raw, RecordMeta::of), ref);
            pending.put(ref, record);
            record.link(this::inner);
            return record;
        } else if (raw.isSealed()) {
            final SealedCodec sealed = new SealedCodec(raw);
            pending.put(ref, sealed);
            sealed.link(this::inner);
            return sealed;
        } else {
            throw new IllegalArgumentException("No codec for " + ref + ". Use a record, an enum, a sealed interface "
                    + "of records, List/Set/Map<String,T>/Optional of those, or register a Codec for the class");
        }
        pending.put(ref, codec);
        return codec;
    }
}
