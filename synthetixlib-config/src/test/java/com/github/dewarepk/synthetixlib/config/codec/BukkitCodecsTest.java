package com.github.dewarepk.synthetixlib.config.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The parts of the Bukkit codecs that need no server; registry and item behaviour is covered by the self-tests. */
class BukkitCodecsTest {

    private static World world(final String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "World[" + name + "]";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private final World overworld = world("world");
    private final World nether = world("world_nether");
    private final LocationCodec locations = new LocationCodec(new LocationCodec.Worlds() {
        @Override
        public @Nullable World find(final String name) {
            return List.of(overworld, nether).stream().filter(w -> w.getName().equals(name)).findFirst().orElse(null);
        }

        @Override
        public List<String> names() {
            return List.of("world", "world_nether");
        }
    });
    private final Codecs codecs = Codecs.create().register(Location.class, locations);

    private static Map<String, Object> map(final Object... pairs) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((String) pairs[i], pairs[i + 1]);
        }
        return out;
    }

    private List<String> problems(final Object tree) {
        return assertThrows(DecodeException.class, () -> codecs.decode(tree, Location.class)).problems().stream()
                .map(Problem::toString).toList();
    }

    // --- Location ----------------------------------------------------------------------------------------------

    @Test
    void aLocationIsDecodedWithOptionalYawAndPitch() {
        final Location location = codecs.decode(map("world", "world", "x", 1.5, "y", 64, "z", -3), Location.class);

        assertSame(overworld, location.getWorld());
        assertEquals(1.5, location.getX());
        assertEquals(64.0, location.getY());
        assertEquals(-3.0, location.getZ());
        assertEquals(0f, location.getYaw());
        assertEquals(0f, location.getPitch());
    }

    @Test
    void aLocationRoundTripsIncludingYawAndPitch() {
        final Location original = new Location(nether, 10.25, 70, -0.5, 91.3f, -12.7f);

        final Object tree = codecs.encode(original);

        assertEquals(map("world", "world_nether", "x", 10.25, "y", 70.0, "z", -0.5, "yaw", 91.3, "pitch", -12.7), tree);
        assertEquals(original, codecs.decode(tree, Location.class));
        assertEquals(original, codecs.fromJson(codecs.toJson(original), Location.class));
    }

    @Test
    void anUnknownWorldIsAnErrorThatListsTheLoadedOnes() {
        assertEquals(List.of("world: unknown world \"end\" (loaded: world, world_nether)"),
                problems(map("world", "end", "x", 0, "y", 0, "z", 0)));
    }

    @Test
    void everyProblemOfALocationIsReported() {
        assertEquals(List.of("world: missing", "x: missing", "y: expected a number, got string \"high\"",
                        "pitch: expected a number, got boolean true"),
                problems(map("y", "high", "z", 1, "pitch", true)));
    }

    @Test
    void aLocationNeedsASectionAndWarnsAboutUnknownKeys() {
        assertEquals(List.of("expected a section with world, x, y, z, got string \"1,2,3\""), problems("1,2,3"));
        final DecodeContext ctx = codecs.newContext();
        codecs.decode(map("world", "world", "x", 0, "y", 0, "z", 0, "yaw", 1, "bonus", 1), Location.class, ctx);
        assertEquals(List.of("bonus: unknown key"), ctx.warnings().stream().map(Problem::toString).toList());
    }

    @Test
    void aLocationWithoutAWorldCannotBeEncoded() {
        assertThrows(IllegalArgumentException.class, () -> codecs.encode(new Location(null, 0, 0, 0)));
    }

    // --- ItemStack forms ---------------------------------------------------------------------------------------

    @Test
    void theBase64FormIsRecognisedByItsKey() {
        assertTrue(ItemStackCodec.isBase64(map("base64", "AAAA")));
        assertTrue(ItemStackCodec.isBase64(map("base64", "AAAA", "material", "stone")));
        assertFalse(ItemStackCodec.isBase64(map("material", "stone", "amount", 2)));
        assertFalse(ItemStackCodec.isBase64("base64"));
        assertFalse(ItemStackCodec.isBase64(List.of("base64")));
    }

    // --- Suggestions, keys, materials --------------------------------------------------------------------------

    @Test
    void suggestionsAreTheThreeClosestByEditDistance() {
        final List<String> keys = List.of("entity.player.levelup", "entity.player.hurt", "entity.player.burp",
                "block.anvil.land", "entity.player.attack.crit");

        assertEquals(List.of("entity.player.levelup", "entity.player.burp", "entity.player.hurt"),
                Suggestions.closest("entity.player.levelp", keys));
        assertEquals(List.of("entity.player.levelup"), Suggestions.closest("entity.player.levelp", keys).subList(0, 1));
        assertEquals(List.of(), Suggestions.closest("x", List.of()));
    }

    @Test
    void editDistanceIsLevenshtein() {
        assertEquals(0, Suggestions.distance("diamond", "diamond"));
        assertEquals(1, Suggestions.distance("diamond", "diamnd"));
        assertEquals(1, Suggestions.distance("dimond", "diamond"));
        assertEquals(3, Suggestions.distance("kitten", "sitting"));
        assertEquals(5, Suggestions.distance("", "stone"));
    }

    @Test
    void theUnknownMessageNamesWhatWasWrongAndWhatCouldBeMeant() {
        assertEquals("unknown sound \"levelp\", did you mean levelup, levelx?",
                Suggestions.unknown("sound", "levelp", List.of("levelx", "levelup", "something.else.entirely")).replace(", something.else.entirely", ""));
        assertEquals("unknown sound \"x\"", Suggestions.unknown("sound", "x", List.of()));
    }

    @Test
    void keysAreShortForMinecraftAndLowerCasedOnInput() {
        assertEquals("diamond", Keys.format(NamespacedKey.minecraft("diamond")));
        assertEquals("synthetix:thing", Keys.format(new NamespacedKey("synthetix", "thing")));
        assertEquals(NamespacedKey.minecraft("diamond"), Keys.parse(" Diamond "));
        assertEquals(NamespacedKey.minecraft("diamond"), Keys.parse("minecraft:DIAMOND"));
        assertEquals(new NamespacedKey("synthetix", "thing"), Keys.parse("synthetix:thing"));
        assertEquals(null, Keys.parse("not valid!"));
    }

    @Test
    void materialsAcceptKeysAndEnumNamesAndExplainMistakes() {
        final Codecs materials = Codecs.create().register(Material.class, new MaterialCodec());

        assertEquals(Material.DIAMOND, materials.decode("diamond", Material.class));
        assertEquals(Material.DIAMOND, materials.decode("minecraft:diamond", Material.class));
        assertEquals(Material.DIAMOND_SWORD, materials.decode("DIAMOND_SWORD", Material.class));
        assertEquals("diamond_sword", materials.encode(Material.DIAMOND_SWORD));
        assertEquals(Material.STONE, materials.fromJson(materials.toJson(Material.STONE), Material.class));

        final String message = assertThrows(DecodeException.class, () -> materials.decode("diamnd_sword", Material.class))
                .getMessage();
        assertTrue(message.startsWith("unknown material \"diamnd_sword\", did you mean diamond_sword"), message);
    }
}
