package com.github.dewarepk.synthetixexample.selftest;

import static com.github.dewarepk.synthetixlib.selftest.SelfTest.require;

import com.github.dewarepk.synthetixlib.config.Configs;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.config.annotation.Comment;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.selftest.SelfTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** Self-test for locale files: bundled copy from the jar, language choice, sending to the console. */
final class ConfigLocaleSelfTests {

    public record Messages(@Comment("Placeholders: <name>") String hello, List<String> lines) {

        static Messages defaults() {
            return new Messages("<green>Hello <name>", List.of("<gray>line one", "<gray>line two"));
        }
    }

    private ConfigLocaleSelfTests() {
    }

    private static void deleteTree(final Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            for (final Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    static List<SelfTest> all(final JavaPlugin plugin, final SynthetixLib lib) {
        final Configs configs = Configs.create(lib);
        return List.of(
                SelfTest.sync("config.locale.send", () -> {
                    final Path folder = plugin.getDataFolder().toPath().resolve("selftest-lang");
                    deleteTree(folder);
                    final LocaleHandle<Messages> lang = configs.locale("selftest-lang", Messages.class,
                                    Messages::defaults)
                            .defaultLocale(Locale.US)
                            .bundled("th_TH")
                            .perPlayer(true)
                            .load();
                    require(Files.exists(folder.resolve("en_US.yml")), "default file created");
                    require(Files.readString(folder.resolve("th_TH.yml")).contains("lines:"),
                            "bundled file copied from the jar and its missing keys filled");

                    final CommandSender console = plugin.getServer().getConsoleSender();
                    final var name = Placeholder.unparsed("name", "console");
                    lang.send(console, Messages::hello, name);
                    lang.sendLines(console, Messages::lines);

                    require(lib.text().plain(lang.get(console, Messages::hello, name)).equals("Hello console"),
                            "the console has no locale, so it gets the default");
                    require(lib.text().plain(lang.get(Locale.of("th", "TH"), Messages::hello, name))
                                    .equals("Sawasdee console"),
                            "a Thai player gets the bundled file");
                    require(lib.text().plain(lang.get(Locale.FRANCE, Messages::hello, name))
                                    .equals("Hello console"),
                            "an unknown language falls back to the default");
                    require(lib.text().plain(lang.getLines(console, Messages::lines)).equals("line one\nline two"),
                            "list message joined by newline");
                    require(lang.reload().ok(), "reload of untouched files is clean");
                })
        );
    }
}
