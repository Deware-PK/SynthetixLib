package com.github.dewarepk.synthetixexample;

import com.github.dewarepk.synthetixexample.demo.Airdrop;
import com.github.dewarepk.synthetixexample.demo.Messages;
import com.github.dewarepk.synthetixexample.demo.Settings;
import com.github.dewarepk.synthetixexample.selftest.SelfTestTrigger;
import com.github.dewarepk.synthetixlib.config.ConfigCollection;
import com.github.dewarepk.synthetixlib.config.ConfigHandle;
import com.github.dewarepk.synthetixlib.config.Configs;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.core.pdc.PdcKey;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * Example consumer of SynthetixLib. Doubles as the smoke-test bed: when started with
 * {@code -Dsynthetix.selftest=true} it runs all self-tests once the server has loaded,
 * writes the result JSON and shuts the server down.
 */
public final class SynthetixExamplePlugin extends JavaPlugin implements Listener {

    private SynthetixLib lib;
    private PdcKey<String> demoId;
    private ConfigHandle<Settings> settings;
    private LocaleHandle<Messages> lang;
    private ConfigCollection<Airdrop> airdrops;

    @Override
    public void onEnable() {
        lib = SynthetixLib.init(this);

        if (Boolean.getBoolean("synthetix.selftest")) {
            getServer().getPluginManager().registerEvents(new SelfTestTrigger(this, lib), this);
            getLogger().info("Self-test mode: tests run after server load, then the server stops.");
            return;
        }

        // Demo of the core services (skipped in self-test mode)
        demoId = lib.keys().string("demo_id");
        getServer().getPluginManager().registerEvents(this, this);
        lib.scheduler().global().runTimer(1200, 1200, task -> getLogger().info("SynthetixLib demo timer: still running"));

        enableConfigDemo();
    }

    /** settings.yml, airdrops/*.yml and lang/*.yml; there are no commands yet, so it only logs what it loaded. */
    private void enableConfigDemo() {
        final Configs configs = Configs.create(lib);
        settings = configs.file("settings.yml", Settings.class, Settings::defaults).load();
        lang = configs.locale("lang", Messages.class, Messages::defaults)
                .defaultLocale(Locale.US)
                .bundled("th_TH")
                .perPlayer(true)
                .load();
        airdrops = configs.collection("airdrops", Airdrop.class, id -> settings.get().defaultAirdrop().withName(id))
                .load();

        if (airdrops.ids().isEmpty()) {
            airdrops.create("example");
            lang.send(getServer().getConsoleSender(), Messages::airdropCreated, Placeholder.unparsed("id", "example"));
        }
        airdrops.onChange(change -> getLogger().info("Airdrop " + change.id() + ": " + change.getClass().getSimpleName()));
        lang.send(getServer().getConsoleSender(), Messages::airdropsLoaded,
                Placeholder.unparsed("count", String.valueOf(airdrops.ids().size())),
                Placeholder.unparsed("ids", String.join(", ", airdrops.ids())));
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        // Run on the entity's own scheduler (Folia-safe)
        lib.scheduler().entity(player).runLater(20, () -> {
            final ItemStack item = lib.items().of(Material.PAPER)
                    .name("<gold>Demo ticket")
                    .lore("<gray>Given to <player>", Placeholder.unparsed("player", player.getName()))
                    .glint(true)
                    .set(demoId, "welcome")
                    .build();
            if (settings.get().welcomeTicket()) {
                player.getInventory().addItem(item);
            }
            lang.send(player, Messages::welcome, Placeholder.unparsed("name", player.getName()));
        });
    }

    @Override
    public void onDisable() {
        if (lib != null) {
            lib.close();
        }
    }
}
