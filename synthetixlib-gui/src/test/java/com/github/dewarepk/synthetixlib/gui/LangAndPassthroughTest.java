package com.github.dewarepk.synthetixlib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.core.item.Items;
import com.github.dewarepk.synthetixlib.core.text.TextService;
import com.github.dewarepk.synthetixlib.gui.file.MenuFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** {@code <lang:key>} in menu texts, async lists in a frame, and resource-pack strings in the title. */
class LangAndPassthroughTest {

    private static final TextService TEXT = TextService.create();

    private static MenuFile menu(final String title) {
        final String yaml = "title: '" + title.replace("'", "''") + "'\nrows: 1\nlayout:\n  - \"L        \"\nitems:\n"
                + "  'L':\n    list: lots\n    loading: { material: clock }\n";
        final MenuFile file = new MenuParser(GuiFixtures.codecs()).parse(yaml).file();
        assertNotNull(file);
        return file;
    }

    /** Two "language files" the test switches between, like two viewers with different locales. */
    private static final class Languages implements GuiMessages {
        final AtomicReference<String> current = new AtomicReference<>("en");
        final Map<String, Map<String, String>> files = Map.of(
                "en", Map.of("title", "<gold>Auction", "hello", "Hi <player>", "loop", "again <lang:loop>"),
                "th", Map.of("title", "<gold>Bieten", "hello", "Hallo <player>"));

        @Override
        public @Nullable Component get(final org.bukkit.entity.@Nullable Player viewer, final String key,
                                       final TagResolver nested) {
            final String raw = files.get(current.get()).get(key);
            return raw == null ? null : TEXT.parse(raw, nested);
        }
    }

    private static Renderer<Void> renderer(final MenuFile file, final @Nullable GuiMessages messages,
                                           final List<String> warnings, final @Nullable AsyncLoads loads,
                                           final Registrations<Void> registered) {
        return new Renderer<>(TEXT, Items.create(TEXT), "auction", new MenuLoader.Resolved(file, Set.of()), registered,
                new DetachedView<>(null, 0), "Steve", warnings::add, null, () -> messages, loads);
    }

    private static Component titleComponent(final MenuFile file, final @Nullable GuiMessages messages,
                                            final List<String> warnings) {
        return renderer(file, messages, warnings, null, GuiFixtures.registered(List.of(), List.of("lots"),
                List.of(), List.of())).frame(0, null, null).title();
    }

    private static String title(final MenuFile file, final @Nullable GuiMessages messages,
                                final List<String> warnings) {
        return TEXT.plain(titleComponent(file, messages, warnings));
    }

    private static TranslatableComponent translatable(final Component title) {
        return (TranslatableComponent) title;
    }

    // ------------------------------------------------------------------------------------------ <lang:key>

    @Test
    void theLangTagReadsTheLanguageOfTheViewerAndMixesWithInlineText() {
        final Languages languages = new Languages();
        final MenuFile file = menu("<lang:title> <gray>(<page>/<pages>)");
        final List<String> warnings = new ArrayList<>();

        assertEquals("Auction (1/1)", title(file, languages, warnings));
        languages.current.set("th");
        assertEquals("Bieten (1/1)", title(file, languages, warnings));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void aMessageCanUseThePlaceholdersOfTheMenu() {
        assertEquals("Hi Steve", title(menu("<lang:hello>"), new Languages(), new ArrayList<>()));
    }

    @Test
    void aMessageThatReferencesLangItselfDoesNotLoop() {
        final List<String> warnings = new ArrayList<>();

        // inside a message <lang> is not ours (no lookup, no loop): MiniMessage makes its own translatable of it
        assertEquals("again loop", title(menu("<lang:loop>"), new Languages(), warnings));
    }

    @Test
    void aMissingKeyBecomesATranslatableAndAKeyThatLooksNotVanillaWarnsOnce() {
        final List<String> warnings = new ArrayList<>();

        final Renderer<Void> renderer = renderer(menu("<lang:nope:a:b>"), new Languages(), warnings, null,
                GuiFixtures.registered(List.of(), List.of("lots"), List.of(), List.of()));
        final TranslatableComponent key = translatable(renderer.frame(0, null, null).title());
        renderer.frame(0, null, null);

        assertEquals("nope", key.key());
        assertEquals(List.of("a", "b"), key.arguments().stream()
                .map(argument -> TEXT.plain(argument.asComponent())).toList());
        assertEquals(1, warnings.size(), "warned once for the renderer, not once per render: " + warnings);
        assertTrue(warnings.get(0).contains("<lang:nope>"), warnings.get(0));
    }

    @Test
    void aVanillaKeyIsATranslatableAndNeverWarns() {
        final List<String> warnings = new ArrayList<>();

        final TranslatableComponent key = translatable(titleComponent(menu("<lang:item.minecraft.diamond>"),
                new Languages(), warnings));

        assertEquals("item.minecraft.diamond", key.key());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void withoutLanguageFilesTheTagIsLeftToMiniMessageAndNothingIsReported() {
        final List<String> warnings = new ArrayList<>();

        title(menu("<lang:title>"), null, warnings);

        assertTrue(warnings.isEmpty());
    }

    // ------------------------------------------------------------------------------------------ resource packs

    @Test
    void itemsAdderAndNexoStringsReachTheTitleUnchanged() {
        final List<String> warnings = new ArrayList<>();

        assertEquals(":offset_-8::auction_bg:Auction", title(menu(":offset_-8::auction_bg:<white>Auction"), null, warnings));
        assertEquals("<glyph:auction_bg>Auction", title(menu("<glyph:auction_bg><white>Auction"), null, warnings));
        assertEquals("<shift:-8><glyph:bg>x", title(menu("<shift:-8><glyph:bg>x"), null, warnings));
        assertTrue(warnings.isEmpty());
    }

    // ------------------------------------------------------------------------------------------ async frame

    @Test
    void anAsyncListIsLoadingInTheFrameUntilItsAnswerArrives() {
        final List<Runnable> async = new ArrayList<>();
        final List<Runnable> viewer = new ArrayList<>();
        final AsyncLoads loads = new AsyncLoads(async::add, viewer::add, () -> false, message -> { },
                id -> List.of("x", "y"), () -> { });
        final Registrations<Void> registered = new Registrations<>(Map.of(),
                Map.of("lots", new Registrations.ListBinding<Void>(view -> List.of(), (entry, view) -> null, null, true)),
                Map.of(), Map.of());
        final Renderer<Void> renderer = renderer(menu("t"), null, new ArrayList<>(), loads, registered);

        final Renderer.Frame first = renderer.frame(0, null, null);
        assertEquals(Set.of("lots"), first.loading());
        assertEquals(List.of(), first.lists().get("lots"));

        async.forEach(Runnable::run);
        viewer.forEach(Runnable::run);
        final Renderer.Frame second = renderer.frame(0, first, Set.of());
        assertEquals(Set.of(), second.loading());
        assertEquals(List.of("x", "y"), second.lists().get("lots"));
    }
}
