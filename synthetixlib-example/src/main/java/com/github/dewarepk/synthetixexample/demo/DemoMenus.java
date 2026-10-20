package com.github.dewarepk.synthetixexample.demo;

import com.github.dewarepk.synthetixlib.config.ConfigCollection;
import com.github.dewarepk.synthetixlib.config.Configs;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.core.pdc.PdcKey;
import com.github.dewarepk.synthetixlib.gui.GuiService;
import com.github.dewarepk.synthetixlib.gui.MenuClick;
import com.github.dewarepk.synthetixlib.gui.MenuType;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The menus of the demo ({@code menus/demo.yml} and {@code menus/airdrop.yml}). The files say what everything looks
 * like; this class only registers the code they refer to by name.
 */
public final class DemoMenus {

    /** An airdrop with its id: the entries of the list and the context of the airdrop menu. */
    public record Entry(String id, Airdrop airdrop) {
    }

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final MenuType<Void> demo;

    private DemoMenus(final MenuType<Void> demo) {
        this.demo = demo;
    }

    /** Builds both menus; their files are copied from the jar on the first start and never overwritten. */
    public static DemoMenus create(final SynthetixLib lib, final Configs configs,
                                   final ConfigCollection<Airdrop> airdrops, final LocaleHandle<Messages> lang,
                                   final ClaimStore claims) {
        final GuiService gui = GuiService.create(lib, configs).messages(lang, Messages::gui);
        final PdcKey<Boolean> notify = lib.keys().bool("demo_notify");

        final MenuType<Entry> airdropMenu = gui.menu("airdrop").<Entry>withContext()
                .placeholder("name", view -> Component.text(view.context().airdrop().name()))
                .placeholder("interval", view -> Component.text(view.context().airdrop().interval().toString()))
                .placeholder("players", view -> Component.text(view.context().airdrop().minPlayers()))
                .placeholder("rewards", view -> Component.text(view.context().airdrop().rewards().size()))
                .build();

        final MenuType<Void> demo = gui.menu("demo")
                .placeholder("balance", view -> Component.text(view.player().getLevel()))
                .placeholder("time", view -> Component.text(LocalTime.now().format(CLOCK)))
                .list("airdrops", view -> entries(airdrops), (entry, view) -> icon(lib, lang, claims, entry, view.player()))
                .onListClick("airdrops", (Entry entry, MenuClick<Void> click) -> airdropMenu.open(click.player(), entry))
                .state("notify", view -> notify.get(view.player()).orElse(false) ? "on" : "off")
                .action("toggle-notify", click -> {
                    notify.set(click.player(), !notify.get(click.player()).orElse(false));
                    click.view().refresh();
                })
                .action("confirm", click -> confirm(click.player(), click.view().takeInputs('S'), lang))
                .build();
        return new DemoMenus(demo);
    }

    private static List<Entry> entries(final ConfigCollection<Airdrop> airdrops) {
        final List<Entry> entries = new ArrayList<>();
        for (final Map.Entry<String, Airdrop> entry : airdrops.all().entrySet()) {
            entries.add(new Entry(entry.getKey(), entry.getValue()));
        }
        return entries;
    }

    /** The claim count comes from memory (see {@link ClaimStore}), so drawing the menu never waits for the database. */
    private static ItemStack icon(final SynthetixLib lib, final LocaleHandle<Messages> lang, final ClaimStore claims,
                                  final Entry entry, final Player viewer) {
        return lib.items().of(Material.CHEST)
                .name("<gold>" + lib.text().escape(entry.airdrop().name()))
                .lore(List.of(
                        lib.text().parse("<gray>" + lib.text().escape(entry.id())),
                        lang.get(viewer, Messages::airdropClaims,
                                Placeholder.unparsed("count", String.valueOf(claims.count(entry.id()))))))
                .build();
    }

    /** The items leave the slot at once (so a double click cannot pay twice), then go back to the player. */
    private static void confirm(final Player player, final List<ItemStack> taken, final LocaleHandle<Messages> lang) {
        if (taken.isEmpty()) {
            lang.send(player, Messages::inputEmpty);
            return;
        }
        int count = 0;
        for (final ItemStack stack : taken) {
            count += stack.getAmount();
            player.getInventory().addItem(stack).values()
                    .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        }
        lang.send(player, Messages::inputConfirmed, Placeholder.unparsed("count", String.valueOf(count)));
    }

    /** Opens the demo menu for a player; any thread. */
    public void open(final Player player) {
        demo.open(player);
    }
}
