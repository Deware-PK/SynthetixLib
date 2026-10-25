package com.github.dewarepk.synthetixlib.config.codec;

import com.github.dewarepk.synthetixlib.config.mapping.CodecResolver;
import com.github.dewarepk.synthetixlib.config.mapping.LenientResult;
import com.github.dewarepk.synthetixlib.config.mapping.TypeRef;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Object to data tree conversion, independent of files: built-in codecs, custom codecs, and automatic mapping of
 * records, enums, sealed interfaces of records and {@code List}/{@code Set}/{@code Map<String,T>}/{@code Optional}.
 * The data tree consists of {@code Map<String,Object>} (insertion ordered), {@code List<Object>}, {@code String},
 * {@code Number} and {@code Boolean}. Thread-safe. Obtain the shared instance from {@code configs.codecs()}.
 *
 * <p>Records must be {@code public}. Keys are the component names in kebab-case ({@code minBid} becomes
 * {@code min-bid}, override with {@code @Key}). A sealed interface of records gets a {@code type} key:
 * {@code ItemReward implements Reward} is written as {@code type: item} (override with {@code @TypeName}); the name
 * does not depend on which sealed root is declared. Nullable components: use {@code org.jspecify.annotations.Nullable}
 * (or {@code Optional<T>}); {@code org.jetbrains.annotations.Nullable} is not visible at runtime and is ignored.
 *
 * <pre>{@code
 * Codecs codecs = configs.codecs();
 * Object tree = codecs.encode(airdrop);                         // Map / List / String / Number / Boolean
 * Airdrop back = codecs.decode(tree, Airdrop.class);            // throws DecodeException listing all problems
 * List<Reward> rewards = codecs.decode(raw, new TypeToken<List<Reward>>() {});
 * String json = codecs.toJson(airdrop);
 * Airdrop again = codecs.fromJson(json, Airdrop.class);
 * }</pre>
 */
public final class Codecs {

    private final Map<Class<?>, Codec<?>> registered = new ConcurrentHashMap<>();
    private final CodecResolver resolver = new CodecResolver(registered);

    private Codecs() {
        ScalarCodecs.registerAll(registered);
    }

    /** @return a registry with the built-in codecs; independent of any other instance */
    public static Codecs create() {
        return new Codecs();
    }

