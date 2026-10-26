package com.github.dewarepk.synthetixexample.selftest;

import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.selftest.SelfTest;
import com.github.dewarepk.synthetixlib.selftest.SelfTests;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Wires every self-test of the library into {@link SelfTests}. Inert unless the JVM has
 * {@code -Dsynthetix.selftest=true} (the Gradle {@code smokeTest} task sets it).
 */
public final class ExampleSelfTests {

    private ExampleSelfTests() {
    }

    /**
     * Call from {@code onEnable}: the test commands must be registered before Paper fires its COMMANDS event, the
     * tests themselves are built after the server loaded.
     */
    public static void install(final JavaPlugin plugin, final SynthetixLib lib) {
        // @snippet:selftest-wiring
        final CommandSelfTests commandTests = CommandSelfTests.install(plugin, lib);
        SelfTests.of(lib)
                .addLater(() -> {
                    final List<SelfTest> tests = new ArrayList<>();
                    tests.addAll(HarnessSelfTests.all(plugin));
                    tests.addAll(CoreSelfTests.all(plugin, lib));
                    tests.addAll(CoreSchedulerSelfTests.all(plugin, lib));
                    tests.addAll(CorePdcSelfTests.all(plugin, lib));
                    tests.addAll(CoreItemSelfTests.all(plugin, lib));
                    tests.addAll(ConfigSelfTests.all(plugin));
                    tests.addAll(ConfigBukkitSelfTests.all(plugin, lib));
                    tests.addAll(ConfigFileSelfTests.all(plugin, lib));
                    tests.addAll(ConfigCollectionSelfTests.all(plugin, lib));
                    tests.addAll(ConfigLocaleSelfTests.all(plugin, lib));
                    tests.addAll(commandTests.all());
                    tests.addAll(GuiSelfTests.all(plugin, lib));
                    tests.addAll(DatabaseSelfTests.all(plugin, lib));
                    tests.addAll(DialogSelfTests.all(plugin, lib));
                    tests.addAll(RecipeSelfTests.all(plugin, lib));
                    tests.addAll(UpgradeSelfTests.all(plugin, lib));
                    // one line per module as modules are added
                    return tests;
                })
                .runIfRequested();
        // @end
    }
}
