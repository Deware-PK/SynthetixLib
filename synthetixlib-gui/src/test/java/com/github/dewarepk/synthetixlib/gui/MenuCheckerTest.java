package com.github.dewarepk.synthetixlib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.config.codec.Problem;
import com.github.dewarepk.synthetixlib.core.text.TextService;
import com.github.dewarepk.synthetixlib.gui.file.MenuFile;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.junit.jupiter.api.Test;

class MenuCheckerTest {

    private static final String MENU = """
            title: '<white>Auction <gray>(<page>/<pages>) <owner>'
            rows: 1
            layout: ["LTAPN      "]
            items:
              'L': { list: lots, empty: { material: air, name: '<empty-text>' } }
              'T':
                state: notify
                states:
                  on: { material: lime_dye, click: ['action:toggle'] }
                  off: { material: gray_dye }
              'A': { material: stone, click: [close, 'action:buy', 'action:buy'] }
              'P': { material: stone, name: '<gold>Balance: <balance> <player>', lore: ['<gray>Page <page> of <pages>', '<!italic><red>x</red>'] }
              'N': { material: stone, click: ['message:<green>Hello <player> <nothing>'] }
            """;

    private static final TextService TEXT = TextService.create();

    private MenuFile menu(final String yaml) {
        return new MenuParser(GuiFixtures.codecs()).parse(yaml).file();
    }

    private static List<String> texts(final List<Problem> problems) {
        return problems.stream().map(Problem::toString).toList();
    }

    @Test
    void everythingRegisteredLeavesNothingInertAndNothingReported() {
        final MenuChecker.Checked checked = MenuChecker.check(menu(MENU), GuiFixtures.registered(
                List.of("owner", "empty-text", "balance", "nothing"), List.of("lots"), List.of("notify"),
                List.of("toggle", "buy")), TEXT);

        assertEquals(Set.of(), checked.inert());
        assertEquals(List.of(), checked.warnings());
    }

    @Test
    void anUnregisteredActionListStateOrPlaceholderMakesItsItemInertAndNamesTheCharacter() {
        final MenuChecker.Checked checked = MenuChecker.check(menu(MENU), GuiFixtures.nothing(), TEXT);

        assertEquals(Set.of('L', 'T', 'A'), checked.inert());
        final List<String> warnings = texts(checked.warnings());
        assertTrue(warnings.contains("items.L: list 'lots' is not registered in code, the item is inert"), warnings.toString());
        assertTrue(warnings.contains("items.L: placeholder <empty-text> is not registered in code, it is shown as text"),
                warnings.toString());
        assertTrue(warnings.contains("items.T: state 'notify' is not registered in code, the item is inert"),
                warnings.toString());
        assertTrue(warnings.contains("items.T: action 'toggle' is not registered in code, the item is inert"),
                warnings.toString());
        assertTrue(warnings.contains("items.A: action 'buy' is not registered in code, the item is inert"),
                warnings.toString());
        assertTrue(warnings.contains("items.P: placeholder <balance> is not registered in code, it is shown as text"),
                warnings.toString());
        assertTrue(warnings.contains("items.N: placeholder <nothing> is not registered in code, it is shown as text"),
                warnings.toString());
    }

    @Test
    void theSameProblemTwiceInOneItemIsReportedOnce() {
        final MenuChecker.Checked checked = MenuChecker.check(menu(MENU), GuiFixtures.nothing(), TEXT);

        assertEquals(1, texts(checked.warnings()).stream().filter(w -> w.startsWith("items.A: ")).count());
    }

    @Test
    void oneMissingRegistrationDoesNotTouchTheOtherItems() {
        final MenuChecker.Checked checked = MenuChecker.check(menu(MENU), GuiFixtures.registered(
                List.of("owner", "empty-text", "balance", "nothing"), List.of("lots"), List.of("notify"),
                List.of("toggle")), TEXT);

        assertEquals(Set.of('A'), checked.inert());
    }

    @Test
    void aTitlePlaceholderOnlyWarns() {
        final MenuChecker.Checked checked = MenuChecker.check(menu(MENU), GuiFixtures.registered(
                List.of("empty-text", "balance", "nothing"), List.of("lots"), List.of("notify"),
                List.of("toggle", "buy")), TEXT);

        assertEquals(Set.of(), checked.inert());
        assertEquals(List.of("title: placeholder <owner> is not registered in code, it is shown as text"),
                texts(checked.warnings()));
    }

    @Test
    void miniMessageTagsAndBuiltInsAreNotPlaceholders() {
        final MenuFile file = menu("""
                title: '<gradient:red:blue>Hi</gradient> <rainbow>x</rainbow> <#ff0000>y <lang:menus.title> <bold><i>z'
                rows: 1
                layout: ["A        "]
                items:
                  'A': { material: stone, name: '<dark_gray><reset><yellow>Hi <player> <page>/<pages><newline><br>' }
                """);

        final MenuChecker.Checked checked = MenuChecker.check(file, GuiFixtures.nothing(), TEXT);

        assertEquals(List.of(), checked.warnings());
        assertEquals(Set.of(), checked.inert());
    }

    @Test
    void placeholderNamesAreComparedIgnoringCase() {
        final MenuFile file = menu("""
                rows: 1
                layout: ["A        "]
                items:
                  'A': { material: stone, name: '<Balance>' }
                """);

        assertEquals(Set.of(), MenuChecker.check(file, GuiFixtures.registered(
                List.of("balance"), List.of(), List.of(), List.of()), TEXT).inert());
    }

    @Test
    void anUnregisteredPlaceholderKeepsTheClicksOfItsItem() {
        final MenuFile file = menu("""
                rows: 1
                layout: ["A        "]
                items:
                  'A': { material: stone, name: '<ghost>', click: [close, 'action:buy'] }
                """);

        final MenuChecker.Checked checked = MenuChecker.check(file, GuiFixtures.registered(
                List.of(), List.of(), List.of(), List.of("buy")), TEXT);

        assertEquals(Set.of(), checked.inert());
        assertEquals(List.of("items.A: placeholder <ghost> is not registered in code, it is shown as text"),
                texts(checked.warnings()));
    }

    @Test
    void aMissingActionStillMakesTheItemInertEvenWithAPlaceholderWarning() {
        final MenuFile file = menu("""
                rows: 1
                layout: ["A        "]
                items:
                  'A': { material: stone, name: '<ghost>', click: ['action:buy'] }
                """);

        final MenuChecker.Checked checked = MenuChecker.check(file, GuiFixtures.nothing(), TEXT);

        assertEquals(Set.of('A'), checked.inert());
        assertEquals(2, checked.warnings().size());
    }

    @Test
    void aTagTheTextServiceWasDerivedWithIsNotAPlaceholder() {
        final MenuFile file = menu("""
                rows: 1
                layout: ["A        "]
                items:
                  'A': { material: stone, name: '<prefix> hi' }
                """);
        final TextService withPrefix = TEXT.with(Placeholder.unparsed("prefix", "[x]"));

        assertEquals(List.of(), MenuChecker.check(file, GuiFixtures.nothing(), withPrefix).warnings());
        assertEquals(List.of("prefix"), MenuChecker.placeholderNames("<prefix> hi", TEXT));
    }
}
