package com.github.dewarepk.synthetixlib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.config.codec.Problem;
import com.github.dewarepk.synthetixlib.gui.file.ClickAction;
import com.github.dewarepk.synthetixlib.gui.file.ClickKind;
import com.github.dewarepk.synthetixlib.gui.file.HideWhen;
import com.github.dewarepk.synthetixlib.gui.file.MenuFile;
import com.github.dewarepk.synthetixlib.gui.file.MenuItem;
import com.github.dewarepk.synthetixlib.gui.file.MenuKind;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

class MenuParserTest {

    private final MenuParser parser = new MenuParser(GuiFixtures.codecs());

    private static final String FULL = """
            config-version: 1
            title: ':offset_-8::auction_bg:<white>Auction <gray>(<page>/<pages>)'
            type: chest
            rows: 6
            update-every: 20t
            layout:
              - "#########"
              - "#LLLLLLL#"
              - "#LLLLLLL#"
              - "#LLLLLLL#"
              - "#########"
              - "<##BIT##>"
            items:
              '#':
                material: black_stained_glass_pane
                item-model: 'mypack:invisible'
                hide-tooltip: true
              'L':
                list: lots
                empty: { material: air }
                loading: { material: clock, name: '<gray>Loading' }
              '<':
                material: arrow
                name: '<yellow>Previous page'
                hide-when: first-page
                click: [page-previous]
                sound: ui.button.click
              '>':
                material: arrow
                amount: 2
                lore: ['<gray>one', '<gray>two']
                glint: true
                tooltip-style: 'mypack:fancy'
                hide-when: last-page
                click: [page-next]
              'B':
                material: gold_ingot
                name: '<gold>Balance: <balance>'
              'T':
                state: notify
                states:
                  on:  { material: lime_dye, name: '<green>ON',  click: ['action:toggle-notify'] }
                  off: { material: gray_dye, name: '<gray>OFF', click: ['action:toggle-notify'] }
              'I':
                material: chest
                click:
                  left:  ['open:my-listings']
                  right: [close, 'command:auction sell']
            """;

    private MenuParser.Parsed parse(final String yaml) {
        return parser.parse(yaml);
    }

    private static List<String> texts(final List<Problem> problems) {
        return problems.stream().map(Problem::toString).toList();
    }

    private static boolean mentions(final List<Problem> problems, final String path, final String fragment) {
        return problems.stream().anyMatch(p -> p.path().equals(path) && p.message().contains(fragment));
    }

    // --- every key of the design -------------------------------------------------------------------------------

    @Test
    void everyKeyOfTheDesignExampleIsParsed() {
        final MenuParser.Parsed parsed = parse(FULL);

        assertTrue(parsed.errors().isEmpty(), texts(parsed.errors()).toString());
        assertTrue(parsed.warnings().isEmpty(), texts(parsed.warnings()).toString());
        final MenuFile file = parsed.file();
        assertNotNull(file);
        assertEquals(":offset_-8::auction_bg:<white>Auction <gray>(<page>/<pages>)", file.title());
        assertEquals(MenuKind.CHEST, file.type());
        assertEquals(6, file.rows());
        assertEquals(20, file.updateTicks());
        assertEquals(6, file.layout().size());
        assertEquals("<##BIT##>", file.layout().get(5));
        assertEquals(54, file.size());
        assertEquals(7, file.items().size());

        final MenuItem background = file.item('#');
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, background.material().orElseThrow());
        assertEquals(new NamespacedKey("mypack", "invisible"), background.itemModel().orElseThrow());
        assertTrue(background.hideTooltip().orElseThrow());

        final MenuItem lots = file.item('L');
        assertEquals("lots", lots.list().orElseThrow());
        assertEquals(Material.AIR, lots.empty().orElseThrow().material().orElseThrow());
        assertEquals("<gray>Loading", lots.loading().orElseThrow().name().orElseThrow());

        final MenuItem previous = file.item('<');
        assertEquals(HideWhen.FIRST_PAGE, previous.hideWhen().orElseThrow());
        assertEquals(new NamespacedKey("minecraft", "ui.button.click"), previous.sound().orElseThrow());
        assertEquals(List.of(new ClickAction.PagePrevious()), previous.clicks().actions(ClickKind.LEFT));

