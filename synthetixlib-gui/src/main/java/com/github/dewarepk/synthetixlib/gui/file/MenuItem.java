package com.github.dewarepk.synthetixlib.gui.file;

import com.github.dewarepk.synthetixlib.config.annotation.Range;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.jspecify.annotations.Nullable;

/**
 * One entry of {@code items:} in a menu file, keyed by the layout character. Keys are the component names in
 * kebab-case ({@code hideTooltip} is {@code hide-tooltip}). Texts stay MiniMessage strings: they are parsed when a
 * view renders, with the placeholders of that view.
 *
 * <pre>{@code
 * 'B':
 *   material: gold_ingot
 *   name: '<gold>Balance: <balance>'
 *   lore: ['<gray>Click to refresh']
 *   hide-tooltip: false
 *   sound: ui.button.click
 *   click: [refresh]
 * }</pre>
 *
 * @param material    what the item is; {@code air} leaves the slot empty. Required unless the item has
 *                    {@code base64}, {@code list}, {@code state} or {@code input}
 * @param amount      stack size, 1 to 99, default 1
 * @param name        custom name (MiniMessage)
 * @param lore        lore lines (MiniMessage); empty if none
 * @param itemModel   {@code item_model} key, e.g. of a resource pack model
 * @param glint       forces the enchantment glint on or off
 * @param hideTooltip hides the whole tooltip
 * @param tooltipStyle {@code tooltip_style} key of a resource pack
 * @param base64      an item stored with {@code ItemStack.serializeAsBytes}, for anything exotic
 * @param list        id of a list that code fills; every slot with this character shows one entry
 * @param empty       shown in list slots that have no entry
 * @param loading     shown in list slots while an asynchronous list is loading
 * @param busy        shown instead of the item while an {@code actionAsync} started from its slot runs
 * @param state       id of a state that code answers with the name of one of {@code states}
 * @param states      the items a {@code state} picks from, by state name; empty if none
 * @param hideWhen    leaves the item out on the first or last page
 * @param input       {@code true} if the player may put items into and take them out of this slot. An input slot shows
 *                    nothing of its own (no material, name or click); items the player left in it are handed back when
 *                    the menu closes
 * @param accept      what an input slot takes: {@code *}, material names or item tags like {@code '#minecraft:swords'};
 *                    empty means everything
 * @param maxAmount   most items an input slot holds, 1 to 99; default: what one stack of the item holds
 * @param sound       sound played when the item is clicked
 * @param click       what a click does
 */
public record MenuItem(
        Optional<Material> material,
        @Range(min = 1, max = 99) Optional<Integer> amount,
        Optional<String> name,
        @Nullable List<String> lore,
        Optional<NamespacedKey> itemModel,
        Optional<Boolean> glint,
        Optional<Boolean> hideTooltip,
        Optional<NamespacedKey> tooltipStyle,
        Optional<String> base64,
        Optional<String> list,
        Optional<MenuItem> empty,
        Optional<MenuItem> loading,
        Optional<MenuItem> busy,
        Optional<String> state,
        @Nullable Map<String, MenuItem> states,
        Optional<HideWhen> hideWhen,
        Optional<Boolean> input,
        @Nullable List<String> accept,
        @Range(min = 1, max = 99) Optional<Integer> maxAmount,
        Optional<NamespacedKey> sound,
        Optional<ClickBindings> click) {

    public MenuItem {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(name, "name");
        lore = lore == null ? List.of() : List.copyOf(lore);
        Objects.requireNonNull(itemModel, "itemModel");
        Objects.requireNonNull(glint, "glint");
        Objects.requireNonNull(hideTooltip, "hideTooltip");
        Objects.requireNonNull(tooltipStyle, "tooltipStyle");
        Objects.requireNonNull(base64, "base64");
        Objects.requireNonNull(list, "list");
        Objects.requireNonNull(empty, "empty");
        Objects.requireNonNull(loading, "loading");
        Objects.requireNonNull(busy, "busy");
        Objects.requireNonNull(state, "state");
        states = states == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(states));
        Objects.requireNonNull(hideWhen, "hideWhen");
        Objects.requireNonNull(input, "input");
        accept = accept == null ? List.of() : List.copyOf(accept);
        Objects.requireNonNull(maxAmount, "maxAmount");
        Objects.requireNonNull(sound, "sound");
        Objects.requireNonNull(click, "click");
    }

    /** @return {@code true} if the player may put items into this slot and take them out */
    public boolean isInput() {
        return input.orElse(false);
    }

    /**
     * The item for a state name that code answered with. YAML reads an unquoted {@code on}, {@code yes} or
     * {@code true} as a boolean, so a file that says {@code on:} holds a state called {@code true}; such a state is
     * found under any of its spellings, as is {@code false} under {@code off} and {@code no}. An exact name always
     * wins.
     *
     * @param name the state name, e.g. {@code on}
     * @return the item, or {@code null} if the item has no such state
     */
    public @Nullable MenuItem stateItem(final String name) {
        final MenuItem exact = states.get(name);
        if (exact != null) {
            return exact;
        }
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "on", "yes", "true" -> states.get("true");
            case "off", "no", "false" -> states.get("false");
            default -> null;
        };
    }

    /** @return what a click does; nothing if the file gives no {@code click} */
    public ClickBindings clicks() {
        return click.orElseGet(ClickBindings::none);
    }
}
