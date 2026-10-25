package com.github.dewarepk.synthetixexample.demo.recipes;

import com.github.dewarepk.synthetixlib.config.ConfigHandle;
import com.github.dewarepk.synthetixlib.config.Configs;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.dialog.DialogService;
import com.github.dewarepk.synthetixlib.gui.GuiService;
import com.github.dewarepk.synthetixlib.gui.MenuType;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Recipe 3: one reload for the config, the language files, the menus and the dialogs. */
public final class ReloadRecipe {

    private final ConfigHandle<RecipeSettings> settings;
    private final LocaleHandle<RecipeMessages> lang;
    private final GuiService gui;
    private final DialogService dialogs;
    private final MenuType<Void> info;

    private ReloadRecipe(final ConfigHandle<RecipeSettings> settings, final LocaleHandle<RecipeMessages> lang,
                         final GuiService gui, final DialogService dialogs, final MenuType<Void> info) {
        this.settings = settings;
        this.lang = lang;
        this.gui = gui;
        this.dialogs = dialogs;
        this.info = info;
    }

    // @snippet:recipe-reload-create
    public static ReloadRecipe create(final SynthetixLib lib, final Configs configs,
                                      final LocaleHandle<RecipeMessages> lang, final GuiService gui,
                                      final DialogService dialogs) {
        final ConfigHandle<RecipeSettings> settings =
                configs.file("recipes.yml", RecipeSettings.class, RecipeSettings::defaults).load();
        settings.onReload(next -> lib.plugin().getLogger().info("daily amount is now " + next.dailyAmount()));

        // The placeholders read the snapshot each time a menu or dialog is drawn, so a reload shows at once.
        final MenuType<Void> info = gui.menu("recipe-info")
                .placeholder("daily", view -> Component.text(settings.get().dailyAmount()))
                .build();
        dialogs.dialog("recipe-notice")
                .placeholder("daily", view -> Component.text(settings.get().dailyAmount()))
                .build();
        return new ReloadRecipe(settings, lang, gui, dialogs, info);
    }
    // @end

    // @snippet:recipe-reload
    /** @return every problem found while re-reading; empty if all went well */
    public List<String> reload() {
        final List<String> errors = new ArrayList<>(settings.reload().errors());
        errors.addAll(lang.reload().errors());
        errors.addAll(gui.reload().errors());
        errors.addAll(dialogs.reload().errors());
        return errors;
    }
    // @end

    /** @return the settings snapshot */
    public RecipeSettings settings() {
        return settings.get();
    }

    /** Opens the info menu; any thread. */
    public void open(final Player player) {
        info.open(player);
    }
}