        final MenuItem next = file.item('>');
        assertEquals(2, next.amount().orElseThrow());
        assertEquals(List.of("<gray>one", "<gray>two"), next.lore());
        assertTrue(next.glint().orElseThrow());
        assertEquals(new NamespacedKey("mypack", "fancy"), next.tooltipStyle().orElseThrow());
        assertEquals(HideWhen.LAST_PAGE, next.hideWhen().orElseThrow());

        final MenuItem toggle = file.item('T');
        assertEquals("notify", toggle.state().orElseThrow());
        assertEquals(2, toggle.states().size());
        assertEquals(List.of(new ClickAction.Run("toggle-notify")),
                toggle.stateItem("on").clicks().actions(ClickKind.RIGHT));
        assertEquals("<gray>OFF", toggle.stateItem("off").name().orElseThrow());
        assertNull(toggle.stateItem("maybe"));
    }

    @Test
    void stateNamesThatYamlReadsAsBooleansAreFoundUnderTheirSpelling() {
        final MenuFile file = parse("""
                rows: 1
                layout: ["QW       "]
                items:
                  'Q':
                    state: a
                    states:
                      'on': { material: lime_dye }
                      'off': { material: gray_dye }
                  'W':
                    state: b
                    states:
                      yes: { material: lime_dye }
                      other: { material: gray_dye }
                """).file();

        assertEquals(Material.LIME_DYE, file.item('Q').stateItem("on").material().orElseThrow());
        assertEquals(Material.GRAY_DYE, file.item('Q').stateItem("off").material().orElseThrow());
        assertNull(file.item('Q').stateItem("true"), "an exact name that is not there is not guessed when quoted");
        assertEquals(Material.LIME_DYE, file.item('W').stateItem("yes").material().orElseThrow());
        assertEquals(Material.LIME_DYE, file.item('W').stateItem("on").material().orElseThrow());
        assertEquals(Material.GRAY_DYE, file.item('W').stateItem("other").material().orElseThrow());
    }

    @Test
    void slotsFollowTheLayoutLeftToRightTopToBottom() {
        final MenuFile file = parse(FULL).file();

        assertArrayEquals(new int[] {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34},
                file.slots('L'));
        assertArrayEquals(new int[] {45}, file.slots('<'));
        assertEquals('B', file.charAt(48));
        assertEquals('#', file.charAt(46));
    }

    private static void assertArrayEquals(final int[] expected, final int[] actual) {
        org.junit.jupiter.api.Assertions.assertArrayEquals(expected, actual);
    }

    // --- click: list or map ------------------------------------------------------------------------------------

    @Test
    void aPlainListAnswersAnyClickAndAMapOnlyTheListedTypes() {
        final MenuFile file = parse(FULL).file();

        final MenuItem any = file.item('<');
        for (final ClickKind kind : ClickKind.values()) {
            assertEquals(List.of(new ClickAction.PagePrevious()), any.clicks().actions(kind));
        }

        final MenuItem perType = file.item('I');
        assertEquals(List.of(new ClickAction.Open("my-listings")), perType.clicks().actions(ClickKind.LEFT));
        assertEquals(List.of(new ClickAction.Close(), new ClickAction.Command("auction sell")),
                perType.clicks().actions(ClickKind.RIGHT));
        assertTrue(perType.clicks().actions(ClickKind.MIDDLE).isEmpty());
        assertTrue(perType.clicks().actions(ClickKind.SHIFT_LEFT).isEmpty());
    }

    @Test
    void everyClickTypeNameIsAccepted() {
        final MenuParser.Parsed parsed = parse("""
                layout: ["#        "]
                items:
                  '#':
                    material: stone
                    click:
                      left: [close]
                      right: [close]
                      shift-left: [close]
                      shift-right: [close]
                      middle: [close]
                      drop: [close]
                """);

        assertTrue(parsed.errors().isEmpty(), texts(parsed.errors()).toString());
        final MenuItem item = parsed.file().item('#');
        for (final ClickKind kind : ClickKind.values()) {
            assertEquals(List.of(new ClickAction.Close()), item.clicks().actions(kind), kind.toString());
        }
    }

    @Test
    void anUnknownActionIsAnErrorAtItsPathAndLeavesTheItemInert() {
        final MenuParser.Parsed parsed = parse("""
                layout: ["#        "]
                items:
                  '#':
                    material: stone
                    click: [close, teleport:spawn]
                """);

        assertTrue(mentions(parsed.errors(), "items.#.click[1]", "unknown action 'teleport'"),
                texts(parsed.errors()).toString());
        final MenuItem item = parsed.file().item('#');
        assertNotNull(item, "the item stays so its slots still show it");
        assertTrue(item.clicks().isEmpty(), "no half-valid list runs part of its steps");
    }

    @Test
    void anUnknownClickTypeAndANonTextActionAreErrorsToo() {
        final MenuParser.Parsed parsed = parse("""
                layout: ["#        "]
                items:
                  '#':
                    material: stone
                    click:
                      leftt: [close]
                """);
        assertTrue(mentions(parsed.errors(), "items.#.click.leftt", "unknown click type"),
                texts(parsed.errors()).toString());
        assertTrue(parsed.file().item('#').clicks().isEmpty());

        final MenuParser.Parsed nonText = parse("""
                layout: ["#        "]
                items:
                  '#':
                    material: stone
                    click: [[close]]
                """);
        assertTrue(mentions(nonText.errors(), "items.#.click[0]", "expected an action as text"),
                texts(nonText.errors()).toString());
    }

    @Test
    void emptyActionTextIsAnError() {
        final MenuParser.Parsed parsed = parse("""
                layout: ["#        "]
                items:
                  '#':
                    material: stone
                    click: ['']
                """);

        assertTrue(mentions(parsed.errors(), "items.#.click[0]", "the action is empty"),
                texts(parsed.errors()).toString());
    }

    // --- size per type -----------------------------------------------------------------------------------------

    @Test
    void hopperAndDispenserHaveTheirOwnSize() {
        final MenuFile hopper = parse("""
                type: hopper
                layout: ["#B#I#"]
                items:
                  '#': { material: stone }
                  'B': { material: stone }
                  'I': { material: stone }
                """).file();
        assertEquals(MenuKind.HOPPER, hopper.type());
        assertEquals(5, hopper.size());
        assertEquals(1, hopper.rows());

        final MenuParser.Parsed dispenser = parse("""
                type: dispenser
                layout: ["###", "#B#", "###"]
                items:
                  '#': { material: stone }
                  'B': { material: stone }
                """);
        assertTrue(dispenser.errors().isEmpty(), texts(dispenser.errors()).toString());
        assertEquals(9, dispenser.file().size());
        assertEquals(3, dispenser.file().rows());
    }

    @Test
    void aChestTakesItsRowsFromTheLayoutWhenRowsIsMissing() {
        final MenuParser.Parsed parsed = parse("""
                layout: ["#########", "#########"]
                items:
                  '#': { material: stone }
                """);

        assertTrue(parsed.errors().isEmpty(), texts(parsed.errors()).toString());
        assertEquals(2, parsed.file().rows());
    }

    @Test
    void aLayoutWithTooFewOrTooManyLinesIsReportedAndFixed() {
        final MenuParser.Parsed few = parse("""
                rows: 3
                layout: ["#########"]
                items:
                  '#': { material: stone }
                """);
        assertTrue(mentions(few.errors(), "layout", "has 1 lines, 3-row chest needs 3"), texts(few.errors()).toString());
        assertEquals(3, few.file().layout().size());
        assertEquals("         ", few.file().layout().get(2));

        final MenuParser.Parsed many = parse("""
                rows: 1
                layout: ["#########", "#########"]
                items:
                  '#': { material: stone }
                """);
        assertTrue(mentions(many.errors(), "layout", "extra lines are ignored"), texts(many.errors()).toString());
        assertEquals(1, many.file().layout().size());
    }

    @Test
    void aLineOfTheWrongWidthIsCutOrPaddedPerType() {
        final MenuParser.Parsed chest = parse("""
                rows: 1
                layout: ["#####"]
                items:
                  '#': { material: stone }
                """);
        assertTrue(mentions(chest.errors(), "layout[0]", "has 5 characters, 1-row chest needs 9"),
                texts(chest.errors()).toString());
        assertEquals("#####    ", chest.file().layout().get(0));

        final MenuParser.Parsed hopper = parse("""
                type: hopper
                layout: ["#######"]
                items:
                  '#': { material: stone }
                """);
        assertTrue(mentions(hopper.errors(), "layout[0]", "hopper needs 5"), texts(hopper.errors()).toString());
        assertEquals("#####", hopper.file().layout().get(0));

        final MenuParser.Parsed dispenser = parse("""
                type: dispenser
                layout: ["####", "###", "###"]
                items:
                  '#': { material: stone }
                """);
        assertTrue(mentions(dispenser.errors(), "layout[0]", "dispenser needs 3"), texts(dispenser.errors()).toString());
        assertEquals("###", dispenser.file().layout().get(0));
    }

    @Test
    void rowsOutsideTheRangeAreAnErrorAndAHopperIgnoresRows() {
        final MenuParser.Parsed seven = parse("""
                rows: 7
                layout: ["#########"]
                items:
                  '#': { material: stone }
                """);
        assertTrue(mentions(seven.errors(), "rows", "between 1 and 6 (was 7)"), texts(seven.errors()).toString());
        assertEquals(1, seven.file().rows());

        final MenuParser.Parsed hopper = parse("""
                type: hopper
                rows: 4
                layout: ["#####"]
                items:
                  '#': { material: stone }
                """);
        assertTrue(hopper.errors().isEmpty(), texts(hopper.errors()).toString());
        assertTrue(mentions(hopper.warnings(), "rows", "ignored"), texts(hopper.warnings()).toString());
        assertEquals(1, hopper.file().rows());
    }

    @Test
    void anUnknownTypeIsAnErrorAndFallsBackToAChest() {
        final MenuParser.Parsed parsed = parse("""
                type: anvil
                layout: ["#########"]
                items:
                  '#': { material: stone }
                """);

        assertTrue(mentions(parsed.errors(), "type", "unknown value 'anvil'"), texts(parsed.errors()).toString());
        assertEquals(MenuKind.CHEST, parsed.file().type());
    }

    // --- chars -------------------------------------------------------------------------------------------------

    @Test
    void aLayoutCharacterWithoutAnItemIsAnErrorAndItsSlotsStayEmpty() {
        final MenuParser.Parsed parsed = parse("""
                rows: 1
                layout: ["#X#X#    "]
                items:
                  '#': { material: stone }
                """);

        assertTrue(mentions(parsed.errors(), "layout[0]", "character 'X' has no item"),
                texts(parsed.errors()).toString());
        assertEquals(1, parsed.errors().size(), "reported once, not per slot");
        assertNull(parsed.file().item('X'));
    }

    @Test
    void anItemThatIsNotInTheLayoutIsOnlyAWarning() {
        final MenuParser.Parsed parsed = parse("""
                rows: 1
                layout: ["#        "]
                items:
                  '#': { material: stone }
                  'U': { material: stone }
                """);

        assertTrue(parsed.errors().isEmpty(), texts(parsed.errors()).toString());
        assertTrue(mentions(parsed.warnings(), "items.U", "not used in layout"), texts(parsed.warnings()).toString());
    }

    @Test
    void anItemKeyMustBeOneCharacterAndNeverASpace() {
        final MenuParser.Parsed parsed = parse("""
                rows: 1
                layout: ["#        "]
                items:
                  '#': { material: stone }
                  'ab': { material: stone }
                  ' ': { material: stone }
                """);

        assertTrue(mentions(parsed.errors(), "items.ab", "exactly one character"), texts(parsed.errors()).toString());
        assertTrue(mentions(parsed.errors(), "items. ", "space"), texts(parsed.errors()).toString());
        assertEquals(1, parsed.file().items().size());
    }

    @Test
    void anItemThatCannotBeReadIsDroppedAndItsCharacterIsNotReportedAgain() {
        final MenuParser.Parsed parsed = parse("""
                rows: 1
                layout: ["#B       "]
                items:
                  '#': { material: stone }
                  'B': { material: stone, amount: 500 }
                """);

        assertTrue(mentions(parsed.errors(), "items.B.amount", "between 1 and 99"),
                texts(parsed.errors()).toString());
        assertEquals(1, parsed.errors().size(), texts(parsed.errors()).toString());
        assertNull(parsed.file().item('B'));
        assertNotNull(parsed.file().item('#'), "the other items are not affected");
    }

    @Test
    void anUnknownKeyIsAnErrorButTheItemStays() {
        final MenuParser.Parsed parsed = parse("""
                rows: 1
                colour: red
                layout: ["#        "]
                items:
                  '#': { material: stone, lorr: ['x'] }
                """);

        assertTrue(mentions(parsed.errors(), "colour", "unknown key"), texts(parsed.errors()).toString());
        assertTrue(mentions(parsed.errors(), "items.#.lorr", "unknown key"), texts(parsed.errors()).toString());
        assertNotNull(parsed.file().item('#'));
    }

    // --- rules between keys ------------------------------------------------------------------------------------

    @Test
    void anItemWithoutAnythingToShowNeedsAMaterial() {
        final MenuParser.Parsed parsed = parse("""
                rows: 1
                layout: ["#L       "]
                items:
                  '#': { name: 'x' }
                  'L': { list: lots }
                """);

        assertTrue(mentions(parsed.errors(), "items.#", "material is required"), texts(parsed.errors()).toString());
        assertFalse(parsed.errors().stream().anyMatch(p -> p.path().startsWith("items.L")),
                "a list item needs no material");
    }

    @Test
    void stateNeedsStatesAndListAndStateExcludeEachOther() {
        final MenuParser.Parsed parsed = parse("""
                rows: 1
                layout: ["ABC      "]
                items:
                  'A': { state: notify }
                  'B': { material: stone, states: { on: { material: stone } } }
                  'C': { list: lots, state: notify, states: { on: { material: stone } } }
                """);

        assertTrue(mentions(parsed.errors(), "items.A.states", "state needs at least one entry"),
                texts(parsed.errors()).toString());
        assertTrue(mentions(parsed.errors(), "items.B.states", "needs state"), texts(parsed.errors()).toString());
        assertTrue(mentions(parsed.errors(), "items.C", "list and state cannot"), texts(parsed.errors()).toString());
    }

    @Test
    void dynamicKeysAreNotAllowedInsideNestedItems() {
        final MenuParser.Parsed parsed = parse("""
                rows: 1
                layout: ["L        "]
                items:
                  'L':
                    list: lots
                    empty: { material: stone, list: other }
                """);

        assertTrue(mentions(parsed.errors(), "items.L.empty", "not allowed here"), texts(parsed.errors()).toString());
    }

    // --- top level ---------------------------------------------------------------------------------------------

    @Test
    void updateEveryAcceptsTicksAndDurationsAndRejectsNonsense() {
        assertEquals(10, parse("update-every: 500ms\nlayout: ['#']\nitems: {'#': {material: stone}}\n")
                .file().updateTicks());
        assertEquals(0, parse("layout: ['#']\nitems: {'#': {material: stone}}\n").file().updateTicks());

        final MenuParser.Parsed bad = parse("update-every: 20\nlayout: ['#']\nitems: {'#': {material: stone}}\n");
        assertTrue(mentions(bad.errors(), "update-every", "missing unit"), texts(bad.errors()).toString());
        assertEquals(0, bad.file().updateTicks());
    }

    @Test
    void missingLayoutAndItemsAreErrorsButStillGiveAMenu() {
        final MenuParser.Parsed parsed = parse("title: Hi\n");

        assertTrue(mentions(parsed.errors(), "layout", "missing"), texts(parsed.errors()).toString());
        assertTrue(mentions(parsed.errors(), "items", "missing"), texts(parsed.errors()).toString());
        assertEquals("Hi", parsed.file().title());
        assertEquals(3, parsed.file().rows());
    }

    @Test
    void textThatIsNotYamlGivesNoMenu() {
        final MenuParser.Parsed parsed = parse("title: [unclosed\n  layout: {\n");

        assertNull(parsed.file());
        assertTrue(parsed.errors().get(0).message().startsWith("invalid YAML"), texts(parsed.errors()).toString());
    }

    @Test
    void anyOtherDigitOrSymbolCanBeAnItemKey() {
        final MenuParser.Parsed parsed = parse("""
                rows: 1
                layout: ["1=@%-    "]
                items:
                  1: { material: stone }
                  '=': { material: stone }
                  '@': { material: stone }
                  '%': { material: stone }
                  '-': { material: stone }
                """);

        assertTrue(parsed.errors().isEmpty(), texts(parsed.errors()).toString());
        assertEquals(5, parsed.file().items().size());
    }
}
