package com.github.dewarepk.synthetixexample.selftest;

import static com.github.dewarepk.synthetixexample.selftest.SelfTest.require;

import com.github.dewarepk.synthetixlib.config.Configs;
import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.config.codec.DecodeException;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

/** Self-tests for the Bukkit codecs of synthetixlib-config (registry types, Location, ItemStack). */
final class ConfigBukkitSelfTests {

    private ConfigBukkitSelfTests() {
    }

    static List<SelfTest> all(final JavaPlugin plugin, final SynthetixLib lib) {
        final Configs configs = Configs.create(lib);
        final Codecs codecs = configs.codecs();
        return List.of(
                SelfTest.sync("config.codec.material", () -> {
                    require(codecs.decode("diamond", Material.class) == Material.DIAMOND, "short key");
                    require(codecs.decode("minecraft:diamond", Material.class) == Material.DIAMOND, "full key");
                    require(codecs.decode("DIAMOND_SWORD", Material.class) == Material.DIAMOND_SWORD, "enum name");
                    require("diamond_sword".equals(codecs.encode(Material.DIAMOND_SWORD)), "written in short form");
                    int checked = 0;
                    for (final Material material : Material.values()) {
                        if (!material.isLegacy()) {
                            require(codecs.decode(codecs.encode(material), Material.class) == material,
                                    "round trip of " + material);
                            checked++;
                        }
                    }
                    require(checked > 1000, "only " + checked + " materials checked");
                }),
                SelfTest.sync("config.codec.sound", () -> {
                    require(codecs.decode("entity.player.levelup", Sound.class) == Sound.ENTITY_PLAYER_LEVELUP, "key");
                    require(codecs.decode("minecraft:entity.player.levelup", Sound.class) == Sound.ENTITY_PLAYER_LEVELUP,
                            "full key");
                    require(codecs.decode("ENTITY_PLAYER_LEVELUP", Sound.class) == Sound.ENTITY_PLAYER_LEVELUP,
                            "old enum style");
                    require("entity.player.levelup".equals(codecs.encode(Sound.ENTITY_PLAYER_LEVELUP)), "short form");
                    roundTrip(codecs, RegistryKey.SOUND_EVENT, Sound.class);
                }),
                SelfTest.sync("config.codec.registries", () -> {
                    require(codecs.decode("zombie", EntityType.class) == EntityType.ZOMBIE, "entity type");
                    require(codecs.decode("SHARPNESS", Enchantment.class) == Enchantment.SHARPNESS, "enchantment");
                    require(codecs.decode("speed", PotionEffectType.class) == PotionEffectType.SPEED, "potion effect");
                    require(codecs.decode("plains", Biome.class) == Biome.PLAINS, "biome");
                    roundTrip(codecs, RegistryKey.ENTITY_TYPE, EntityType.class);
                    roundTrip(codecs, RegistryKey.ENCHANTMENT, Enchantment.class);
                    roundTrip(codecs, RegistryKey.MOB_EFFECT, PotionEffectType.class);
                    roundTrip(codecs, RegistryKey.BIOME, Biome.class);
                }),
                SelfTest.sync("config.codec.unknown-key-suggestion", () -> {
                    require(message(codecs, "entity.player.levelp", Sound.class).contains("entity.player.levelup"),
                            "sound suggestion: " + message(codecs, "entity.player.levelp", Sound.class));
                    require(message(codecs, "diamnd_sword", Material.class).contains("diamond_sword"),
                            "material suggestion: " + message(codecs, "diamnd_sword", Material.class));
                    require(message(codecs, "zombi", EntityType.class).contains("zombie"), "entity suggestion");
                    require(message(codecs, "nope:nope", Biome.class).startsWith("unknown biome \"nope:nope\""),
                            "foreign namespace");
                    require(message(codecs, "bad key!", Enchantment.class).startsWith("unknown enchantment"),
                            "malformed key is an error, not an exception");
                }),
                SelfTest.sync("config.codec.location", () -> {
                    final World world = Bukkit.getWorlds().get(0);
                    final Location original = new Location(world, 1.5, 64, -3.25, 90.5f, -10.25f);
                    final Object tree = codecs.encode(original);
                    require(codecs.decode(tree, Location.class).equals(original), "tree round trip: " + tree);
                    require(codecs.fromJson(codecs.toJson(original), Location.class).equals(original), "JSON round trip");
                    final Location plain = codecs.decode(
                            Map.of("world", world.getName(), "x", 1, "y", 2, "z", 3), Location.class);
                    require(plain.getYaw() == 0f && plain.getPitch() == 0f, "yaw and pitch are optional");
                    final String error = message(codecs, Map.of("world", "no_such_world", "x", 0, "y", 0, "z", 0),
                            Location.class);
                    require(error.contains("unknown world \"no_such_world\"") && error.contains(world.getName()),
                            "unknown world: " + error);
                }),
                SelfTest.sync("config.codec.item.simple", () -> {
                    final ItemStack item = lib.items().of(Material.DIAMOND)
                            .name("<gold>Shiny <bold>gem")
                            .lore("<gray>first line")
                            .lore("<red>second line")
                            .itemModel(new NamespacedKey("synthetix", "gem"))
                            .glint(true)
                            .amount(3)
                            .build();
                    final Object tree = codecs.encode(item);
                    require(tree instanceof Map<?, ?> map && !map.containsKey("base64") && "diamond".equals(map.get("material"))
                            && Integer.valueOf(3).equals(map.get("amount")), "readable form expected, got " + tree);
                    final ItemStack back = codecs.decode(tree, ItemStack.class);
                    require(back.isSimilar(item) && back.getAmount() == 3, "exact round trip of " + tree);
                    require(codecs.fromJson(codecs.toJson(item), ItemStack.class).isSimilar(item), "JSON round trip");
                    final ItemStack bare = codecs.decode(Map.of("material", "STONE"), ItemStack.class);
                    require(bare.getType() == Material.STONE && bare.getAmount() == 1 && !bare.hasItemMeta(), "minimal form");
                    require(codecs.encode(new ItemStack(Material.STONE, 2)) instanceof Map<?, ?> m && !m.containsKey("base64"),
                            "an item without meta is readable");
                }),
                SelfTest.sync("config.codec.item.complex", () -> {
                    final ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
                    book.editMeta(EnchantmentStorageMeta.class, meta -> meta.addStoredEnchant(Enchantment.SHARPNESS, 3, true));
                    final Object bookTree = codecs.encode(book);
                    require(bookTree instanceof Map<?, ?> m && m.size() == 1 && m.get("base64") instanceof String,
                            "stored enchantments need the base64 form, got " + bookTree);
                    require(codecs.decode(bookTree, ItemStack.class).isSimilar(book), "exact round trip of the book");

                    final ItemStack sword = lib.items().of(Material.DIAMOND_SWORD).name("<red>Blade").build();
                    sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 7);
                    sword.editMeta(Damageable.class, meta -> meta.setDamage(12));
                    final Object swordTree = codecs.encode(sword);
                    require(swordTree instanceof Map<?, ?> m && m.containsKey("base64"), "enchanted sword: " + swordTree);
                    final ItemStack swordBack = codecs.fromJson(codecs.toJson(sword), ItemStack.class);
                    require(swordBack.isSimilar(sword) && swordBack.getEnchantmentLevel(Enchantment.SHARPNESS) == 7,
                            "exact round trip of the sword");

                    require(message(codecs, Map.of("material", "water"), ItemStack.class).contains("not an item"),
                            "WATER is not an item");
                    require(message(codecs, Map.of("material", "stone", "amount", 100), ItemStack.class)
                            .contains("between 1 and 99"), "amount range");
                    require(message(codecs, Map.of("base64", "!!!"), ItemStack.class).contains("base64"),
                            "broken base64 is an error");
                })
        );
    }

    private static <T extends Keyed> void roundTrip(final Codecs codecs, final RegistryKey<T> key, final Class<T> type) {
        final List<T> values = RegistryAccess.registryAccess().getRegistry(key).stream().toList();
        require(!values.isEmpty(), "registry " + key + " is empty");
        for (final T value : values) {
            require(codecs.decode(codecs.encode(value), type).equals(value), "round trip of " + value.getKey());
        }
    }

    /** The message of the {@code DecodeException} that decoding {@code raw} must throw. */
    private static String message(final Codecs codecs, final Object raw, final Class<?> type) {
        try {
            codecs.decode(raw, type);
        } catch (final DecodeException e) {
            return e.getMessage();
        }
        throw new AssertionError(raw + " must not decode as " + type.getSimpleName());
    }
}
