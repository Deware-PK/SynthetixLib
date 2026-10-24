package com.github.dewarepk.synthetixexample;

import com.github.dewarepk.synthetixlib.dialog.SynthetixDialogs;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NullMarked;

/**
 * Registers {@code dialogs/help.yml} in the dialog registry while the server starts, so it is in the pause menu's quick
 * actions (referenced from paper-plugin.yml). The registry object goes to the plugin, which gives it to its
 * {@code DialogService}: no static state.
 */
@NullMarked
public final class ExampleBootstrap implements PluginBootstrap {

    /** The registry key of the help dialog. */
    public static final String HELP = "synthetixexample:help";

    private SynthetixDialogs registry;

    @Override
    public void bootstrap(final BootstrapContext context) {
        registry = SynthetixDialogs.bootstrap(context)
                .register("help", HELP)
                .quickActions("help");
    }

    @Override
    public JavaPlugin createPlugin(final PluginProviderContext context) {
        return new SynthetixExamplePlugin(registry);
    }
}
