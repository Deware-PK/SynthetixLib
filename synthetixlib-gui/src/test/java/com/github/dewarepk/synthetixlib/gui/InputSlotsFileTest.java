package com.github.dewarepk.synthetixlib.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.config.codec.Problem;
import com.github.dewarepk.synthetixlib.core.item.Items;
import com.github.dewarepk.synthetixlib.core.text.TextService;
import com.github.dewarepk.synthetixlib.gui.file.MenuFile;
import com.github.dewarepk.synthetixlib.gui.file.MenuItem;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** Input slots in a menu file: what the parser accepts, what the checker says, which slots the renderer leaves alone. */
class InputSlotsFileTest {

    private static final TextService TEXT = TextService.create();

    private static final String MENU = """
            title: 'Sell'
            rows: 2
            layout:
              - "#SS#P####"
              - "#   #####"
            items:
              '#': { material: stone }
              'P': { material: paper, name: '<gold>Price' }
              'S':
                input: true
                accept: [diamond, '#minecraft:swords']
                max-amount: 8
            """;

    private final MenuParser parser = new MenuParser(GuiFixtures.codecs());

    private MenuParser.Parsed parse(final String yaml) {
        return parser.parse(yaml);
    }

    private static boolean mentions(final List<Problem> problems, final String path, final String fragment) {
        return problems.stream().anyMatch(p -> p.path().equals(path) && p.message().contains(fragment));
    }

    // ------------------------------------------------------------------------------------------ parser

    @Test
    void anInputItemNeedsNoMaterialAndKeepsItsRules() {
        final MenuParser.Parsed parsed = parse(MENU);

        assertEquals(List.of(), parsed.errors());
        final MenuItem input = parsed.file().item('S');
        assertTrue(input.isInput());
        assertEquals(List.of("diamond", "#minecraft:swords"), input.accept());
        assertEquals(8, input.maxAmount().orElseThrow());
        assertFalse(parsed.file().item('#').isInput());
    }

    @Test
    void anItemThatIsNotAnInputSlotCannotHaveAcceptOrMaxAmount() {
        final MenuParser.Parsed parsed = parse(MENU.replace("input: true", "input: false"));

        assertTrue(mentions(parsed.errors(), "items.S", "accept and max-amount belong to an item with input: true"),
                parsed.errors().toString());
    }

    @Test
    void anInputItemCannotAlsoBeAListOrAState() {
        final MenuParser.Parsed parsed = parse(MENU.replace("input: true", "input: true\n    list: lots"));

        assertTrue(mentions(parsed.errors(), "items.S", "input cannot be combined with list or state"),
                parsed.errors().toString());
    }

    @Test
    void anInputSlotShowsNothingOfItsOwnSoThoseKeysAreReported() {
        final MenuParser.Parsed parsed = parse(MENU.replace("input: true",
                "input: true\n    material: chest\n    name: 'Put it here'\n    click: [close]"));

        assertTrue(mentions(parsed.errors(), "items.S", "these keys have no effect: material, name, click"),
                parsed.errors().toString());
        assertNotNull(parsed.file().item('S'), "the item is kept");
    }

    @Test
    void anAcceptEntryThatIsNeitherAnItemNorATagIsReportedWithItsPath() {
        final MenuParser.Parsed parsed = parse(MENU.replace("[diamond, '#minecraft:swords']", "[diamnod, '#Bad Tag']"));

        assertTrue(mentions(parsed.errors(), "items.S.accept", "'diamnod' is not an item"), parsed.errors().toString());
        assertTrue(mentions(parsed.errors(), "items.S.accept", "'#Bad Tag' is not a tag"), parsed.errors().toString());
    }

    @Test
    void anInputItemInsideAStateIsNotAllowed() {
        final MenuParser.Parsed parsed = parse("""
                rows: 1
                layout: ["T        "]
                items:
                  'T':
                    state: mode
                    states:
                      'on': { input: true }
                      'off': { material: stone }
                """);

        assertTrue(mentions(parsed.errors(), "items.T.states.true", "input is not allowed here")
                || mentions(parsed.errors(), "items.T.states.on", "input is not allowed here"), parsed.errors().toString());
    }

    // ------------------------------------------------------------------------------------------ checker

    @Test
    void aHandlerForACharacterThatIsNotAnInputSlotIsWarnedAbout() {
        final MenuFile file = parse(MENU).file();
        final BiConsumer<MenuView<Void>, List<ItemStack>> handler = (view, items) -> { };
        final Registrations<Void> registered = new Registrations<>(Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of('S', handler, 'P', handler, 'Z', handler));

        final List<String> warnings = MenuChecker.check(file, registered, TEXT).warnings().stream()
                .map(Problem::toString).toList();

        assertTrue(warnings.stream().anyMatch(w -> w.startsWith("items.P: onInputChange('P')")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.startsWith("items.Z: onInputChange('Z')")), warnings.toString());
        assertFalse(warnings.stream().anyMatch(w -> w.startsWith("items.S:")), warnings.toString());
    }

    // ------------------------------------------------------------------------------------------ slots

    @Test
    void theInputSlotsOfAFileAreExactlyTheSlotsOfItsInputCharacters() {
        final MenuFile file = parse(MENU).file();

        assertEquals(Set.of('S'), SlotMap.inputChars(file, Set.of()));
        assertEquals(Set.of(), SlotMap.inputChars(file, Set.of('S')), "an inert item is a locked slot");
        final SlotMap<ItemStack> slots = new BukkitStacks().slotMap(file, Set.of());
        assertArrayEquals(new int[] {1, 2}, slots.inputSlots());
        assertArrayEquals(new int[] {1, 2}, slots.slotsOf('S'));
        assertTrue(slots.hasInput());
        assertTrue(slots.hasLocked(), "stone, the paper and the layout spaces are locked");
        assertFalse(slots.isInput(0));
        assertFalse(slots.isInput(4));
        assertFalse(slots.isInput(10), "a layout space is locked");
    }

    @Test
    void aMenuWithoutInputItemsHasNoInputSlots() {
        final SlotMap<ItemStack> slots = new BukkitStacks().slotMap(
                parse(MENU.replace("input: true", "input: false").replace("accept: [diamond, '#minecraft:swords']", "")
                        .replace("max-amount: 8", "")).file(), Set.of());

        assertFalse(slots.hasInput());
        assertEquals(18, slots.size());
    }

    @Test
    void theRendererLeavesInputSlotsAlone() {
        final MenuFile file = parse(MENU).file();
        final Renderer<Void> renderer = new Renderer<>(TEXT, Items.create(TEXT), "sell",
                new MenuLoader.Resolved(file, Set.of()), GuiFixtures.nothing(), new DetachedView<>(null, 0), "Steve",
                message -> { });

        assertTrue(renderer.isInputSlot(1));
        assertTrue(renderer.isInputSlot(2));
        assertFalse(renderer.isInputSlot(0));
        assertFalse(renderer.isInputSlot(4));
        assertFalse(renderer.isInputSlot(-1));
        assertFalse(renderer.isInputSlot(99));
        assertFalse(renderer.dynamicChars().contains('S'), "an input slot is never redrawn on an update");
    }
}
