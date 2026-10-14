package com.github.dewarepk.synthetixexample.selftest;

import static com.github.dewarepk.synthetixexample.selftest.SelfTest.require;

import com.github.dewarepk.synthetixlib.config.Configs;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

/** Self-tests for synthetixlib-config. */
final class ConfigSelfTests {

    private ConfigSelfTests() {
    }

    static List<SelfTest> all(final JavaPlugin plugin) {
        return List.of(
                SelfTest.sync("config.setup", () -> {
                    // Separate lib instance so closing it does not affect the shared test lib.
                    final SynthetixLib own = SynthetixLib.init(plugin);
                    final Configs configs = Configs.create(own);
                    require(!configs.isClosed(), "Configs must be open after create");
                    own.close();
                    require(configs.isClosed(), "lib.close() must close attached Configs");
                    boolean rejected = false;
                    try {
                        own.attach(() -> { });
                    } catch (final IllegalStateException expected) {
                        rejected = true;
                    }
                    require(rejected, "attach after close must throw IllegalStateException");
                })
        );
    }
}
