package com.github.dewarepk.synthetixexample;

import com.github.dewarepk.synthetixexample.demo.Airdrop;
import com.github.dewarepk.synthetixexample.demo.ClaimStore;
import com.github.dewarepk.synthetixlib.database.Database;
import com.github.dewarepk.synthetixlib.database.DatabaseSettings;
import com.github.dewarepk.synthetixexample.demo.DemoDialogs;
import com.github.dewarepk.synthetixexample.demo.DemoMenus;
import com.github.dewarepk.synthetixexample.demo.Messages;
import com.github.dewarepk.synthetixexample.demo.Settings;
import com.github.dewarepk.synthetixexample.demo.command.ExampleCommand;
import com.github.dewarepk.synthetixlib.command.CommandService;
import com.github.dewarepk.synthetixexample.selftest.SelfTestTrigger;
import com.github.dewarepk.synthetixlib.config.ConfigCollection;
import com.github.dewarepk.synthetixlib.config.ConfigHandle;
import com.github.dewarepk.synthetixlib.config.Configs;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.core.pdc.PdcKey;
import com.github.dewarepk.synthetixlib.dialog.DialogService;
import com.github.dewarepk.synthetixlib.dialog.SynthetixDialogs;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
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
    private final SynthetixDialogs dialogRegistry;

    /** Made by {@link ExampleBootstrap}, which registers the dialogs of the dialog registry. */
    public SynthetixExamplePlugin(final SynthetixDialogs dialogRegistry) {
        this.dialogRegistry = dialogRegistry;
    }

    /** @return what the bootstrap registered; the self-tests read it */
    public SynthetixDialogs dialogRegistry() {
        return dialogRegistry;
    }

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

    /** settings.yml, airdrops/*.yml and lang/*.yml, and the {@code /example} command on top of them. */
    private void enableConfigDemo() {
        final Configs configs = Configs.create(lib);
        DatabaseSettings.register(configs.codecs());
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

        // Closed together with lib; onEnable may block on the first migration.
        final Database database = Database.create(lib, settings.get().database());
        final ClaimStore claims = ClaimStore.open(database);

        final DialogService dialogs = DialogService.create(lib, configs)
                .settings(settings.get().dialog())
                .bootstrapped(dialogRegistry);
        dialogs.registered(ExampleBootstrap.HELP)
                .action("ping", click -> click.player().sendMessage(Component.text("Pong!")))
                .build();

        dialogs.messages(lang, Messages::dialog);
        final DemoMenus menus = DemoMenus.create(lib, configs, airdrops, lang, claims, dialogs.opener());
        dialogs.link("menu", menus.opener());
        final DemoDialogs demoDialogs = DemoDialogs.create(dialogs, lang);

        CommandService.create(lib)
                .messages(lang, Messages::commands)
                .register(ExampleCommand.build(settings, airdrops, lang, menus, demoDialogs, database, claims));
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
