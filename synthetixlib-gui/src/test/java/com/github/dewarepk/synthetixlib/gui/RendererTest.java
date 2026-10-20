package com.github.dewarepk.synthetixlib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.core.item.Items;
import com.github.dewarepk.synthetixlib.core.text.TextService;
import com.github.dewarepk.synthetixlib.gui.file.MenuFile;
import com.github.dewarepk.synthetixlib.gui.file.MenuItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

/** Rendering that needs no server: texts, placeholders, states, hide-when, lists and pages. */
class RendererTest {

    private static final TextService TEXT = TextService.create();

    private static final String MENU = """
            title: '<white>Lots <gray>(<page>/<pages>) <player>'
            rows: 2
            layout:
              - "LLL<>S   "
              - "#       B"
            items:
              'L':
                list: lots
                empty: { material: barrier, name: '<red>none' }
              '<': { material: arrow, hide-when: first-page, click: [page-previous] }
              '>': { material: arrow, hide-when: last-page, click: [page-next] }
              'S':
                state: mode
                states:
                  'on': { material: lime_dye, click: [close] }
                  'off': { material: gray_dye }
              '#': { material: stone }
              'B': { material: gold_ingot, name: '<gold>Balance <balance>', lore: ['<gray>for <player>'] }
            """;

    private static MenuFile menu(final String yaml) {
        final MenuFile file = new MenuParser(GuiFixtures.codecs()).parse(yaml).file();
        assertNotNull(file);
        return file;
    }

    private static Registrations<Void> registrations(final List<String> lots, final Component balance) {
        return new Registrations<>(
                Map.of("balance", view -> balance),
                Map.of("lots", new Registrations.ListBinding<Void>(view -> lots, (entry, view) -> null, null)),
                Map.of("mode", view -> "on"),
                Map.of());
    }

    private static Renderer<Void> renderer(final MenuFile file, final Registrations<Void> registered,
                                           final String player) {
        return new Renderer<>(TEXT, Items.create(TEXT), "lots", new MenuLoader.Resolved(file, Set.of()), registered,
                new DetachedView<>(null, 0), player, message -> { });
    }

    private static List<Component> flatten(final Component component) {
        final List<Component> all = new ArrayList<>();
        all.add(component);
        component.children().forEach(child -> all.addAll(flatten(child)));
        return all;
    }

    // ------------------------------------------------------------------------------------------ text

    @Test
    void aPlaceholderValueIsInsertedAndNeverParsedAgain() {
        final Renderer<Void> renderer = renderer(menu(MENU),
                registrations(List.of(), Component.text("<red>12</red> <bold>coins")), "Steve");

        final Component parsed = renderer.pass(0, 1).parse("<gold>Balance: <balance> for <player>");

        assertEquals("Balance: <red>12</red> <bold>coins for Steve", TEXT.plain(parsed));
        // the markup in the value is text: nothing in the result is red or bold, only what the file wrote (gold)
        assertTrue(flatten(parsed).stream().noneMatch(part -> NamedTextColor.RED.equals(part.color())), parsed.toString());
        assertTrue(flatten(parsed).stream().noneMatch(part -> part.decoration(TextDecoration.BOLD)
                == TextDecoration.State.TRUE), parsed.toString());
    }

    @Test
    void aPlayerNamedLikeMarkupStaysText() {
        final Renderer<Void> renderer = renderer(menu(MENU), registrations(List.of(), Component.empty()),
                "<rainbow>Evil");

        assertEquals("Hi <rainbow>Evil", TEXT.plain(renderer.pass(0, 1).parse("Hi <player>")));
    }

    @Test
    void pageIsShownFromOneAndPagesAsCounted() {
        final Renderer<Void> renderer = renderer(menu(MENU), registrations(List.of(), Component.empty()), "Steve");

        assertEquals("2/5", TEXT.plain(renderer.pass(1, 5).parse("<page>/<pages>")));
    }

