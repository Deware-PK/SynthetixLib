package com.github.dewarepk.synthetixlib.config.codec;

import com.github.dewarepk.synthetixlib.core.text.TextService;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.Nullable;

/**
 * {@link ItemStack} in a readable form {@code {material, amount, name, lore, item-model, glint}} (name and lore are
 * MiniMessage) when that form reproduces the stack exactly, else {@code {base64: ...}} from
 * {@link ItemStack#serializeAsBytes()}. Never lossy. Decoding accepts both.
 */
final class ItemStackCodec implements Codec<ItemStack> {

    static final String BASE64 = "base64";
    private static final int MAX_AMOUNT = 99;

    private final Codecs codecs;
    private final TextService text;

    ItemStackCodec(final Codecs codecs, final TextService text) {
        this.codecs = codecs;
        this.text = text;
    }

    /** @return {@code true} if {@code raw} is the {@code {base64: ...}} form */
    static boolean isBase64(final Object raw) {
        return raw instanceof Map<?, ?> map && map.containsKey(BASE64);
    }

    @Override
    public ItemStack decode(final Object raw, final DecodeContext ctx) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw ctx.expected("a section with material", raw);
        }
        return isBase64(raw) ? decodeBase64(map, ctx) : decodeReadable(map, ctx);
    }

    private ItemStack decodeBase64(final Map<?, ?> map, final DecodeContext ctx) {
        for (final Object key : map.keySet()) {
            if (!BASE64.equals(key)) {
                ctx.at(String.valueOf(key)).warn("ignored, the item is stored as " + BASE64);
            }
        }
        final DecodeContext at = ctx.at(BASE64);
        final String encoded = ScalarCodecs.text(map.get(BASE64), at);
        try {
            return ItemStack.deserializeBytes(Base64.getMimeDecoder().decode(encoded));
        } catch (final RuntimeException e) {
            throw at.fail("cannot read item data (" + e.getMessage() + ")");
        }
    }

    private ItemStack decodeReadable(final Map<?, ?> map, final DecodeContext ctx) {
        final List<Problem> problems = new ArrayList<>();
        final Material material = attempt(problems, () -> material(map.get("material"), ctx.at("material")));
        final Integer amount = attempt(problems, () -> amount(map.get("amount"), ctx.at("amount")));
        final Component name = attempt(problems, () -> map.get("name") == null ? null
                : text.parseItem(ScalarCodecs.text(map.get("name"), ctx.at("name"))));
        final List<Component> lore = attempt(problems, () -> lore(map.get("lore"), ctx.at("lore")));
        final NamespacedKey model = attempt(problems, () -> map.get("item-model") == null ? null
                : ctx.at("item-model").decode(map.get("item-model"), NamespacedKey.class));
        final Boolean glint = attempt(problems, () -> map.get("glint") == null ? null
                : ctx.at("glint").decode(map.get("glint"), Boolean.class));
        for (final Object key : map.keySet()) {
            if (!isReadableKey(String.valueOf(key))) {
                ctx.at(String.valueOf(key)).warn("unknown key");
            }
        }
        if (!problems.isEmpty()) {
            throw new DecodeException(problems);
        }
        final ItemStack stack = new ItemStack(material, amount);
        stack.editMeta(meta -> {
            if (name != null) {
                meta.customName(name);
            }
            if (lore != null) {
                meta.lore(lore);
            }
            if (model != null) {
                meta.setItemModel(model);
            }
            if (glint != null) {
                meta.setEnchantmentGlintOverride(glint);
            }
        });
        return stack;
    }

    private static boolean isReadableKey(final String key) {
        return switch (key) {
            case "material", "amount", "name", "lore", "item-model", "glint" -> true;
            default -> false;
        };
    }

    private interface Read<V> {
        @Nullable V get();
    }

    private static <V> @Nullable V attempt(final List<Problem> problems, final Read<V> read) {
        try {
            return read.get();
        } catch (final DecodeException e) {
            problems.addAll(e.problems());
            return null;
        }
    }

    private static Material material(final @Nullable Object raw, final DecodeContext at) {
        if (raw == null) {
            throw at.fail("missing");
        }
        final Material material = at.decode(raw, Material.class);
        if (!material.isItem() || material.isAir()) {
            throw at.fail(material + " is not an item that can be held");
        }
        return material;
    }

    private static int amount(final @Nullable Object raw, final DecodeContext at) {
        if (raw == null) {
            return 1;
        }
        final int amount = at.decode(raw, int.class);
        if (amount < 1 || amount > MAX_AMOUNT) {
            throw at.fail("must be between 1 and " + MAX_AMOUNT + " (was " + amount + ")");
        }
        return amount;
    }

    private @Nullable List<Component> lore(final @Nullable Object raw, final DecodeContext at) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof List<?> lines)) {
            throw at.expected("a list", raw);
        }
        final List<Component> out = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            out.add(text.parseItem(ScalarCodecs.text(lines.get(i), at.at(i))));
        }
        return out;
    }

    @Override
    public Object encode(final ItemStack value) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException("An empty item stack cannot be saved");
        }
        final Map<String, Object> readable = readable(value);
        if (reproduces(readable, value)) {
            return readable;
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put(BASE64, Base64.getEncoder().encodeToString(value.serializeAsBytes()));
        return out;
    }

    private Map<String, Object> readable(final ItemStack stack) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("material", Keys.format(stack.getType().getKey()));
        out.put("amount", stack.getAmount());
        if (!stack.hasItemMeta()) {
            return out;
        }
        final ItemMeta meta = stack.getItemMeta();
        final Component name = meta.customName();
        if (name != null) {
            out.put("name", markup(name));
        }
        final List<Component> lore = meta.lore();
        if (lore != null) {
            out.put("lore", lore.stream().map(ItemStackCodec::markup).toList());
        }
        if (meta.hasItemModel()) {
            out.put("item-model", meta.getItemModel().toString());
        }
        if (meta.hasEnchantmentGlintOverride()) {
            out.put("glint", meta.getEnchantmentGlintOverride());
        }
        return out;
    }

    /** MiniMessage of a name/lore line; the italic-off that {@code parseItem} adds again is left implicit. */
    private static String markup(final Component component) {
        final Component plain = component.decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE
                ? component.decoration(TextDecoration.ITALIC, TextDecoration.State.NOT_SET)
                : component;
        return MiniMessage.miniMessage().serialize(plain);
    }

    /** Whether decoding {@code readable} gives back a stack identical to {@code original}. */
    private boolean reproduces(final Map<String, Object> readable, final ItemStack original) {
        try {
            final ItemStack back = decodeReadable(readable, codecs.newContext());
            return back.isSimilar(original) && back.getAmount() == original.getAmount();
        } catch (final DecodeException e) {
            return false;
        }
    }
}
