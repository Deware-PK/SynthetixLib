package com.github.dewarepk.synthetixexample.selftest;

import static com.github.dewarepk.synthetixlib.selftest.SelfTest.require;

import com.github.dewarepk.synthetixlib.config.Configs;
import com.github.dewarepk.synthetixlib.config.FilesReloadResult;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.gui.GuiService;
import com.github.dewarepk.synthetixlib.gui.MenuType;
import com.github.dewarepk.synthetixlib.gui.file.ClickAction;
import com.github.dewarepk.synthetixlib.gui.file.ClickKind;
import com.github.dewarepk.synthetixlib.gui.file.MenuFile;
import com.github.dewarepk.synthetixlib.gui.file.MenuKind;
import com.github.dewarepk.synthetixlib.selftest.SelfTest;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/** Self-test for the gui module: menu files copied from the jar, parsed with the real Bukkit codecs, checked. */
final class GuiSelfTests {

    /** The language files of the gui.lang self-test: the keys of {@code <lang:key>} in menu files. */
    public record Texts(Map<String, String> gui) {

        static Texts defaults() {
            return new Texts(Map.of("title", "<gold>Auction", "hello", "Hello <player>"));
        }
    }

    private GuiSelfTests() {
    }

    private static void deleteTree(final Path folder) throws java.io.IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            for (final Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static Material type(final Inventory inventory, final int slot) {
        final ItemStack stack = inventory.getItem(slot);
        return stack == null ? Material.AIR : stack.getType();
    }

    private static String name(final SynthetixLib lib, final Inventory inventory, final int slot) {
        return lib.text().plain(inventory.getItem(slot).getItemMeta().customName());
    }

    /** @return {@code true} if the component, or a child of it, is a translatable with this key */
    private static boolean translates(final Component component, final String key) {
        return component instanceof net.kyori.adventure.text.TranslatableComponent translatable
                && translatable.key().equals(key)
                || component.children().stream().anyMatch(child -> translates(child, key));
    }

    static List<SelfTest> all(final JavaPlugin plugin, final SynthetixLib lib) {
        final Configs configs = Configs.create(lib);
        final GuiService gui = GuiService.create(lib, configs);
        final AtomicReference<MenuType<Void>> pack = new AtomicReference<>();
        return List.of(
                SelfTest.sync("gui.menu-files", () -> {
                    final Path file = plugin.getDataFolder().toPath().resolve("menus/selftest-menu.yml");
                    Files.deleteIfExists(file);
                    final String bundled;
                    try (InputStream in = plugin.getResource("menus/selftest-menu.yml")) {
                        require(in != null, "the test menu is in the jar");
                        bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }

                    final MenuType<Void> menu = gui.menu("selftest-menu")
                            .action("known", click -> { })
                            .build();

                    require(bundled.equals(Files.readString(file)), "bundled file copied byte for byte, comments kept");
                    final MenuFile parsed = menu.file();
                    require(parsed.type() == MenuKind.HOPPER && parsed.size() == 5, "hopper of 5 slots");
                    require(parsed.title().startsWith("<white>Self test"), "title read");
                    require(parsed.items().size() == 4, "four items");
                    require(parsed.item('K').material().orElseThrow() == Material.LIME_DYE,
                            "material read through the Bukkit codec");
                    require(parsed.item('#').hideTooltip().orElse(false), "hide-tooltip read");
                    require(parsed.item('K').clicks().actions(ClickKind.LEFT)
                                    .equals(List.of(new ClickAction.Run("known"), new ClickAction.Close())),
                            "click actions parsed in order");

                    require(menu.isInert('B'), "an action nobody registered makes the item inert");
                    require(!menu.isInert('K'), "a registered action does not");
                    require(parsed.item('U').clicks().isEmpty(), "an unknown action leaves the item without clicks");

                    final FilesReloadResult reloaded = gui.reload();
                    require(reloaded.errors().stream().anyMatch(e -> e.startsWith("selftest-menu: items.U.click[0]: "
                                    + "unknown action 'teleport'")),
                            "unknown action reported with menu, item and index: " + reloaded.errors());
                    require(reloaded.warnings().stream().anyMatch(w -> w.contains("items.B")
                                    && w.contains("action 'missing' is not registered")),
                            "unregistered action reported: " + reloaded.warnings());

                    Files.writeString(file, bundled.replace("Self test", "Edited"));
                    require(gui.reload().changed(), "an edit is noticed");
                    require(menu.file().title().contains("Edited"), "edited file is read, the jar copy is not used");
                    require(Files.readString(file).contains("Edited"), "an existing file is never overwritten");
                }),
                SelfTest.sync("gui.render", () -> {
                    final Path file = plugin.getDataFolder().toPath().resolve("menus/selftest-render.yml");
                    Files.deleteIfExists(file);
                    final List<String> lots = List.of("alpha", "beta", "gamma", "delta", "epsilon");
                    final MenuType<Void> menu = gui.menu("selftest-render")
                            .placeholder("balance", view -> Component.text("<red>12"))
                            .list("lots", view -> lots, (lot, view) -> lib.items().of(Material.PAPER)
                                    .name("<white>" + lib.text().escape(lot)).build())
                            .state("mode", view -> "on")
                            .build();

                    final Inventory first = menu.preview("Steve", null, 0);
                    require(first.getType() == InventoryType.CHEST && first.getSize() == 18, "two rows of a chest");
                    require(first.getHolder(false) != null, "the inventory has the library's holder");
                    for (int i = 0; i < 3; i++) {
                        require(first.getItem(i) != null && first.getItem(i).getType() == Material.PAPER,
                                "list slot " + i + " shows an entry");
                    }
                    require(name(lib, first, 0).equals("alpha") && name(lib, first, 2).equals("gamma"),
                            "entries in layout order");
                    require(first.getItem(3) == null, "previous is hidden on the first page");
                    require(type(first, 4) == Material.ARROW, "next is shown on the first page");
                    require(type(first, 5) == Material.LIME_DYE, "the state code answered picks the item");
                    require(type(first, 9) == Material.STONE, "static item");
                    require(first.getItem(6) == null && first.getItem(7) == null, "layout spaces stay empty");
                    require(name(lib, first, 17).equals("Balance <red>12"),
                            "a placeholder value is text, not markup: " + name(lib, first, 17));
                    require(lib.text().plain(first.getItem(17).getItemMeta().lore().get(0)).equals("for Steve"),
                            "<player> in lore");

                    final Inventory second = menu.preview("Steve", null, 1);
                    require(name(lib, second, 0).equals("delta") && name(lib, second, 1).equals("epsilon"),
                            "second page holds the rest");
                    require(type(second, 2) == Material.BARRIER && name(lib, second, 2).equals("none"),
                            "a free list slot shows the empty item");
                    require(type(second, 3) == Material.ARROW, "previous is shown on page 2");
                    require(second.getItem(4) == null, "next is hidden on the last page");

                    final Inventory clamped = menu.preview("Steve", null, 99);
                    require(name(lib, clamped, 0).equals("delta"), "a page past the end shows the last page");

                    Files.writeString(file, Files.readString(file).replace("material: stone", "material: cobblestone"));
                    gui.reload();
                    require(type(menu.preview("Steve", null, 0), 9) == Material.COBBLESTONE,
                            "a reloaded file is rendered by the next view");
                }),
                SelfTest.sync("gui.lang", () -> {
                    final Path data = plugin.getDataFolder().toPath();
                    deleteTree(data.resolve("selftest-gui-lang"));
                    Files.deleteIfExists(data.resolve("menus/selftest-lang.yml"));
                    final LocaleHandle<Texts> lang = configs.locale("selftest-gui-lang", Texts.class, Texts::defaults)
                            .defaultLocale(Locale.US)
                            .bundled("th_TH")
                            .perPlayer(true)
                            .load();
                    gui.messages(lang, Texts::gui);
                    final MenuType<Void> menu = gui.menu("selftest-lang").build();

                    require(lib.text().plain(menu.previewTitle("Steve", null, 0)).equals("Auction (1/1)"),
                            "<lang:title> mixed with inline text and <page>");
                    final PolicyBot english = new PolicyBot();
                    final Inventory en = menu.attach(english.player, null);
                    english.show(en);
                    require(name(lib, en, 0).equals("Hello PolicyBot"), "the default language, with <player> inside the message");
                    require(translates(en.getItem(1).getItemMeta().customName(), "nope"),
                            "a key in no language file becomes a translatable (MiniMessage's own <lang>)");
                    require(lib.text().plain(en.getItem(1).getItemMeta().lore().get(0)).equals("Hello PolicyBot"),
                            "<lang:key> in lore");
                    english.close();

                    final PolicyBot thai = new PolicyBot();
                    thai.locale = Locale.of("th", "TH");
                    final Inventory th = menu.attach(thai.player, null);
                    thai.show(th);
                    require(name(lib, th, 0).equals("Sawasdee PolicyBot"), "the viewer's own language: " + name(lib, th, 0));
                    thai.close();
                }),
                SelfTest.sync("gui.title-passthrough", () -> {
                    Files.deleteIfExists(plugin.getDataFolder().toPath().resolve("menus/selftest-pack.yml"));
                    final MenuType<Void> menu = gui.menu("selftest-pack").build();
                    pack.set(menu);

                    // an ItemsAdder ":name:" and ":offset_-8:", a Nexo glyph tag: the title keeps all of them as written
                    final Component title = menu.previewTitle("Steve", null, 0);
                    require(lib.text().plain(title).equals(":offset_-8::auction_bg:Pack <glyph:bg_main>(1/1)"),
                            "both placeholder styles reach the title unchanged: " + lib.text().plain(title));
                    require(menu.preview("Steve", null, 0).getSize() == 5, "the menu with such a title still renders");
                }),
                SelfTest.sync("gui.item-components", () -> {
                    final Inventory inventory = pack.get().preview("Steve", null, 0);
                    final ItemMeta model = inventory.getItem(0).getItemMeta();
                    require(model.hasItemModel() && model.getItemModel().equals(NamespacedKey.fromString("selftest:invisible")),
                            "item-model applied: " + model.getItemModel());
                    final ItemMeta styled = inventory.getItem(1).getItemMeta();
                    require(NamespacedKey.fromString("selftest:fancy").equals(styled.getTooltipStyle()),
                            "tooltip-style applied: " + styled.getTooltipStyle());
                    require(inventory.getItem(2).getItemMeta().isHideTooltip(), "hide-tooltip applied");
                    require(!inventory.getItem(1).getItemMeta().isHideTooltip(), "a tooltip is shown unless asked otherwise");
                }),
                SelfTest.async("gui.async-list", () -> {
                    try {
                        Files.deleteIfExists(plugin.getDataFolder().toPath().resolve("menus/selftest-async.yml"));
                    } catch (final java.io.IOException e) {
                        return CompletableFuture.failedFuture(e);
                    }
                    final CompletableFuture<Boolean> offThread = new CompletableFuture<>();
                    final MenuType<Void> menu = gui.menu("selftest-async")
                            .listAsync("lots", view -> {
                                offThread.complete(!Bukkit.isPrimaryThread());
                                return List.of("a", "b");
                            }, (lot, view) -> lib.items().of(Material.PAPER).name(lot).build())
                            .build();
                    final PolicyBot bot = new PolicyBot();
                    final Inventory top = menu.attach(bot.player, null);
                    bot.show(top);
                    require(type(top, 0) == Material.CLOCK && type(top, 1) == Material.CLOCK,
                            "the loading item shows until the entries arrive");
                    bot.close(); // the view ends before the answer: it is dropped and nothing is drawn afterwards
                    return offThread.thenAccept(off -> require(off, "the source ran off the server threads"));
                }),
                SelfTest.async("gui.action-async", () -> actionAsync(plugin, lib, gui)),
                SelfTest.sync("gui.policy", () -> policy(plugin, gui))
        );
    }

    /**
     * An {@code actionAsync} that completes after 10 ticks: two clicks 2 ticks apart run the handler once and show the busy
     * item; a click after the completion runs it again and the normal item is back.
     */
    private static CompletableFuture<Void> actionAsync(final JavaPlugin plugin, final SynthetixLib lib,
                                                       final GuiService gui) {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            Files.deleteIfExists(plugin.getDataFolder().toPath().resolve("menus/selftest-action-async.yml"));
            final AtomicInteger runs = new AtomicInteger();
            final MenuType<Void> menu = gui.menu("selftest-action-async")
                    .actionAsync("claim", click -> {
                        runs.incrementAndGet();
                        final CompletableFuture<Void> work = new CompletableFuture<>();
                        lib.scheduler().global().runLater(10, () -> work.complete(null));
                        return work;
                    })
                    .build();
            final PolicyBot bot = new PolicyBot(lib);
            final Inventory top = menu.attach(bot.player, null);
            bot.show(top);
            require(type(top, 0) == Material.EMERALD, "the normal item before the click");

            bot.click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
            require(runs.get() == 1, "the first click starts the work");
            require(type(top, 0) == Material.CLOCK, "the busy item shows while the work runs");
            lib.scheduler().global().runLater(2, () -> {
                try {
                    bot.click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
                    require(runs.get() == 1, "a click while the work runs is ignored");
                } catch (final Throwable e) {
                    result.completeExceptionally(e);
                }
            });
            lib.scheduler().global().runLater(20, () -> {
                try {
                    require(runs.get() == 1, "still one run after the work completed");
                    require(type(top, 0) == Material.EMERALD, "the normal item is back after the work");
                    bot.click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
                    require(runs.get() == 2, "a click after the completion runs the handler again");
                    bot.close();
                    result.complete(null);
                } catch (final Throwable e) {
                    result.completeExceptionally(e);
                }
            });
        } catch (final Throwable e) {
            result.completeExceptionally(e);
        }
        return result;
    }

