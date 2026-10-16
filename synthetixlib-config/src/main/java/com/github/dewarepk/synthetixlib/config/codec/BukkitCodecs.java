package com.github.dewarepk.synthetixlib.config.codec;

import com.github.dewarepk.synthetixlib.core.text.TextService;
import io.papermc.paper.registry.RegistryKey;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

/**
 * Codecs for Bukkit types: registry values by key ({@code Material}, {@code Sound}, {@code EntityType},
 * {@code Enchantment}, {@code PotionEffectType}, {@code Biome}), {@code Location} and {@code ItemStack}.
 * Registered by {@code Configs} into its {@link Codecs}; they need a server, so plain {@code Codecs.create()} has
 * none of them.
 *
 * @apiNote Internal: use {@code configs.codecs()}
 */
@org.jetbrains.annotations.ApiStatus.Internal
public final class BukkitCodecs {

    private BukkitCodecs() {
    }

    /**
     * Registers every Bukkit codec into {@code codecs}.
     *
     * @param text   parses the MiniMessage of item names and lore
     * @param server resolves world names
     */
    public static void register(final Codecs codecs, final TextService text, final Server server) {
        codecs.register(Material.class, new MaterialCodec());
        codecs.register(Sound.class, new RegistryCodec<>(RegistryKey.SOUND_EVENT, "sound", true));
        codecs.register(EntityType.class, new RegistryCodec<>(RegistryKey.ENTITY_TYPE, "entity type", false));
        codecs.register(Enchantment.class, new RegistryCodec<>(RegistryKey.ENCHANTMENT, "enchantment", false));
        codecs.register(PotionEffectType.class, new RegistryCodec<>(RegistryKey.MOB_EFFECT, "potion effect", false));
        codecs.register(Biome.class, new RegistryCodec<>(RegistryKey.BIOME, "biome", false));
        codecs.register(Location.class, new LocationCodec(new ServerWorlds(server)));
        codecs.register(ItemStack.class, new ItemStackCodec(codecs, text));
    }

    private record ServerWorlds(Server server) implements LocationCodec.Worlds {

        @Override
        public World find(final String name) {
            return server.getWorld(name);
        }

        @Override
        public List<String> names() {
            return server.getWorlds().stream().map(World::getName).toList();
        }
    }
}
