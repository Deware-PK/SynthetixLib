package com.github.dewarepk.synthetixexample.selftest;

import static com.github.dewarepk.synthetixexample.selftest.SelfTest.require;

import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.core.item.ItemBuilder;
import com.github.dewarepk.synthetixlib.core.pdc.PdcKey;
import java.util.List;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/** Self-tests for {@code core.item}. */
final class CoreItemSelfTests {

    private CoreItemSelfTests() {
    }

    static List<SelfTest> all(final JavaPlugin plugin, final SynthetixLib lib) {
        return List.of(
                SelfTest.sync("core.item.basic", () -> {
                    final NamespacedKey model = new NamespacedKey(plugin, "ticket");
                    final ItemStack item = lib.items().of(Material.PAPER)
                            .name("<gold>Ticket")
                            .itemName("<red>Base")
                            .lore("<gray>one")
                            .lore("<gray>two")
                            .itemModel(model)
                            .amount(3)
                            .glint(true)
                            .hideTooltip(false)
                            .build();
                    final ItemMeta meta = item.getItemMeta();
                    require(lib.text().plain(meta.customName()).equals("Ticket"), "custom name");
                    require(meta.customName().decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE,
                            "name must be non-italic");
                    require(lib.text().plain(meta.itemName()).equals("Base"), "item name");
                    require(meta.lore() != null && meta.lore().size() == 2, "lore must have 2 lines");
                    require(model.equals(meta.getItemModel()), "item model");
                    require(Boolean.TRUE.equals(meta.getEnchantmentGlintOverride()), "glint");
                    require(!meta.isHideTooltip(), "tooltip must be visible");
                    require(item.getAmount() == 3, "amount");
                }),
                SelfTest.sync("core.item.from-no-mutate", () -> {
                    final ItemStack input = new ItemStack(Material.STONE, 2);
                    final ItemStack built = lib.items().from(input).name("<red>X").amount(5).build();
                    require(!input.hasItemMeta() && input.getAmount() == 2, "input must not be mutated");
                    require(built != input && built.getAmount() == 5, "result must be a separate stack");
                }),
                SelfTest.sync("core.item.build-distinct", () -> {
                    final ItemBuilder builder = lib.items().of(Material.PAPER).name("<gold>T");
                    final ItemStack a = builder.build();
                    final ItemStack b = builder.build();
                    require(a != b && a.isSimilar(b), "builds must be distinct but equal");
                    a.setAmount(1);
                    a.editMeta(m -> m.lore(List.of(lib.text().parse("changed"))));
                    require(b.getItemMeta().lore() == null, "editing one build must not affect the other");
                    require(builder.build().getItemMeta().lore() == null, "template must stay unchanged");
                }),
                SelfTest.sync("core.item.pdc", () -> {
                    final PdcKey<String> key = lib.keys().string("item_builder");
                    final ItemStack item = lib.items().of(Material.PAPER).set(key, "12").build();
                    require(key.get(item).filter("12"::equals).isPresent(), "pdc value via builder");
                }),
                SelfTest.sync("core.item.lore-placeholder", () -> {
                    final ItemStack item = lib.items().of(Material.PAPER)
                            .lore("<gray>Lot #<id>", Placeholder.unparsed("id", "<red>12"))
                            .build();
                    require(lib.text().plain(item.getItemMeta().lore().get(0)).equals("Lot #<red>12"),
                            "unparsed placeholder must stay literal");
                }),
                SelfTest.sync("core.item.lore-replace-clear", () -> {
                    final ItemStack item = lib.items().of(Material.PAPER).lore("<gray>a")
                            .lore(List.of(lib.text().parse("b"))).build();
                    require(item.getItemMeta().lore().size() == 1, "lore(List) must replace");
                    final ItemStack cleared = lib.items().from(item).clearLore().build();
                    require(cleared.getItemMeta().lore() == null, "clearLore");
                }),
                SelfTest.sync("core.item.amount-validated", () -> {
                    boolean rejected = false;
                    try {
                        lib.items().of(Material.DIAMOND_SWORD).amount(2).build();
                    } catch (final IllegalArgumentException expected) {
                        rejected = true;
                    }
                    require(rejected, "amount above max stack size must throw");
                })
        );
    }
}
