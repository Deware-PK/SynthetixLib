package com.github.dewarepk.synthetixlib.config.codec;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;

/** Built-in codecs for scalar types; registered into every {@link Codecs}. */
final class ScalarCodecs {

    private static final Pattern UUID_FORMAT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final double LONG_LIMIT = 9.0e18;

    private ScalarCodecs() {
    }

    static void registerAll(final Map<Class<?>, Codec<?>> target) {
        final Codec<Boolean> bool = new FunctionCodec<>((raw, ctx) -> {
            if (raw instanceof Boolean b) {
                return b;
            }
            throw ctx.expected("a boolean (true or false)", raw);
        }, b -> b);
        both(target, boolean.class, Boolean.class, bool);

        final Codec<Integer> integer = new FunctionCodec<>(
                (raw, ctx) -> (int) integral(raw, ctx, "int", Integer.MIN_VALUE, Integer.MAX_VALUE), i -> i);
        both(target, int.class, Integer.class, integer);

        final Codec<Long> longs = new FunctionCodec<>(
                (raw, ctx) -> integral(raw, ctx, "long", Long.MIN_VALUE, Long.MAX_VALUE), l -> l);
        both(target, long.class, Long.class, longs);

        final Codec<Double> doubles = new FunctionCodec<>((raw, ctx) -> floating(raw, ctx), d -> d);
        both(target, double.class, Double.class, doubles);

        final Codec<Float> floats = new FunctionCodec<>((raw, ctx) -> {
            final double d = floating(raw, ctx);
            final float f = (float) d;
            if (Float.isInfinite(f) && !Double.isInfinite(d)) {
                throw ctx.fail("out of range for float (was " + raw + ")");
            }
            return f;
        }, f -> f);
        both(target, float.class, Float.class, floats);

        target.put(String.class, new FunctionCodec<String>(ScalarCodecs::text, s -> s));

        target.put(Duration.class, new FunctionCodec<Duration>(
                (raw, ctx) -> Durations.parse(text(raw, ctx)), Durations::format));

        target.put(UUID.class, new FunctionCodec<UUID>((raw, ctx) -> {
            final String text = text(raw, ctx);
            if (!UUID_FORMAT.matcher(text).matches()) {
                throw ctx.fail("invalid UUID \"" + text + "\" (expected xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)");
            }
            return UUID.fromString(text);
        }, UUID::toString));

        target.put(NamespacedKey.class, new FunctionCodec<NamespacedKey>((raw, ctx) -> {
            final String text = text(raw, ctx);
            final NamespacedKey key = NamespacedKey.fromString(text);
            if (key == null) {
                throw ctx.fail("invalid key \"" + text + "\" (expected namespace:key, using a-z 0-9 _ - . /)");
            }
            return key;
        }, NamespacedKey::toString));

        target.put(Key.class, new FunctionCodec<Key>(
                (raw, ctx) -> Key.key(text(raw, ctx).strip()), Key::asString));
    }

    /** A string, or a number / boolean in its text form. */
    static String text(final Object raw, final DecodeContext ctx) {
        if (raw instanceof CharSequence text) {
            return text.toString();
        }
        if (raw instanceof Number || raw instanceof Boolean) {
            return String.valueOf(raw);
        }
        throw ctx.expected("a string", raw);
    }

    private static <T> void both(final Map<Class<?>, Codec<?>> target, final Class<?> primitive,
                                 final Class<T> boxed, final Codec<T> codec) {
        target.put(primitive, codec);
        target.put(boxed, codec);
    }

    /** Whole number within {@code [min, max]}; whole floating point values (5.0) are accepted. */
    private static long integral(final Object raw, final DecodeContext ctx, final String type,
                                 final long min, final long max) {
        final long value;
        if (raw instanceof Integer || raw instanceof Long || raw instanceof Short || raw instanceof Byte) {
            value = ((Number) raw).longValue();
        } else if (raw instanceof BigInteger big) {
            if (big.bitLength() > 63) {
                throw ctx.fail("out of range for " + type + " (was " + raw + ")");
            }
            value = big.longValue();
        } else if (raw instanceof Number number) {
            final double d = number.doubleValue();
            if (!Double.isFinite(d) || d != Math.rint(d)) {
                throw ctx.expected("an integer", raw);
            }
            if (Math.abs(d) >= LONG_LIMIT) {
                throw ctx.fail("out of range for " + type + " (was " + raw + ")");
            }
            value = (long) d;
        } else {
            throw ctx.expected("an integer", raw);
        }
        if (value < min || value > max) {
            throw ctx.fail("out of range for " + type + " (" + min + " to " + max + ", was " + value + ")");
        }
        return value;
    }

    private static double floating(final Object raw, final DecodeContext ctx) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        throw ctx.expected("a number", raw);
    }
}
