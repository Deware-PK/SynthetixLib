package com.github.dewarepk.synthetixlib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.core.text.TextService;
import com.github.dewarepk.synthetixlib.config.FilesReloadResult;
import com.github.dewarepk.synthetixlib.gui.file.ClickAction;
import com.github.dewarepk.synthetixlib.gui.file.ClickKind;
import com.github.dewarepk.synthetixlib.gui.file.MenuFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Menu files on disk: the bundled copy, the builder and the registry that reloads them. */
class MenuFilesTest {

    private static final String BUNDLED = """
            # A comment the buyer will read
            title: 'Bundled'
            rows: 1
            layout: ["#B       "]
            items:
              '#': { material: stone } # trailing comment
              'B': { material: stone, click: ['action:buy'] }
            """;

    @TempDir
    Path folder;

    private final Map<String, String> jar = new HashMap<>();
    private final List<String> logged = new java.util.ArrayList<>();
    private MenuRegistry registry;

    @BeforeEach
    void setUp() {
        final MenuStore store = new MenuStore(folder.resolve("menus"), path -> {
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
        registry = new MenuRegistry(new MenuLoader(store, new MenuParser(GuiFixtures.codecs()), TextService.create(), prefix -> false), logger,
                GuiFixtures.NO_HOST);
    }

    private MenuBuilder<Void> menu(final String id) {
        return new MenuBuilder<>(registry, id);
    }

    private Path file(final String id) {
        return folder.resolve("menus").resolve(id + ".yml");
    }

    // --- bundled copy ------------------------------------------------------------------------------------------

    @Test
    void theBundledFileIsCopiedOnceWithItsCommentsAndThenParsed() throws IOException {
        jar.put("menus/shop.yml", BUNDLED);

        final MenuType<Void> shop = menu("shop").action("buy", click -> { }).build();

        assertEquals(BUNDLED, Files.readString(file("shop")), "a byte for byte copy, comments included");
        assertEquals("Bundled", shop.file().title());
        assertEquals(List.of(), logged);
        assertFalse(shop.isInert('B'));
    }

    @Test
    void anExistingFileIsNeverOverwritten() throws IOException {
        Files.createDirectories(file("shop").getParent());
        Files.writeString(file("shop"), BUNDLED.replace("Bundled", "Edited by the buyer"));
        jar.put("menus/shop.yml", BUNDLED);

        final MenuType<Void> shop = menu("shop").action("buy", click -> { }).build();
        registry.reload();

        assertEquals("Edited by the buyer", shop.file().title());
        assertTrue(Files.readString(file("shop")).contains("Edited by the buyer"));
    }

    @Test
    void aFileOlderThanTheBundledOneWarnsOnceAtLoadAndIsKept() throws IOException {
        Files.createDirectories(file("shop").getParent());
        final String old = BUNDLED.replace("title: 'Bundled'", "config-version: 1\ntitle: 'Old copy'");
        Files.writeString(file("shop"), old);
        jar.put("menus/shop.yml", BUNDLED.replace("title: 'Bundled'", "config-version: 2\ntitle: 'New default'"));

        final MenuType<Void> shop = menu("shop").action("buy", click -> { }).build();

        assertEquals(List.of("[menus/shop.yml] the plugin ships config-version 2, this file is 1: delete it to get the new"
                + " default or update it by hand (old copy kept)"), logged);
        assertEquals(old, Files.readString(file("shop")));
        assertEquals("Old copy", shop.file().title());
    }

    @Test
    void aFileWithoutVersionCountsAsOneAndEqualOrNewerFilesDoNotWarn() throws IOException {
        Files.createDirectories(file("shop").getParent());
        Files.writeString(file("shop"), BUNDLED);
        jar.put("menus/shop.yml", BUNDLED);
        menu("shop").action("buy", click -> { }).build();
        assertEquals(List.of(), logged);

        Files.writeString(file("shop"), BUNDLED.replace("title: 'Bundled'", "config-version: 3\ntitle: 'Bundled'"));
        jar.put("menus/shop.yml", BUNDLED.replace("title: 'Bundled'", "config-version: 2\ntitle: 'Bundled'"));
        assertEquals(List.of(), registry.reload().warnings());

        jar.put("menus/shop.yml", BUNDLED.replace("title: 'Bundled'", "config-version: 4\ntitle: 'Bundled'"));
        final List<String> warnings = registry.reload().warnings();
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("config-version 4, this file is 3"), warnings.get(0));
    }

    @Test
    void aMenuWithoutFileAndWithoutBundledCopyFailsFast() {
        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> menu("ghost").build());

        assertTrue(e.getMessage().contains("ghost") && e.getMessage().contains("menus/ghost.yml"), e.getMessage());
    }

    @Test
    void aDeletedFileIsCopiedAgainOnReload() throws IOException {
        jar.put("menus/shop.yml", BUNDLED);
        menu("shop").action("buy", click -> { }).build();
        Files.delete(file("shop"));

        final FilesReloadResult result = registry.reload();

        assertTrue(result.ok());
        assertTrue(Files.exists(file("shop")));
    }

    // --- build() cross-check -----------------------------------------------------------------------------------

