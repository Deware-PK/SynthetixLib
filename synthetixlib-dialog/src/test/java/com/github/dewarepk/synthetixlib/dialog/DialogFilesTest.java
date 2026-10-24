package com.github.dewarepk.synthetixlib.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.config.FilesReloadResult;
import com.github.dewarepk.synthetixlib.core.text.TextService;
import com.github.dewarepk.synthetixlib.dialog.file.DialogKind;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Dialog files on disk: the bundled copy, the builder cross-check and the registry that reloads them. */
class DialogFilesTest {

    /** Public: the config codecs read answers into it by reflection, without setAccessible. */
    public record SellForm(double price, String duration, String note, boolean anonymous) {
    }

    public record CountForm(int amount) {
    }

    public record WrongKinds(String price, boolean duration, int note) {
    }

    public record Missing(double price, String reason) {
    }

    public enum Length { H1, H12 }

    public record Enumerated(Length duration) {
    }

    private static final String SELL = """
            # A comment the buyer will read
            title: 'Sell <item-name> for <fee>'
            type: confirmation
            inputs:
              price: { type: number, label: 'Price', min: 1, max: 1000 }
              duration:
                type: option
                label: 'Duration'
                options:
                  h1: { display: '1 hour' }
                  h12: { display: '12 hours' }
              note: { type: text, label: 'Note' }
              anonymous: { type: bool, label: 'Hide' }
            buttons:
              yes: { label: 'Sell', click: ['action:sell'] }
              no: { label: 'Cancel', click: [close, 'action:typo'] }
            errors:
              price: '<red>Between <min> and <max>'
            """;

    @TempDir
    Path folder;

    private final Map<String, String> jar = new HashMap<>();
    private final List<String> logged = new ArrayList<>();
    private DialogRegistry registry;