    @Test
    void anUnregisteredTagIsLeftAsText() {
        final Renderer<Void> renderer = renderer(menu(MENU), registrations(List.of(), Component.empty()), "Steve");

        assertEquals("a <ghost> b", TEXT.plain(renderer.pass(0, 1).parse("a <ghost> b")));
    }

    @Test
    void aPlaceholderValueIsAskedOncePerPass() {
        final AtomicInteger calls = new AtomicInteger();
        final Registrations<Void> registered = new Registrations<>(
                Map.of("balance", view -> {
                    calls.incrementAndGet();
                    return Component.text("5");
                }), Map.of(), Map.of(), Map.of());
        final Renderer<Void> renderer = renderer(menu(MENU), registered, "Steve");

        final Renderer<Void>.Pass pass = renderer.pass(0, 1);
        pass.parse("<balance> <balance>");
        pass.parse("<balance>");
        renderer.pass(0, 1).parse("<balance>");

        assertEquals(2, calls.get());
    }

    // ------------------------------------------------------------------------------------------ states, hide-when

    @Test
    void aStateItemShowsTheItemOfTheStateThatCodeAnswers() {
        final MenuItem toggle = menu(MENU).item('S');

        assertEquals("lime_dye", Renderer.effective(toggle, 0, 1, () -> "on").material().orElseThrow().name().toLowerCase());
        assertEquals("gray_dye", Renderer.effective(toggle, 0, 1, () -> "off").material().orElseThrow().name().toLowerCase());
        assertNull(Renderer.effective(toggle, 0, 1, () -> "unknown"));
        assertNull(Renderer.effective(toggle, 0, 1, () -> null));
    }

    @Test
    void aPlainItemIsNotAskedForAState() {
        final MenuItem stone = menu(MENU).item('#');

        assertSame(stone, Renderer.effective(stone, 0, 1, () -> {
            throw new AssertionError("a plain item has no state");
        }));
    }

    @Test
    void hideWhenFirstPageHidesThePreviousButtonOnlyOnPageZero() {
        final MenuItem previous = menu(MENU).item('<');

        assertNull(Renderer.effective(previous, 0, 3, () -> null));
        assertSame(previous, Renderer.effective(previous, 1, 3, () -> null));
        assertSame(previous, Renderer.effective(previous, 2, 3, () -> null));
    }

    @Test
    void hideWhenLastPageHidesTheNextButtonOnlyOnTheLastPage() {
        final MenuItem next = menu(MENU).item('>');

        assertSame(next, Renderer.effective(next, 0, 3, () -> null));
        assertSame(next, Renderer.effective(next, 1, 3, () -> null));
        assertNull(Renderer.effective(next, 2, 3, () -> null));
    }

    @Test
    void withOnePageBothButtonsAreHidden() {
        final MenuFile file = menu(MENU);

        assertNull(Renderer.effective(file.item('<'), 0, 1, () -> null));
        assertNull(Renderer.effective(file.item('>'), 0, 1, () -> null));
    }

    // ------------------------------------------------------------------------------------------ lists and pages

    @Test
    void sevenEntriesInThreeSlotsMakeThreePagesAndTheTitleSaysSo() {
        final List<String> lots = List.of("a", "b", "c", "d", "e", "f", "g");
        final Renderer<Void> renderer = renderer(menu(MENU), registrations(lots, Component.empty()), "Steve");

        final Renderer.Frame first = renderer.frame(0, null, null);

        assertEquals(3, first.pages());
        assertEquals(0, first.page());
        assertEquals("Lots (1/3) Steve", TEXT.plain(first.title()));
        assertEquals("Lots (3/3) Steve", TEXT.plain(renderer.frame(2, null, null).title()));
    }

    @Test
    void aPageOutsideTheListIsClamped() {
        final List<String> lots = List.of("a", "b", "c", "d");
        final Renderer<Void> renderer = renderer(menu(MENU), registrations(lots, Component.empty()), "Steve");

        assertEquals(1, renderer.frame(99, null, null).page());
        assertEquals(0, renderer.frame(-4, null, null).page());
    }