    @Test
    void buildLogsEveryUnregisteredNameWithFileAndCharacterAndKeepsTheMenu() {
        jar.put("menus/shop.yml", BUNDLED);

        final MenuType<Void> shop = menu("shop").build();

        assertTrue(shop.isInert('B'));
        assertFalse(shop.isInert('#'));
        assertEquals(List.of("[menus/shop.yml] items.B: action 'buy' is not registered in code, the item is inert"),
                logged);
        assertEquals("Bundled", shop.file().title(), "the menu still opens");
    }

    @Test
    void buildLogsFileErrorsAtTheirPath() {
        jar.put("menus/shop.yml", BUNDLED.replace("action:buy", "teleport"));

        final MenuType<Void> shop = menu("shop").build();

        assertTrue(logged.contains("[menus/shop.yml] items.B.click[0]: unknown action 'teleport' (use close, back, "
                + "refresh, page-next, page-previous, open:<menu>, command:<cmd>, console:<cmd>, message:<text>, "
                + "sound:<key>, action:<name> or <linked prefix>:<id>)"), logged.toString());
        assertTrue(shop.file().item('B').clicks().isEmpty());
    }

    @Test
    void aFileThatIsNotYamlStartsAsAnEmptyChestAndKeepsTheLastGoodFileOnReload() throws IOException {
        jar.put("menus/shop.yml", "title: [unclosed\n  layout: {\n");
        final MenuType<Void> broken = menu("shop").build();
        assertEquals(MenuFile.empty(), broken.file());
        assertTrue(logged.get(0).startsWith("[menus/shop.yml] invalid YAML"), logged.toString());

        Files.writeString(file("shop"), BUNDLED);
        assertTrue(registry.reload().ok());
        assertEquals("Bundled", broken.file().title());

        Files.writeString(file("shop"), "title: [unclosed\n  layout: {\n");
        final FilesReloadResult result = registry.reload();
        assertFalse(result.ok());
        assertEquals("Bundled", broken.file().title(), "a broken edit keeps the menu that worked");
    }

    // --- reload ------------------------------------------------------------------------------------------------

    @Test
    void reloadPicksUpEditsAndReportsChangedOnlyWhenSomethingChanged() throws IOException {
        jar.put("menus/shop.yml", BUNDLED);
        final MenuType<Void> shop = menu("shop").action("buy", click -> { }).build();
        final MenuFile before = shop.file();

        final FilesReloadResult same = registry.reload();
        assertTrue(same.ok());
        assertFalse(same.changed());
        assertEquals(before, shop.file());

        Files.writeString(file("shop"), Files.readString(file("shop")).replace("Bundled", "Renamed"));
        final FilesReloadResult edited = registry.reload();
        assertTrue(edited.changed());
        assertEquals("Renamed", shop.file().title());
    }

    @Test
    void reloadReportsErrorsPerMenuByIdAndEveryMenuIsReadAgain() throws IOException {
        jar.put("menus/a.yml", BUNDLED);
        jar.put("menus/b.yml", BUNDLED);
        menu("b").action("buy", click -> { }).build();
        menu("a").action("buy", click -> { }).build();
        Files.writeString(file("a"), BUNDLED.replace("rows: 1", "rows: 9"));

        final FilesReloadResult result = registry.reload();

        assertEquals(List.of("a", "b"), List.copyOf(result.files().keySet()), "in name order");
        assertEquals(List.of("a: rows: must be between 1 and 6 (was 9)"), result.errors());
        assertTrue(result.files().get("b").ok());
    }

    @Test
    void reloadAfterCloseThrowsAndSoDoesBuilding() {
        jar.put("menus/shop.yml", BUNDLED);
        registry.close();

        assertThrows(IllegalStateException.class, () -> registry.reload());
        assertThrows(IllegalStateException.class, () -> menu("shop").build());
    }

    // --- builder -----------------------------------------------------------------------------------------------

    @Test
    void aSecondMenuWithTheSameIdIsRejected() {
        jar.put("menus/shop.yml", BUNDLED);
        menu("shop").action("buy", click -> { }).build();

        assertThrows(IllegalStateException.class, () -> menu("shop").build());
    }

    @Test
    void namesAreValidatedAndDuplicatesRejected() {
        final MenuBuilder<Void> builder = menu("shop");

        assertThrows(IllegalArgumentException.class, () -> builder.action("Buy", click -> { }));
        assertThrows(IllegalArgumentException.class, () -> builder.action("", click -> { }));
        assertThrows(IllegalArgumentException.class, () -> builder.placeholder("my tag", view -> Component.empty()));
        assertThrows(IllegalArgumentException.class, () -> builder.placeholder("player", view -> Component.empty()));
        assertThrows(IllegalArgumentException.class, () -> builder.placeholder("pages", view -> Component.empty()));
        assertThrows(IllegalArgumentException.class, () -> builder.state("A", view -> "on"));
        assertThrows(IllegalArgumentException.class, () -> builder.list("a/b", view -> List.<String>of(),
                (entry, view) -> null));

        builder.action("buy", click -> { }).placeholder("balance", view -> Component.empty())
                .state("notify", view -> "on").list("lots", view -> List.<String>of(), (entry, view) -> null);
        assertThrows(IllegalArgumentException.class, () -> builder.action("buy", click -> { }));
        assertThrows(IllegalArgumentException.class, () -> builder.placeholder("balance", view -> Component.empty()));
        assertThrows(IllegalArgumentException.class, () -> builder.state("notify", view -> "off"));
        assertThrows(IllegalArgumentException.class, () -> builder.list("lots", view -> List.<String>of(),
                (entry, view) -> null));
    }

