package com.example.myplugin;

import com.github.dewarepk.synthetixlib.gui.GuiService;
import com.github.dewarepk.synthetixlib.gui.MenuType;
import org.bukkit.entity.Player;

/** The menu {@code menus/main.yml}. The file is copied from the jar on the first start and never overwritten. */
public final class MainMenu {

    private final MenuType<Void> menu;

    private MainMenu(final MenuType<Void> menu) {
        this.menu = menu;
    }

    public static MainMenu create(final GuiService gui) {
        return new MainMenu(gui.menu("main").build());
    }

    /** Opens the menu; any thread. */
    public void open(final Player player) {
        menu.open(player);
    }
}
