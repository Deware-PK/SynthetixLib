package com.example.myplugin;

import com.github.dewarepk.synthetixlib.command.CommandService;
import com.github.dewarepk.synthetixlib.config.ConfigHandle;
import com.github.dewarepk.synthetixlib.config.Configs;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.dialog.DialogService;
import com.github.dewarepk.synthetixlib.gui.GuiService;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyPlugin extends JavaPlugin implements Listener {

    private SynthetixLib lib;
    private ConfigHandle<Settings> settings;
    private LocaleHandle<Messages> lang;

    @Override
    public void onEnable() {
        // @snippet:template-enable
        lib = SynthetixLib.init(this);
        final Configs configs = Configs.create(lib);
        settings = configs.file("config.yml", Settings.class, Settings::defaults).load();
        lang = configs.locale("lang", Messages.class, Messages::defaults).defaultLocale(Locale.US).load();

        final DialogService dialogs = DialogService.create(lib, configs).messages(lang, Messages::dialog);
        final GreetDialog greet = GreetDialog.create(dialogs, lang);
        final GuiService gui = GuiService.create(lib, configs).messages(lang, Messages::gui)
                .link("dialog", dialogs.opener());
        final MainMenu menu = MainMenu.create(gui);

        CommandService.create(lib)
                .messages(lang, Messages::commands)
                .register(MyPluginCommand.build(settings, lang, menu, greet));

        // Database module: Database.create(lib, <DatabaseSettings from config.yml>); closed together with lib.
        getServer().getPluginManager().registerEvents(this, this);
        // @end
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        if (!settings.get().welcome()) {
            return;
        }
        final Player player = event.getPlayer();
        // Entity scheduler (Folia-safe); never the Bukkit scheduler.
        lib.scheduler().entity(player).runLater(settings.get().welcomeDelayTicks(),
                () -> lang.send(player, Messages::welcome, Placeholder.unparsed("name", player.getName())));
    }

    @Override
    public void onDisable() {
        // @snippet:template-disable
        if (lib != null) {
            lib.close();
        }
        // @end
    }
}