    @Test
    void anActionNameIsEitherSyncOrAsyncAndTheTimeoutHasAFloor() {
        jar.put("menus/shop.yml", BUNDLED);
        final MenuBuilder<Void> builder = menu("shop");

        builder.action("buy", click -> { }).actionAsync("claim", click -> CompletableFuture.completedFuture(null));
        assertThrows(IllegalArgumentException.class, () -> builder.actionAsync("buy",
                click -> CompletableFuture.completedFuture(null)));
        assertThrows(IllegalArgumentException.class, () -> builder.action("claim", click -> { }));
        assertThrows(IllegalArgumentException.class, () -> builder.actionAsync("claim",
                click -> CompletableFuture.completedFuture(null)));
        assertThrows(IllegalArgumentException.class, () -> builder.actionAsync("Bad Name",
                click -> CompletableFuture.completedFuture(null)));
        assertThrows(IllegalArgumentException.class, () -> builder.actionAsync("quick", Duration.ofMillis(10),
                click -> CompletableFuture.completedFuture(null)));

        final Registrations<Void> registrations = builder.actionAsync("slow", Duration.ofSeconds(5),
                click -> CompletableFuture.completedFuture(null)).build().registrations();
        assertEquals(List.of("buy"), List.copyOf(registrations.actions().keySet()));
        assertEquals(Set.of("claim", "slow"), registrations.asyncActions().keySet());
        assertEquals(Duration.ofSeconds(30), registrations.asyncActions().get("claim").timeout());
        assertEquals(Duration.ofSeconds(5), registrations.asyncActions().get("slow").timeout());
    }

    @Test
    void anAsyncActionIsRegisteredForTheFileLikeAnyOther() {
        jar.put("menus/shop.yml", BUNDLED);

        final MenuType<Void> shop = menu("shop")
                .actionAsync("buy", click -> CompletableFuture.completedFuture(null)).build();

        assertFalse(shop.isInert('B'));
        assertTrue(logged.isEmpty(), logged.toString());
    }

    @Test
    void withContextAfterAnAsyncActionIsRejected() {
        final MenuBuilder<Void> builder = menu("bid").actionAsync("claim", click -> CompletableFuture.completedFuture(null));

        assertThrows(IllegalStateException.class, () -> builder.<String>withContext());
    }

    @Test
    void onListClickNeedsItsListAtBuild() {
        jar.put("menus/shop.yml", BUNDLED);

        final IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> menu("shop").onListClick("lots", (String entry, MenuClick<Void> click) -> { }).build());

        assertTrue(e.getMessage().contains("lots"), e.getMessage());
    }

    @Test
    void registrationsAreKeptAndTypedByTheContext() {
        jar.put("menus/bid.yml", BUNDLED);

        final MenuType<String> bid = menu("bid").<String>withContext()
                .placeholder("price", view -> Component.text(view.context()))
                .list("lots", view -> List.of(view.context()), (String entry, MenuView<String> view) -> null)
                .onListClick("lots", (String entry, MenuClick<String> click) -> { })
                .state("notify", view -> "on")
                .action("buy", click -> { })
                .build();

        final Registrations<String> registrations = bid.registrations();
        assertEquals(List.of("price"), List.copyOf(registrations.placeholders().keySet()));
        assertEquals(List.of("lots"), List.copyOf(registrations.lists().keySet()));
        assertTrue(registrations.lists().get("lots").onClick() != null);
        assertEquals(List.of("notify"), List.copyOf(registrations.states().keySet()));
        assertEquals(List.of("buy"), List.copyOf(registrations.actions().keySet()));
    }

    @Test
    void withContextAfterARegistrationIsRejected() {
        final MenuBuilder<Void> builder = menu("bid").action("buy", click -> { });

        assertThrows(IllegalStateException.class, () -> builder.<String>withContext());
    }

    @Test
    void aBuilderCannotBeUsedAfterBuild() {
        jar.put("menus/shop.yml", BUNDLED);
        final MenuBuilder<Void> builder = menu("shop").action("buy", click -> { });
        builder.build();

        assertThrows(IllegalStateException.class, builder::build);
        assertThrows(IllegalStateException.class, () -> builder.action("again", click -> { }));
    }

    @Test
    void clickBindingsOfTheBundledMenuAreAvailable() {
        jar.put("menus/shop.yml", BUNDLED);

        final MenuType<Void> shop = menu("shop").action("buy", click -> { }).build();

        assertEquals(List.of(new ClickAction.Run("buy")), shop.file().item('B').clicks().actions(ClickKind.LEFT));
    }
}