    @Test
    void anEmptyListIsOnePageAndAnExactMultipleHasNoSpareOne() {
        final MenuFile file = menu(MENU);

        assertEquals(1, renderer(file, registrations(List.of(), Component.empty()), "S").frame(0, null, null).pages());
        assertEquals(1, renderer(file, registrations(List.of("a", "b", "c"), Component.empty()), "S")
                .frame(0, null, null).pages());
        assertEquals(2, renderer(file, registrations(List.of("a", "b", "c", "d"), Component.empty()), "S")
                .frame(0, null, null).pages());
    }

    @Test
    void twoViewersOfTheSameMenuKeepTheirOwnPageAndData() {
        final MenuFile file = menu(MENU);
        final Renderer<Void> alex = renderer(file, registrations(List.of("a", "b", "c", "d", "e"), Component.empty()),
                "Alex");
        final Renderer<Void> steve = renderer(file, registrations(List.of("x"), Component.empty()), "Steve");

        final Renderer.Frame alexFrame = alex.frame(1, null, null);
        final Renderer.Frame steveFrame = steve.frame(0, null, null);

        assertEquals(1, alexFrame.page());
        assertEquals(2, alexFrame.pages());
        assertEquals(List.of("a", "b", "c", "d", "e"), alexFrame.lists().get("lots"));
        assertEquals(0, steveFrame.page());
        assertEquals(1, steveFrame.pages());
        assertEquals(List.of("x"), steveFrame.lists().get("lots"));
        assertEquals("Lots (2/2) Alex", TEXT.plain(alexFrame.title()));
        assertEquals("Lots (1/1) Steve", TEXT.plain(steveFrame.title()));
    }

    @Test
    void aSourceIsOnlyAskedAgainForTheListsThatAreReloaded() {
        final AtomicInteger calls = new AtomicInteger();
        final Function<MenuView<Void>, List<?>> source = view -> {
            calls.incrementAndGet();
            return List.of("a", "b", "c", "d");
        };
        final Registrations<Void> registered = new Registrations<>(Map.of(),
                Map.of("lots", new Registrations.ListBinding<Void>(source, (entry, view) -> null, null)),
                Map.of("mode", view -> "on"), Map.of("x", click -> { }));
        final Renderer<Void> renderer = renderer(menu(MENU), registered, "Steve");

        final Renderer.Frame first = renderer.frame(0, null, null);
        assertEquals(1, calls.get());
        final Renderer.Frame moved = renderer.frame(1, first, Set.of());
        assertEquals(1, calls.get());
        assertEquals(1, moved.page());
        renderer.frame(1, moved, Set.of("lots"));
        assertEquals(2, calls.get());
        renderer.frame(1, moved, null);
        assertEquals(3, calls.get());
    }

    @Test
    void aSourceThatThrowsGivesAnEmptyListAndAMessage() {
        final List<String> messages = new ArrayList<>();
        final Registrations<Void> registered = new Registrations<>(Map.of(),
                Map.of("lots", new Registrations.ListBinding<Void>(view -> {
                    throw new IllegalStateException("db down");
                }, (entry, view) -> null, null)),
                Map.of("mode", view -> "on"), Map.of());
        final Renderer<Void> renderer = new Renderer<>(TEXT, Items.create(TEXT), "lots",
                new MenuLoader.Resolved(menu(MENU), Set.of()), registered, new DetachedView<>(null, 0), "Steve",
                messages::add);

        final Renderer.Frame frame = renderer.frame(0, null, null);

        assertEquals(List.of(), frame.lists().get("lots"));
        assertEquals(1, frame.pages());
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("db down"), messages.toString());
    }

    @Test
    void staticItemsAreNotRenderedOnEveryUpdate() {
        final Renderer<Void> renderer = renderer(menu(MENU), registrations(List.of(), Component.empty()), "Steve");

        // lists, buttons with hide-when, the state item and the one with placeholders; not the plain stone
        assertEquals(Set.of('L', '<', '>', 'S', 'B'), renderer.dynamicChars());
        assertEquals("lots", renderer.listIdOf('L'));
        assertNull(renderer.listIdOf('B'));
    }
}
