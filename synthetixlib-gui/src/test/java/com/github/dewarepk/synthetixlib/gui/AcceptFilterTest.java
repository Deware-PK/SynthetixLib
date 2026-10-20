package com.github.dewarepk.synthetixlib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

/** The {@code accept} list of an input slot. */
class AcceptFilterTest {

    private static final AcceptFilter.TagLookup NO_TAGS = key -> null;

    private static AcceptFilter filter(final String... entries) {
        final AcceptFilter.Parsed parsed = AcceptFilter.parse(List.of(entries));
        assertEquals(List.of(), parsed.problems());
        return parsed.filter();
    }

    @Test
    void anEmptyListAndAStarAcceptEverything() {
        assertTrue(filter().matches(Material.DIRT, NO_TAGS));
        assertTrue(filter("*").matches(Material.DIRT, NO_TAGS));
        assertTrue(filter("diamond", "*").matches(Material.DIRT, NO_TAGS));
    }

    @Test
    void materialsAreMatchedByNameInAnyCase() {
        final AcceptFilter filter = filter("diamond", "Emerald", "minecraft:gold_ingot");

        assertTrue(filter.matches(Material.DIAMOND, NO_TAGS));
        assertTrue(filter.matches(Material.EMERALD, NO_TAGS));
        assertTrue(filter.matches(Material.GOLD_INGOT, NO_TAGS));
        assertFalse(filter.matches(Material.DIRT, NO_TAGS));
    }

    @Test
    void aTagIsLookedUpWhenAnItemIsChecked() {
        final AcceptFilter filter = filter("#minecraft:swords");
        final Set<Material> swords = Set.of(Material.DIAMOND_SWORD, Material.IRON_SWORD);
        final AcceptFilter.TagLookup lookup = key -> key.equals(NamespacedKey.minecraft("swords"))
                ? swords::contains : null;

        assertTrue(filter.matches(Material.DIAMOND_SWORD, lookup));
        assertFalse(filter.matches(Material.DIAMOND, lookup));
        assertFalse(filter.matches(Material.DIAMOND_SWORD, NO_TAGS), "a tag that does not exist accepts nothing");
    }

    @Test
    void tagsAndMaterialsMix() {
        final AcceptFilter filter = filter("#minecraft:swords", "stick");
        final AcceptFilter.TagLookup lookup = key -> material -> material == Material.IRON_SWORD;

        assertTrue(filter.matches(Material.STICK, lookup));
        assertTrue(filter.matches(Material.IRON_SWORD, lookup));
        assertFalse(filter.matches(Material.DIRT, lookup));
    }

    @Test
    void entriesThatAreNotUnderstoodAreReportedAndLeftOut() {
        final AcceptFilter.Parsed parsed = AcceptFilter.parse(List.of("diamond", "diamnod", "#Not A Tag", "#"));

        assertEquals(3, parsed.problems().size(), parsed.problems().toString());
        assertTrue(parsed.problems().get(0).contains("diamnod"));
        assertTrue(parsed.filter().matches(Material.DIAMOND, NO_TAGS));
        assertFalse(parsed.filter().matches(Material.DIRT, NO_TAGS));
    }

    @Test
    void aListWhoseEveryEntryIsWrongAcceptsNothingRatherThanEverything() {
        final AcceptFilter.Parsed parsed = AcceptFilter.parse(List.of("diamnod"));

        assertEquals(1, parsed.problems().size());
        assertFalse(parsed.filter().matches(Material.DIAMOND, NO_TAGS));
    }
}
