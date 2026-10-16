package com.github.dewarepk.synthetixlib.config.codec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

/**
 * {@link Location} as {@code {world, x, y, z, yaw, pitch}}; yaw and pitch are optional (0). The world is stored by
 * name. A world that is not loaded is a decode error, so the config layer keeps the text in the file.
 */
final class LocationCodec implements Codec<Location> {

    /** The worlds of the server; a seam so parsing needs no running server. */
    interface Worlds {

        @Nullable World find(String name);

        List<String> names();
    }

    private final Worlds worlds;

    LocationCodec(final Worlds worlds) {
        this.worlds = worlds;
    }

    @Override
    public Location decode(final Object raw, final DecodeContext ctx) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw ctx.expected("a section with world, x, y, z", raw);
        }
        final List<Problem> problems = new ArrayList<>();
        final World world = attempt(problems, () -> world(map.get("world"), ctx.at("world")));
        final Double x = attempt(problems, () -> required(map, "x", ctx));
        final Double y = attempt(problems, () -> required(map, "y", ctx));
        final Double z = attempt(problems, () -> required(map, "z", ctx));
        final Float yaw = attempt(problems, () -> optional(map, "yaw", ctx));
        final Float pitch = attempt(problems, () -> optional(map, "pitch", ctx));
        for (final Object key : map.keySet()) {
            if (!isKnown(String.valueOf(key))) {
                ctx.at(String.valueOf(key)).warn("unknown key");
            }
        }
        if (!problems.isEmpty()) {
            throw new DecodeException(problems);
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    private static boolean isKnown(final String key) {
        return switch (key) {
            case "world", "x", "y", "z", "yaw", "pitch" -> true;
            default -> false;
        };
    }

    private World world(final Object raw, final DecodeContext at) {
        if (raw == null) {
            throw at.fail("missing");
        }
        final String name = ScalarCodecs.text(raw, at).strip();
        final World world = worlds.find(name);
        if (world == null) {
            throw at.fail("unknown world \"" + name + "\" (loaded: " + String.join(", ", worlds.names()) + ")");
        }
        return world;
    }

    private static Double required(final Map<?, ?> map, final String key, final DecodeContext ctx) {
        final Object raw = map.get(key);
        if (raw == null) {
            throw ctx.at(key).fail("missing");
        }
        return ctx.at(key).decode(raw, double.class);
    }

    private static Float optional(final Map<?, ?> map, final String key, final DecodeContext ctx) {
        final Object raw = map.get(key);
        return raw == null ? Float.valueOf(0f) : ctx.at(key).decode(raw, float.class);
    }

    private static <V> @Nullable V attempt(final List<Problem> problems, final Supplier<V> read) {
        try {
            return read.get();
        } catch (final DecodeException e) {
            problems.addAll(e.problems());
            return null;
        }
    }

    @Override
    public Object encode(final Location value) {
        final World world = value.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("A location without a world cannot be saved");
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("world", world.getName());
        out.put("x", value.getX());
        out.put("y", value.getY());
        out.put("z", value.getZ());
        out.put("yaw", shortest(value.getYaw()));
        out.put("pitch", shortest(value.getPitch()));
        return out;
    }

    /** {@code 0.1f} as {@code 0.1} instead of {@code 0.10000000149011612}; decodes back to the same float. */
    private static double shortest(final float value) {
        return Double.parseDouble(Float.toString(value));
    }
}