    private static ItemStack stack(final Material material, final int amount) {
        return new ItemStack(material, amount);
    }

    /**
     * The click policy wired to a real view: synthetic clicks, drags and moves go through the server's event system to the
     * service's listener, on a menu a test double of a player has open (no client is needed). The server does not carry out
     * an event nobody sent, so an allowed click is only checked for "not cancelled".
     */
    private static void policy(final JavaPlugin plugin, final GuiService gui) throws Exception {
        Files.deleteIfExists(plugin.getDataFolder().toPath().resolve("menus/selftest-policy.yml"));
        final List<ItemStack> taken = new ArrayList<>();
        final MenuType<Void> menu = gui.menu("selftest-policy")
                .action("take", click -> taken.addAll(click.view().takeInputs()))
                .build();
        final PolicyBot bot = new PolicyBot();
        final Inventory top = menu.attach(bot.player, null);
        bot.show(top);
        require(top.getSize() == 18 && type(top, 0) == Material.STONE && type(top, 4) == Material.PAPER,
                "the menu is drawn");
        require(top.getItem(1) == null && top.getItem(2) == null, "input slots start empty");

        // a locked slot: nothing gets out, nothing gets in
        bot.cursor = stack(Material.DIAMOND, 3);
        require(bot.click(0, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR).isCancelled(), "swap into a locked slot");
        bot.cursor = ItemStack.empty();
        require(bot.click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled(), "take a menu item");
        require(bot.click(0, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled(),
                "shift-click a menu item");
        require(bot.click(0, ClickType.DROP, InventoryAction.DROP_ONE_SLOT).isCancelled(), "Q on a menu item");
        require(bot.click(0, ClickType.CREATIVE, InventoryAction.CLONE_STACK).isCancelled(), "creative clone");
        bot.bottom.setItem(0, stack(Material.DIAMOND, 1));
        require(bot.clickWithKey(0, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0).isCancelled(),
                "number key onto a menu item");
        bot.offHand = stack(Material.DIRT, 1);
        require(bot.clickWithKey(0, ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP, 40).isCancelled(),
                "off-hand key onto a menu item");
        bot.bottom.clear();
        bot.offHand = ItemStack.empty();
        require(type(top, 0) == Material.STONE, "the menu item is still there");
        require(!bot.click(-999, ClickType.WINDOW_BORDER_LEFT, InventoryAction.DROP_ALL_CURSOR).isCancelled(),
                "dropping the cursor outside the window is the player's own business");

        // bug #2: a double-click gathers from every slot of the window, locked ones included
        bot.bottom.setItem(5, stack(Material.DIAMOND, 5));
        bot.cursor = stack(Material.DIAMOND, 1);
        require(bot.click(bot.bottomRaw(5), ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR).isCancelled(),
                "double-click in the player's inventory");
        require(bot.click(1, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR).isCancelled(),
                "double-click on an input slot");
        bot.cursor = ItemStack.empty();
        bot.bottom.clear();

        // input slots take what they accept, up to max-amount
        bot.cursor = stack(Material.DIAMOND, 3);
        require(!bot.click(1, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled(), "an accepted item");
        bot.cursor = stack(Material.DIRT, 3);
        require(bot.click(1, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled(), "a refused item");
        bot.cursor = stack(Material.DIAMOND, 9);
        require(bot.click(1, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled(), "more than max-amount");
        bot.cursor = stack(Material.DIAMOND, 8);
        require(!bot.click(1, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled(), "exactly max-amount");
        bot.cursor = ItemStack.empty();
        bot.bottom.setItem(0, stack(Material.EMERALD, 4));
        require(!bot.clickWithKey(2, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0).isCancelled(),
                "an accepted item by number key");
        bot.bottom.setItem(0, stack(Material.DIAMOND, 9));
        require(bot.clickWithKey(2, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0).isCancelled(),
                "too many by number key");
        bot.bottom.clear();

        // bug #3: a shift-click from the player's inventory goes to input slots only, never to the first free slot
        bot.bottom.setItem(3, stack(Material.DIAMOND, 20));
        require(bot.click(bot.bottomRaw(3), ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled(),
                "the server's own shift-click is cancelled");
        require(top.getItem(1) != null && top.getItem(1).getAmount() == 8 && top.getItem(2) != null
                        && top.getItem(2).getAmount() == 8, "filled both input slots up to max-amount");
        require(bot.bottom.getItem(3) != null && bot.bottom.getItem(3).getAmount() == 4, "the rest stays where it was");
        require(PolicyBot.count(top, Material.DIAMOND) + PolicyBot.count(bot.bottom, Material.DIAMOND) == 20,
                "nothing lost, nothing doubled");
        require(type(top, 0) == Material.STONE && type(top, 3) == Material.STONE, "locked slots were not touched");
        bot.click(bot.bottomRaw(3), ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        require(bot.bottom.getItem(3).getAmount() == 4 && PolicyBot.count(top, Material.DIAMOND) == 16,
                "no room: nothing moves");
        bot.bottom.setItem(4, stack(Material.DIRT, 7));
        require(bot.click(bot.bottomRaw(4), ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled(),
                "a refused item is not moved");
        require(PolicyBot.count(top, Material.DIRT) == 0 && bot.bottom.getItem(4).getAmount() == 7,
                "the refused item stays in the player's inventory");
        bot.bottom.clear();
        top.setItem(1, null);
        top.setItem(2, null);

        // drags: one locked slot spoils the whole drag
        final ItemStack eight = stack(Material.DIAMOND, 8);
        require(bot.drag(eight, Map.of(0, stack(Material.DIAMOND, 4), 1, stack(Material.DIAMOND, 4))).isCancelled(),
                "a drag over a locked slot");
        require(!bot.drag(eight, Map.of(1, stack(Material.DIAMOND, 4), 2, stack(Material.DIAMOND, 4))).isCancelled(),
                "a drag over input slots");
        require(bot.drag(eight, Map.of(1, stack(Material.DIRT, 4))).isCancelled(), "a drag of a refused item");

        // hoppers and the like
        final InventoryMoveItemEvent intoMenu = new InventoryMoveItemEvent(bot.bottom, stack(Material.DIAMOND, 1), top, true);
        Bukkit.getPluginManager().callEvent(intoMenu);
        require(intoMenu.isCancelled(), "an item mover cannot fill a menu");
        final InventoryMoveItemEvent elsewhere = new InventoryMoveItemEvent(Bukkit.createInventory(null, 9),
                stack(Material.DIAMOND, 1), Bukkit.createInventory(null, 9), true);
        Bukkit.getPluginManager().callEvent(elsewhere);
        require(!elsewhere.isCancelled(), "other inventories are not our business");

        // takeInputs from a click handler: atomic, a second click finds the slots empty
        top.setItem(1, stack(Material.DIAMOND, 5));
        top.setItem(2, stack(Material.EMERALD, 3));
        require(bot.click(4, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled(), "the button is locked");
        require(taken.size() == 2 && taken.get(0).getType() == Material.DIAMOND && taken.get(0).getAmount() == 5
                        && taken.get(1).getType() == Material.EMERALD && taken.get(1).getAmount() == 3,
                "the action took both stacks: " + taken);
        require(top.getItem(1) == null && top.getItem(2) == null, "the slots are empty after takeInputs");
        bot.click(4, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        require(taken.size() == 2, "takeInputs twice: the second one finds nothing");

        // input items are handed back when the menu closes, once
        top.setItem(1, stack(Material.DIAMOND, 5));
        top.setItem(2, stack(Material.EMERALD, 3));
        bot.close();
        require(top.getItem(1) == null && top.getItem(2) == null, "the input slots are emptied on close");
        require(PolicyBot.count(bot.bottom, Material.DIAMOND) == 5 && PolicyBot.count(bot.bottom, Material.EMERALD) == 3,
                "the items are back in the player's inventory");
        bot.close();
        require(PolicyBot.count(bot.bottom, Material.DIAMOND) == 5 && PolicyBot.count(bot.bottom, Material.EMERALD) == 3,
                "a second close hands nothing back: never doubled");

        // the service closes while a menu is open on a thread that cannot close it: none of the menu is left to take, the
        // player's items stay. (The test double is not a real entity, so the library cannot tell which thread owns it and
        // logs one warning that it removed the menu's items instead; nothing is lost.)
        final Inventory open = menu.attach(bot.player, null);
        bot.show(open);
        open.setItem(1, stack(Material.DIAMOND, 5));
        gui.close();
        require(gui.isClosed(), "the service is closed");
        require(open.getItem(0) == null && open.getItem(3) == null && open.getItem(4) == null,
                "nothing of the menu is left in the inventory");
        require(open.getItem(1) != null && open.getItem(1).getAmount() == 5, "the player's input item is still there");
    }
}
