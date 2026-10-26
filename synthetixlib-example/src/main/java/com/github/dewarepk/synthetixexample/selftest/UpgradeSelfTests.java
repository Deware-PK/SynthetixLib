package com.github.dewarepk.synthetixexample.selftest;

import static com.github.dewarepk.synthetixlib.selftest.SelfTest.require;

import com.github.dewarepk.synthetixexample.demo.Messages;
import com.github.dewarepk.synthetixlib.config.Configs;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.dialog.DialogService;
import com.github.dewarepk.synthetixlib.dialog.DialogType;
import com.github.dewarepk.synthetixlib.dialog.file.DialogInput;
import com.github.dewarepk.synthetixlib.selftest.SelfTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Self-test for a server that is updated: the files of the last release stay in the data folder while the plugin jar
 * ships newer ones (bundled files are copied once, never overwritten).
 */
final class UpgradeSelfTests {

    /** {@code dialogs/sell.yml} as 0.7.0 shipped it: {@code config-version: 1}, the price is a slider. */
    private static final String SELL_0_7 = """
            # The /example dialog sell form: number, option, text and bool inputs; the item in hand is the context.
            config-version: 1
            title: '<lang:sell-title>'
            type: confirmation
            body:
              - message: '<lang:sell-info>'
                width: 250
            inputs:
              price:
                type: number
                label: '<lang:sell-price>'
                min: 1
                max: 1000000
                step: 1
                initial: 100
              note:
                type: text
                label: '<lang:sell-note>'
                max-length: 64
            buttons:
              yes: { label: '<lang:sell-yes>', click: ['action:sell'] }
              no: { label: '<lang:sell-no>', click: [close] }
            errors:
              price: '<lang:sell-error-price>'
            """;

    private static final String ADDED_KEY = "sell-error-too-high";
    private static final String CUSTOM_TITLE = "<gold>My own title";

    private UpgradeSelfTests() {
    }

    static List<SelfTest> all(final JavaPlugin plugin, final SynthetixLib lib) {
        final Configs configs = Configs.create(lib);
        return List.of(SelfTest.sync("upgrade.old-files", () -> oldFiles(plugin, lib, configs)));
    }

    /**
     * A 0.7 dialog file and a 0.7 language file meet the 0.8 code: the dialog file is kept and a warning says it is
     * behind, the language file is given the key it lacks and keeps what the owner wrote.
     */
    private static void oldFiles(final JavaPlugin plugin, final SynthetixLib lib, final Configs configs)
            throws IOException {
        final Path data = plugin.getDataFolder().toPath();
        final Path sell = data.resolve("dialogs/sell.yml");
        final byte[] before = Files.exists(sell) ? Files.readAllBytes(sell) : null;
        final List<String> logged = new CopyOnWriteArrayList<>();
        final Handler handler = new Handler() {
            @Override
            public void publish(final LogRecord record) {
                logged.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        plugin.getLogger().addHandler(handler);
        try {
            languageFile(data, configs);
            dialogFile(sell, lib, configs, logged);
        } finally {
            plugin.getLogger().removeHandler(handler);
            if (before == null) {
                Files.deleteIfExists(sell);
            } else {
                Files.write(sell, before);
            }
        }
    }

    private static void languageFile(final Path data, final Configs configs) throws IOException {
        final Path folder = data.resolve("selftest-upgrade");
        deleteTree(folder);
        configs.locale("selftest-upgrade/lang", Messages.class, Messages::defaults).defaultLocale(Locale.US).load();
        final Path file = folder.resolve("lang/en_US.yml");

        // the file of 0.7: no sell-error-too-high, and a text the owner changed
        final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        require(lines.stream().anyMatch(line -> line.startsWith("  " + ADDED_KEY + ":")), "the generated file has no "
                + ADDED_KEY);
        final String old = lines.stream()
                .filter(line -> !line.startsWith("  " + ADDED_KEY + ":"))
                .map(line -> line.startsWith("  sell-title:") ? "  sell-title: " + quoted(CUSTOM_TITLE) : line)
                .collect(Collectors.joining("\n", "", "\n"));
        Files.writeString(file, old, StandardCharsets.UTF_8);

        final LocaleHandle<Messages> lang = configs.locale("selftest-upgrade/lang", Messages.class, Messages::defaults)
                .defaultLocale(Locale.US).load();

        final String text = Files.readString(file, StandardCharsets.UTF_8);
        require(text.contains("  " + ADDED_KEY + ":"), "the missing key was not added to the old file:\n" + text);
        require(text.contains(CUSTOM_TITLE), "the owner's own text was lost:\n" + text);
        require(!plain(lang.get((Locale) null, messages -> messages.dialog().get(ADDED_KEY))).isEmpty(),
                "the added key is empty at runtime");
        require(plain(lang.get((Locale) null, messages -> messages.dialog().get("sell-title"))).equals("My own title"),
                "the owner's text is not the one in use");
    }

    private static void dialogFile(final Path sell, final SynthetixLib lib, final Configs configs,
                                   final List<String> logged) throws IOException {
        Files.createDirectories(sell.getParent());
        Files.writeString(sell, SELL_0_7, StandardCharsets.UTF_8);
        final byte[] old = Files.readAllBytes(sell);

        final DialogService dialogs = DialogService.create(lib, configs);
        final DialogType<Void> type = dialogs.dialog("sell").build();

        require(logged.stream().anyMatch(line -> line.equals("[dialogs/sell.yml] the plugin ships config-version 3,"
                        + " this file is 1: delete it to get the new default or update it by hand (old copy kept)")),
                "no config-version warning was logged: " + logged);
        require(Arrays.equals(old, Files.readAllBytes(sell)), "the old dialog file was changed");
        require(type.file().inputs().get("price") instanceof DialogInput.NumberInput,
                "the old copy (slider) is not the one in use: " + type.file().inputs().get("price"));
    }

    private static String plain(final Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static String quoted(final String text) {
        return "'" + text.replace("'", "''") + "'";
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
}
