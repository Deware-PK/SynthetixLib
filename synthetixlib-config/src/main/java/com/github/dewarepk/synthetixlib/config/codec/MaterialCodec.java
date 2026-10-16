package com.github.dewarepk.synthetixlib.config.codec;

import java.util.Arrays;
import java.util.List;
import org.bukkit.Material;

/**
 * {@link Material} by key ({@code diamond}, {@code minecraft:diamond}; the enum name {@code DIAMOND} also works).
 * Material has no Paper registry of its own in 1.21.11 (items and blocks are separate), so it goes through
 * {@link Material#matchMaterial(String)}.
 */
final class MaterialCodec implements Codec<Material> {

    @Override
    public Material decode(final Object raw, final DecodeContext ctx) {
        final String text = ScalarCodecs.text(raw, ctx).strip();
        final Material material = Material.matchMaterial(text);
        if (material == null || material.isLegacy()) {
            throw ctx.fail(Suggestions.unknown("material", text, keys()));
        }
        return material;
    }

    @Override
    public Object encode(final Material value) {
        if (value.isLegacy()) {
            throw new IllegalArgumentException("Legacy material " + value + " cannot be saved");
        }
        return Keys.format(value.getKey());
    }

    private static List<String> keys() {
        return Arrays.stream(Material.values())
                .filter(material -> !material.isLegacy())
                .map(material -> Keys.format(material.getKey()))
                .toList();
    }
}