    /**
     * Registers a codec for {@code type}, replacing a built-in or an earlier one. Does not apply to codecs you
     * already hold; register during startup, before decoding.
     *
     * <pre>{@code
     * codecs.register(Region.class, Codec.string(Region::parse, Region::serialize));
     * }</pre>
     *
     * @return this
     * @throws IllegalArgumentException for {@code Optional}, {@code List}, {@code Set} and {@code Map}
     */
    public <T> Codecs register(final Class<T> type, final Codec<T> codec) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(codec, "codec");
        if (type == Optional.class || type == List.class || type == Set.class || type == Map.class) {
            throw new IllegalArgumentException("Containers cannot be registered; register their element type");
        }
        registered.put(type, codec);
        resolver.invalidate();
        return this;
    }

    /** @return a fresh context, to collect {@linkplain DecodeContext#warnings() warnings} of a decode call */
    public DecodeContext newContext() {
        return new DecodeContext(this);
    }

    // --- tree -------------------------------------------------------------------------------------------------

    /**
     * Encodes by runtime class. Lists, sets, maps and {@code Optional} are encoded element by element. To encode a
     * value of a sealed interface with its {@code type} key, use {@link #encode(Object, Class)}.
     *
     * @throws IllegalArgumentException if a value cannot be encoded (unsupported type, {@code null} component)
     */
    public Object encode(final Object value) {
        return resolver.encodeDynamic(value);
    }

    /** Encodes {@code value} as the declared type {@code type} (e.g. a sealed interface). */
    public <T> Object encode(final T value, final Class<T> type) {
        return resolver.encode(TypeRef.of(type), Objects.requireNonNull(value, "value"));
    }

    /** Encodes {@code value} as the declared generic type, e.g. {@code new TypeToken<List<Reward>>() {}}. */
    public <T> Object encode(final T value, final TypeToken<T> type) {
        return resolver.encode(type.ref(), Objects.requireNonNull(value, "value"));
    }

    /**
     * @param raw  a data tree
     * @param type target type
     * @throws DecodeException          listing every problem if {@code raw} does not fit
     * @throws IllegalArgumentException if {@code type} cannot be mapped
     */
    public <T> T decode(final Object raw, final Class<T> type) {
        return decode(raw, type, newContext());
    }

    /** Like {@link #decode(Object, Class)}, collecting warnings in {@code ctx} and using its path. */
    public <T> T decode(final Object raw, final Class<T> type, final DecodeContext ctx) {
        return decodeRef(raw, TypeRef.of(Objects.requireNonNull(type, "type")), ctx);
    }

    /** Decodes into a generic type, e.g. {@code new TypeToken<List<Map<String, Zone>>>() {}}. */
    public <T> T decode(final Object raw, final TypeToken<T> type) {
        return decode(raw, type, newContext());
    }

    /** Like {@link #decode(Object, TypeToken)}, collecting warnings in {@code ctx} and using its path. */
    public <T> T decode(final Object raw, final TypeToken<T> type, final DecodeContext ctx) {
        return decodeRef(raw, Objects.requireNonNull(type, "type").ref(), ctx);
    }

    @SuppressWarnings("unchecked")
    private <T> T decodeRef(final Object raw, final TypeRef ref, final DecodeContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        return (T) resolver.decode(ref, raw, ctx);
    }

    /**
     * Decodes file data where a missing or invalid field takes its value from {@code defaults} instead of failing,
     * recursing into nested records and maps. Never throws for bad data.
     *
     * @param defaults a non-null value of {@code type}
     * @apiNote Internal: used by the YAML layer; use {@link #decode(Object, Class)} for strict decoding
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    @SuppressWarnings("unchecked")
    public <T> LenientResult<T> decodeLenient(final Object raw, final Class<T> type, final T defaults) {
        Objects.requireNonNull(defaults, "defaults");
        return (LenientResult<T>) (LenientResult<?>) resolver.decodeLenient(
                TypeRef.of(Objects.requireNonNull(type, "type")), raw, defaults, newContext());
    }

    /**
     * Like {@link #decodeLenient(Object, Class, Object)}, and each entry a map of the data lacks is added from
     * {@code defaults}, with the value of {@code supplement} (a data tree of the same shape) where it has one.
     *
     * @apiNote Internal: used by the YAML layer for locale files
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    @SuppressWarnings("unchecked")
    public <T> LenientResult<T> decodeLenientFillingMaps(final Object raw, final Class<T> type, final T defaults,
                                                         final Map<String, Object> supplement) {
        Objects.requireNonNull(defaults, "defaults");
        Objects.requireNonNull(supplement, "supplement");
        return (LenientResult<T>) (LenientResult<?>) resolver.decodeLenientFillingMaps(
                TypeRef.of(Objects.requireNonNull(type, "type")), raw, defaults, newContext(), supplement);
    }

    // --- JSON -------------------------------------------------------------------------------------------------

    /**
     * Compact JSON of {@link #encode(Object)}.
     *
     * @throws IllegalArgumentException if the value cannot be encoded or holds NaN / infinity
     */
    public String toJson(final Object value) {
        return Json.write(encode(value));
    }

    /** Compact JSON of {@link #encode(Object, Class)}. */
    public <T> String toJson(final T value, final Class<T> type) {
        return Json.write(encode(value, type));
    }

    /** Compact JSON of {@link #encode(Object, TypeToken)}. */
    public <T> String toJson(final T value, final TypeToken<T> type) {
        return Json.write(encode(value, type));
    }

    /**
     * @throws DecodeException if {@code json} is malformed or does not fit {@code type}
     */
    public <T> T fromJson(final String json, final Class<T> type) {
        final DecodeContext ctx = newContext();
        return decode(Json.read(Objects.requireNonNull(json, "json"), ctx), type, ctx);
    }

    /** Like {@link #fromJson(String, Class)} for a generic type. */
    public <T> T fromJson(final String json, final TypeToken<T> type) {
        final DecodeContext ctx = newContext();
        return decode(Json.read(Objects.requireNonNull(json, "json"), ctx), type, ctx);
    }
}