    @BeforeEach
    void setUp() {
        final DialogStore store = new DialogStore(folder.resolve("dialogs"), path -> {
            final String text = jar.get(path);
            return text == null ? null : (InputStream) new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        });
        final Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
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
        });
        registry = new DialogRegistry(new DialogLoader(store, new DialogParser(DialogFixtures.codecs()),
                TextService.create(), DialogFixtures.codecs()), logger, DialogFixtures.NO_HOST);
    }

    private DialogBuilder<Void> dialog(final String id) {
        return new DialogBuilder<>(registry, id);
    }

    private boolean logHas(final String... parts) {
        return logged.stream().anyMatch(line -> {
            for (final String part : parts) {
                if (!line.contains(part)) {
                    return false;
                }
            }
            return true;
        });
    }

    // --- the file ----------------------------------------------------------------------------------------------

    @Test
    void bundledFileIsCopiedOnceAndNeverOverwritten() throws IOException {
        jar.put("dialogs/sell.yml", SELL);
        final Path file = folder.resolve("dialogs/sell.yml");
        dialog("sell").build();
        assertEquals(SELL, Files.readString(file));

        final String edited = "# mine\ntitle: 'Edited'\ntype: notice\n";
        Files.writeString(file, edited);
        jar.put("dialogs/sell.yml", "title: 'New version'\ntype: notice\n");
        registry.reload();
        assertEquals(edited, Files.readString(file));
        assertEquals("Edited", registry.find("sell").file().title());
    }

    @Test
    void noFileAndNoBundledOneFailsClearly() {
        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> dialog("ghost").build());
        assertTrue(e.getMessage().contains("dialogs/ghost.yml"), e.getMessage());
    }

    @Test
    void idsAreBuiltOnce() {
        jar.put("dialogs/a.yml", "title: t\ntype: notice\n");
        dialog("a").build();
        assertThrows(IllegalStateException.class, () -> dialog("a").build());
    }

    @Test
    void reloadKeepsTheLastGoodDialogWhenTheFileIsNotYaml() throws IOException {
        jar.put("dialogs/a.yml", "title: first\ntype: notice\n");
        final DialogType<Void> type = dialog("a").build();
        Files.writeString(folder.resolve("dialogs/a.yml"), "title: [oops");
        final FilesReloadResult result = registry.reload();
        assertFalse(result.ok());
        assertEquals("first", type.file().title());
        Files.writeString(folder.resolve("dialogs/a.yml"), "title: second\ntype: notice\n");
        assertTrue(registry.reload().changed());
        assertEquals("second", type.file().title());
        assertEquals(DialogKind.NOTICE, type.file().kind());
    }

    // --- builder cross-check ----------------------------------------------------------------------------------

    @Test
    void unregisteredActionMakesOnlyThatButtonInert() {
        jar.put("dialogs/sell.yml", SELL);
        final DialogType<Void> type = dialog("sell")
                .submit("sell", SellForm.class, (form, click) -> { })
                .placeholder("item-name", view -> Component.text("x"))
                .placeholder("fee", view -> Component.text("5"))
                .build();
        assertFalse(type.isInert("buttons.yes"));
        assertTrue(type.isInert("buttons.no"));
        assertTrue(logHas("dialogs/sell.yml", "buttons.no.click", "action 'typo' is not registered"));
        assertFalse(logHas("placeholder"), logged.toString());
    }

    @Test
    void unregisteredPlaceholderOnlyWarns() {
        jar.put("dialogs/sell.yml", SELL);
        final DialogType<Void> type = dialog("sell")
                .submit("sell", SellForm.class, (form, click) -> { })
                .action("typo", click -> { })
                .build();
        assertFalse(type.isInert("buttons.yes"));
        assertFalse(type.isInert("buttons.no"));
        assertTrue(logHas("title", "<item-name>"));
        assertTrue(logHas("title", "<fee>"));
        assertFalse(logHas("errors.price"), "min and max are built in for error lines: " + logged);
    }

    @Test
    void matchingSubmitFormIsAccepted() {
        jar.put("dialogs/sell.yml", SELL);
        dialog("sell").submit("sell", SellForm.class, (form, click) -> { }).action("typo", click -> { }).build();
        assertFalse(logHas("submit"), logged.toString());
    }

    @Test
    void submitRecordWithAMissingInputIsReportedAndItsButtonIsInert() {
        jar.put("dialogs/sell.yml", SELL);
        final DialogType<Void> type = dialog("sell")
                .submit("sell", Missing.class, (form, click) -> { })
                .action("typo", click -> { })
                .build();
        assertTrue(type.isInert("buttons.yes"));
        assertFalse(type.isInert("buttons.no"));
        assertTrue(logHas("dialogs/sell.yml", "inputs.reason", "submit 'sell' (Missing)", "no input with this key"),
                logged.toString());
    }

    @Test
    void submitRecordWithTheWrongKindsIsReportedPerKey() {
        jar.put("dialogs/sell.yml", SELL);
        final DialogType<Void> type = dialog("sell")
                .submit("sell", WrongKinds.class, (form, click) -> { })
                .action("typo", click -> { })
                .build();
        assertTrue(type.isInert("buttons.yes"));
        assertTrue(logHas("inputs.price", "needs an int, long, float or double"), logged.toString());   // String
        assertTrue(logHas("inputs.duration"), logged.toString());                                        // boolean
        assertTrue(logHas("inputs.note"), logged.toString());                                            // int
    }

    @Test
    void numberInputIntoAnIntComponentNeedsWholeNumbers() {
        jar.put("dialogs/count.yml", """
                title: t
                type: confirmation
                inputs:
                  amount: { type: number, label: 'A', min: 1, max: 64, step: 1 }
                buttons:
                  yes: { label: 'Y', click: ['action:go'] }
                  no: { label: 'N' }
                """);
        final DialogType<Void> ok = dialog("count").submit("go", CountForm.class, (form, click) -> { }).build();
        assertFalse(ok.isInert("buttons.yes"));
        assertFalse(logHas("submit"), logged.toString());

        jar.put("dialogs/half.yml", """
                title: t
                type: confirmation
                inputs:
                  amount: { type: number, label: 'A', min: 0.5, max: 64 }
                buttons:
                  yes: { label: 'Y', click: ['action:go'] }
                  no: { label: 'N' }
                """);
        final DialogType<Void> half = dialog("half").submit("go", CountForm.class, (form, click) -> { }).build();
        assertTrue(half.isInert("buttons.yes"));
        assertTrue(logHas("dialogs/half.yml", "inputs.amount", "expected an integer"), logged.toString());
    }

    @Test
    void optionIdsMustBeKnownToAnEnumComponent() {
        jar.put("dialogs/sell.yml", SELL);
        final DialogType<Void> type = dialog("sell")
                .submit("sell", Enumerated.class, (form, click) -> { })
                .action("typo", click -> { })
                .build();
        // ids h1 / h12 are not H1 / H12 as written... EnumCodec ignores case, so they match
        assertFalse(type.isInert("buttons.yes"), logged.toString());
        jar.put("dialogs/enum.yml", """
                title: t
                type: confirmation
                inputs:
                  duration: { type: option, label: 'D', options: { h1: {}, forever: {} } }
                buttons:
                  yes: { label: 'Y', click: ['action:go'] }
                  no: { label: 'N' }
                """);
        final DialogType<Void> bad = dialog("enum").submit("go", Enumerated.class, (form, click) -> { }).build();
        assertTrue(bad.isInert("buttons.yes"));
        assertTrue(logHas("dialogs/enum.yml", "inputs.duration", "unknown value 'forever'"), logged.toString());
    }

    @Test
    void inputRegisteredForAKeyTheFileLacksWarns() {
        jar.put("dialogs/a.yml", "title: t\ntype: notice\n");
        dialog("a").input("price", view -> 10).build();
        assertTrue(logHas("dialogs/a.yml", "inputs.price"), logged.toString());
    }

    @Test
    void urlAndCopyMustBeTheOnlyStepOfAButton() {
        jar.put("dialogs/a.yml", """
                title: t
                type: confirmation
                buttons:
                  yes: { label: 'Y', click: ['url:https://example.com'] }
                  no: { label: 'N', click: ['copy:x', close] }
                """);
        final DialogType<Void> type = dialog("a").build();
        assertFalse(type.isInert("buttons.yes"));
        assertTrue(type.isInert("buttons.no"));
        assertTrue(logHas("buttons.no.click", "url and copy only work as the only step"), logged.toString());
    }

    @Test
    void unregisteredItemPlaceholderWarns() {
        jar.put("dialogs/a.yml", """
                title: t
                type: notice
                body:
                  - item: { placeholder: lot-item }
                  - item: { placeholder: known }
                """);
        dialog("a").item("known", view -> new org.bukkit.inventory.ItemStack(org.bukkit.Material.STONE)).build();
        assertTrue(logHas("body[0].item.placeholder", "lot-item"), logged.toString());
        assertFalse(logHas("body[1]"), logged.toString());
    }

    // --- builder rules ------------------------------------------------------------------------------------------

    @Test
    void builderRejectsBadAndDuplicateNames() {
        final DialogBuilder<Void> builder = dialog("x");
        assertThrows(IllegalArgumentException.class, () -> builder.action("Bad Name", click -> { }));
        assertThrows(IllegalArgumentException.class, () -> builder.placeholder("player", view -> Component.empty()));
        builder.action("go", click -> { });
        assertThrows(IllegalArgumentException.class, () -> builder.action("go", click -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> builder.submit("go", CountForm.class, (form, click) -> { }));
        assertThrows(IllegalStateException.class, builder::withContext);
    }

    @Test
    void aBuilderIsUsedOnce() {
        jar.put("dialogs/a.yml", "title: t\ntype: notice\n");
        final DialogBuilder<Void> builder = dialog("a");
        builder.build();
        assertThrows(IllegalStateException.class, builder::build);
        assertThrows(IllegalStateException.class, () -> builder.action("go", click -> { }));
    }

    @Test
    void closedRegistryRefusesNewDialogs() {
        jar.put("dialogs/a.yml", "title: t\ntype: notice\n");
        registry.close();
        assertThrows(IllegalStateException.class, () -> dialog("a").build());
        assertThrows(IllegalStateException.class, registry::reload);
    }
}
