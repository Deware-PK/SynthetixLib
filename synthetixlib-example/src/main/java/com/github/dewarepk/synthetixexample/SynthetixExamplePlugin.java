package com.github.dewarepk.synthetixexample;

import com.github.dewarepk.synthetixexample.selftest.SelfTestTrigger;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Example consumer of SynthetixLib. Doubles as the smoke-test bed: when started with
 * {@code -Dsynthetix.selftest=true} it runs all self-tests once the server has loaded,
 * writes the result JSON and shuts the server down.
 */
public final class SynthetixExamplePlugin extends JavaPlugin {

    private SynthetixLib lib;

    @Override
    public void onEnable() {
        lib = SynthetixLib.init(this);

        if (Boolean.getBoolean("synthetix.selftest")) {
            getServer().getPluginManager().registerEvents(new SelfTestTrigger(this, lib), this);
            getLogger().info("Self-test mode: tests run after server load, then the server stops.");
        }
    }

    @Override
    public void onDisable() {
        if (lib != null) {
            lib.close();
        }
    }
}
