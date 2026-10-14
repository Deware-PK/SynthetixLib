package com.github.dewarepk.synthetixexample.selftest;

import static com.github.dewarepk.synthetixexample.selftest.SelfTest.require;

import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.plugin.java.JavaPlugin;

/** Self-tests for synthetixlib-core. Add one per feature that touches the server. */
final class CoreSelfTests {

    private CoreSelfTests() {
    }

    static List<SelfTest> all(final JavaPlugin plugin, final SynthetixLib lib) {
        return List.of(
                SelfTest.sync("core.init", () -> {
                    require(lib.plugin() == plugin, "lib.plugin() must be the owning plugin");
                    require(!lib.isClosed(), "lib must be open after init");
                }),
                SelfTest.sync("core.relocated", () -> {
                    // Proves the shaded jar was relocated (docs/adr/0005).
                    String pkg = SynthetixLib.class.getPackageName();
                    require(pkg.startsWith("com.github.dewarepk.synthetixexample.libs."),
                            "SynthetixLib not relocated, package = " + pkg);
                }),
                SelfTest.sync("core.text.send", () ->
                        lib.text().send(plugin.getServer().getConsoleSender(), "<green>text self-test <n>",
                                Placeholder.unparsed("n", "ok")))
        );
    }
}
