package com.github.dewarepk.synthetixlib.gui;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.jspecify.annotations.Nullable;

/**
 * Which items an input slot accepts, from the {@code accept} list of its menu item: {@code '*'} for everything, a
 * material name ({@code diamond}), or an item tag ({@code '#minecraft:swords'}). An empty list accepts everything. A
 * tag is looked up when an item is checked, so a tag a datapack adds later works; a tag that does not exist accepts
 * nothing.
 */
final class AcceptFilter {

    /** Finds an item tag; {@code null} if there is none with that key. */
    @FunctionalInterface
    interface TagLookup {

        @Nullable Tag find(NamespacedKey key);

        /** An item tag. */
        interface Tag {

            boolean contains(Material material);
        }
    }

    /**
     * @param filter   what was understood; entries that were not understood are left out
     * @param problems one message per entry that was not understood
     */
    record Parsed(AcceptFilter filter, List<String> problems) {
    }

    private final boolean everything;
    private final Set<Material> materials;
    private final List<NamespacedKey> tags;

    private AcceptFilter(final boolean everything, final Set<Material> materials, final List<NamespacedKey> tags) {
        this.everything = everything;
        this.materials = materials;
        this.tags = tags;
    }

    static Parsed parse(final List<String> entries) {
        final List<String> problems = new ArrayList<>();
        final Set<Material> materials = EnumSet.noneOf(Material.class);
        final List<NamespacedKey> tags = new ArrayList<>();
        boolean everything = entries.isEmpty();
        for (final String raw : entries) {
            final String entry = raw.strip();
            if (entry.equals("*")) {
                everything = true;
            } else if (entry.startsWith("#")) {
                final String name = entry.substring(1).strip().toLowerCase(Locale.ROOT);
                final NamespacedKey key = name.isEmpty() ? null : NamespacedKey.fromString(name);
                if (key == null) {
                    problems.add("'" + entry + "' is not a tag (write it like '#minecraft:swords')");
                } else {
                    tags.add(key);
                }
            } else {
                final Material material = Material.matchMaterial(entry);
                if (material == null) { // isItem() needs a server; a block-only material just never matches
                    problems.add("'" + entry + "' is not an item (use a material name, '*' or a tag like '#minecraft:swords')");
                } else {
                    materials.add(material);
                }
            }
        }
        return new Parsed(new AcceptFilter(everything, materials, List.copyOf(tags)), problems);
    }

    boolean matches(final Material material, final TagLookup lookup) {
        if (everything || materials.contains(material)) {
            return true;
        }
        for (final NamespacedKey key : tags) {
            final TagLookup.Tag tag = lookup.find(key);
            if (tag != null && tag.contains(material)) {
                return true;
            }
        }
        return false;
    }
}
