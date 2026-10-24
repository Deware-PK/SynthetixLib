package com.github.dewarepk.synthetixlib.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.config.codec.Problem;
import com.github.dewarepk.synthetixlib.dialog.file.ClickAction;
import com.github.dewarepk.synthetixlib.dialog.file.DialogBody;
import com.github.dewarepk.synthetixlib.dialog.file.DialogButton;
import com.github.dewarepk.synthetixlib.dialog.file.DialogFile;
import com.github.dewarepk.synthetixlib.dialog.file.DialogInput;
import com.github.dewarepk.synthetixlib.dialog.file.DialogKind;
import com.github.dewarepk.synthetixlib.dialog.file.ItemSpec;
import java.util.List;
import java.util.Optional;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** Every key of a dialog file, the rules of each type and the range checks, from text to model. */
class DialogParserTest {

    private final DialogParser parser = new DialogParser(DialogFixtures.codecs());

    private static final String SELL = """
            config-version: 1
            title: '<gold>Sell <item-name>'
            external-title: '<gold>Sell'
            type: confirmation
            can-close-with-escape: false
            pause: true
            body:
              - message: '<gray>Starting price. Fee: <fee>%'
                width: 250
              - item: { slot: hand }
                description: '<gray>The item you are selling'
                show-decorations: false
                show-tooltip: false
                width: 32
                height: 24
              - item: { placeholder: lot-item }
              - item: { material: diamond, amount: 3, name: '<aqua>Prize', lore: ['<gray>One'], glint: true }
            inputs:
              price:
                type: number
                label: 'Price'
                width: 200
                min: 1
                max: 1000000
                step: 1
                initial: 100
                format: '%s: %s coins'
              duration:
                type: option
                label: 'Duration'
                width: 100
                options:
                  1h:  { display: '1 hour' }
                  12h: { display: '12 hours', initial: true }
                  24h: { display: '24 hours' }
              note:
                type: text
                label: 'Note'
                width: 300
                initial: 'hi'
                max-length: 64
                multiline: { max-lines: 3, height: 48 }
              anonymous:
                type: bool
                label: 'Hide my name'
                initial: true
            buttons:
              yes: { label: '<green>List it', tooltip: '<gray>Puts the lot on sale', width: 150, click: [action:sell] }
              no:  { label: '<red>Cancel', click: [close] }
            errors:
              price: '<red>Price must be between <min> and <max>'
            """;

    private DialogParser.Parsed parse(final String text) {
        return parser.parse(text);
    }

    private static List<String> paths(final List<Problem> problems) {
        return problems.stream().map(Problem::path).toList();
    }

    @Test
    void everyKeyOfTheDesignFileIsRead() {
        final DialogParser.Parsed parsed = parse(SELL);
        assertEquals(List.of(), parsed.errors());
        final DialogFile file = parsed.file();
        assertNotNull(file);
        assertEquals("<gold>Sell <item-name>", file.title());
        assertEquals(Optional.of("<gold>Sell"), file.externalTitle());
        assertEquals(DialogKind.CONFIRMATION, file.kind());
        assertFalse(file.canCloseWithEscape());
        assertTrue(file.pause());

        assertEquals(4, file.body().size());
        assertEquals(new DialogBody.Message("<gray>Starting price. Fee: <fee>%", Optional.of(250)), file.body().get(0));
        final DialogBody.Item hand = (DialogBody.Item) file.body().get(1);
        assertEquals(Optional.of(ItemSpec.Hand.HAND), hand.item().slot());
        assertEquals(Optional.of("<gray>The item you are selling"), hand.description());
        assertFalse(hand.showDecorations());
        assertFalse(hand.showTooltip());
        assertEquals(Optional.of(32), hand.width());
        assertEquals(Optional.of(24), hand.height());
        final DialogBody.Item placeholder = (DialogBody.Item) file.body().get(2);
        assertEquals(Optional.of("lot-item"), placeholder.item().placeholder());
        assertTrue(placeholder.showDecorations() && placeholder.showTooltip());
        final DialogBody.Item configured = (DialogBody.Item) file.body().get(3);
        assertEquals(Optional.of(Material.DIAMOND), configured.item().material());
        assertEquals(Optional.of(3), configured.item().amount());
        assertEquals(List.of("<gray>One"), configured.item().lore());
        assertEquals(Optional.of(true), configured.item().glint());

        assertEquals(List.of("price", "duration", "note", "anonymous"), List.copyOf(file.inputs().keySet()));
        final DialogInput.NumberInput price = (DialogInput.NumberInput) file.inputs().get("price");
        assertEquals(1, price.min());
        assertEquals(1000000, price.max());
        assertEquals(Optional.of(1.0), price.step());
        assertEquals(Optional.of(100.0), price.initial());
        assertEquals(Optional.of("%s: %s coins"), price.format());
        assertEquals(Optional.of(200), price.width());
        final DialogInput.OptionInput duration = (DialogInput.OptionInput) file.inputs().get("duration");
        assertEquals(List.of("1h", "12h", "24h"), List.copyOf(duration.options().keySet()));
        assertTrue(duration.options().get("12h").isInitial());
        assertEquals(Optional.of("12 hours"), duration.options().get("12h").display());
        final DialogInput.TextInput note = (DialogInput.TextInput) file.inputs().get("note");
        assertEquals(Optional.of(64), note.maxLength());
        assertEquals(Optional.of("hi"), note.initial());
        assertEquals(Optional.of(3), note.multiline().orElseThrow().maxLines());
        assertEquals(Optional.of(48), note.multiline().orElseThrow().height());
        final DialogInput.BoolInput anonymous = (DialogInput.BoolInput) file.inputs().get("anonymous");
        assertEquals(Optional.of(true), anonymous.initial());

        final DialogButton yes = file.buttons().named().get("yes");
        assertEquals("<green>List it", yes.label());
        assertEquals(Optional.of("<gray>Puts the lot on sale"), yes.tooltip());
        assertEquals(Optional.of(150), yes.width());
        assertEquals(List.of(new ClickAction.Run("sell")), yes.clicks().actions());
        assertEquals(List.of(new ClickAction.Close()), file.buttons().named().get("no").clicks().actions());
        assertEquals("<red>Price must be between <min> and <max>", file.errors().get("price"));
    }

    @Test
    void unquotedYesAndNoAreButtonsToo() {
        final DialogFile file = parse("""
                title: t
                type: confirmation
                buttons:
                  yes: { label: 'Y' }
                  no: { label: 'N' }
                """).file();
        assertEquals("Y", file.buttons().named().get("yes").label());
        assertEquals("N", file.buttons().named().get("no").label());
    }

    // --- the rules of each type --------------------------------------------------------------------------------

    @Test
    void confirmationNeedsYesAndNo() {
        final DialogParser.Parsed parsed = parse("""
                title: t
                type: confirmation
                buttons:
                  yes: { label: 'Y' }
                  maybe: { label: 'M' }
                """);
        assertEquals(List.of("buttons.maybe", "buttons.no"), paths(parsed.errors()));
        final DialogFile file = parsed.file();
        assertEquals(List.of("yes", "no"), List.copyOf(file.buttons().named().keySet()));
        assertEquals("<lang:gui.no>", file.buttons().named().get("no").label());
    }

    @Test
    void noticeHasOneOkAndAFallback() {
        assertEquals(List.of(), parse("title: t\ntype: notice\nbuttons:\n  ok: { label: 'Fine' }\n").errors());
        final DialogParser.Parsed without = parse("title: t\ntype: notice\n");
        assertEquals(List.of(), without.errors());
        assertEquals("<lang:gui.ok>", without.file().buttons().named().get("ok").label());
        assertEquals(List.of("buttons.yes"), paths(parse("title: t\ntype: notice\nbuttons:\n  yes: { label: 'Y' }\n")
                .errors()));
    }

    @Test
    void multiActionKeepsFileOrderAndHasExitAndColumns() {
        final DialogFile file = parse("""
                title: t
                type: multi-action
                buttons:
                  zeta: { label: 'Z' }
                  alpha: { label: 'A' }
                  columns: 3
                  exit: { label: 'Back', click: [back] }
                """).file();
        assertEquals(List.of("zeta", "alpha"), List.copyOf(file.buttons().named().keySet()));
        assertEquals(Optional.of(3), file.buttons().columns());
        assertEquals("Back", file.buttons().exit().orElseThrow().label());
        assertEquals(List.of("buttons.zeta", "buttons.alpha", "buttons.exit"),
                List.copyOf(file.buttons().byPath().keySet()));
        assertEquals(List.of("buttons"), paths(parse("title: t\ntype: multi-action\nbuttons:\n  columns: 2\n").errors()));
    }

    @Test
    void listHasDialogsExitColumnsAndButtonWidth() {
        final DialogParser.Parsed parsed = parse("""
                title: t
                type: list
                buttons:
                  dialogs: [first, second, 'Bad Id']
                  exit: { label: 'Close' }
                  columns: 2
                  button-width: 120
                  ok: { label: 'x' }
                """);
        assertEquals(List.of("buttons.dialogs[2]", "buttons.ok"), paths(parsed.errors()));
        final DialogFile file = parsed.file();
        assertEquals(List.of("first", "second"), file.buttons().dialogs());
        assertEquals(Optional.of(2), file.buttons().columns());
        assertEquals(Optional.of(120), file.buttons().buttonWidth());
        assertTrue(file.buttons().named().isEmpty());
        assertEquals(List.of("buttons.dialogs"), paths(parse("title: t\ntype: list\nbuttons:\n  dialogs: []\n").errors()));
    }

    @Test
    void linksHasExitColumnsAndButtonWidthButNoDialogs() {
        final DialogParser.Parsed parsed = parse("""
                title: t
                type: links
                buttons:
                  exit: { label: 'Close' }
                  columns: 1
                  button-width: 200
                  dialogs: [x]
                """);
        assertEquals(List.of("buttons.dialogs"), paths(parsed.errors()));
        assertEquals(Optional.of(200), parsed.file().buttons().buttonWidth());
        assertEquals(DialogKind.LINKS, parsed.file().kind());
    }

    // --- range checks --------------------------------------------------------------------------------------------

    @Test
    void widthsAreClampedTo1To1024() {
        final DialogParser.Parsed parsed = parse("""
                title: t
                type: notice
                body:
                  - { message: 'm', width: 5000 }
                inputs:
                  a: { type: text, label: 'A', width: 0 }
                buttons:
                  ok: { label: 'OK', width: 99999 }
                """);
        assertEquals(List.of("body[0].width", "inputs.a.width", "buttons.ok.width"), paths(parsed.errors()));
        final DialogFile file = parsed.file();
        assertEquals(Optional.of(1024), ((DialogBody.Message) file.body().get(0)).width());
        assertEquals(Optional.of(1), ((DialogInput.TextInput) file.inputs().get("a")).width());
        assertEquals(Optional.of(1024), file.buttons().named().get("ok").width());
    }

    @Test
    void textLimitsAreChecked() {
        final DialogParser.Parsed parsed = parse("""
                title: t
                type: notice
                inputs:
                  zero: { type: text, label: 'A', max-length: 0 }
                  long: { type: text, label: 'B', max-length: 3, initial: 'abcdef' }
                  default: { type: text, label: 'C', initial: '0123456789012345678901234567890123456789' }
                  lines: { type: text, label: 'D', multiline: { max-lines: 0, height: 0 } }
                """);
        assertEquals(List.of("inputs.zero.max-length", "inputs.long.initial", "inputs.default.initial",
                "inputs.lines.multiline.max-lines", "inputs.lines.multiline.height"), paths(parsed.errors()));
        final var inputs = parsed.file().inputs();
        assertEquals(Optional.of(1), ((DialogInput.TextInput) inputs.get("zero")).maxLength());
        assertEquals(Optional.of("abc"), ((DialogInput.TextInput) inputs.get("long")).initial());
        assertEquals(32, ((DialogInput.TextInput) inputs.get("default")).initial().orElseThrow().length());
        assertEquals(Optional.of(1), ((DialogInput.TextInput) inputs.get("lines")).multiline().orElseThrow()
                .maxLines());
    }

    @Test
    void numberRangeStepAndInitialAreChecked() {
        final DialogParser.Parsed parsed = parse("""
                title: t
                type: notice
                inputs:
                  flat: { type: number, label: 'A', min: 5, max: 5 }
                  backwards: { type: number, label: 'B', min: 9, max: 1 }
                  step: { type: number, label: 'C', min: 0, max: 10, step: 0 }
                  low: { type: number, label: 'D', min: 0, max: 10, initial: -4 }
                  high: { type: number, label: 'E', min: 0, max: 10, initial: 99 }
                  fine: { type: number, label: 'F', min: 0, max: 10, step: 0.5, initial: 10 }
                """);
        assertEquals(List.of("inputs.flat", "inputs.backwards", "inputs.step.step", "inputs.low.initial",
                "inputs.high.initial"), paths(parsed.errors()));
        final var inputs = parsed.file().inputs();
        assertFalse(inputs.containsKey("flat"));
        assertFalse(inputs.containsKey("backwards"));
        assertEquals(Optional.empty(), ((DialogInput.NumberInput) inputs.get("step")).step());
        assertEquals(Optional.of(0.0), ((DialogInput.NumberInput) inputs.get("low")).initial());
        assertEquals(Optional.of(10.0), ((DialogInput.NumberInput) inputs.get("high")).initial());
        assertEquals(Optional.of(10.0), ((DialogInput.NumberInput) inputs.get("fine")).initial());
    }

    @Test
    void optionsNeedOneEntryAndAtMostOneInitial() {
        final DialogParser.Parsed parsed = parse("""
                title: t
                type: notice
                inputs:
                  none: { type: option, label: 'A', options: {} }
                  two:
                    type: option
                    label: 'B'
                    options:
                      a: { initial: true }
                      b: { initial: true }
                      c: { display: 'C' }
                """);
        assertEquals(List.of("inputs.none.options", "inputs.two.options.b.initial"), paths(parsed.errors()));
        final DialogInput.OptionInput two = (DialogInput.OptionInput) parsed.file().inputs().get("two");
        assertTrue(two.options().get("a").isInitial());
        assertFalse(two.options().get("b").isInitial());
        assertFalse(parsed.file().inputs().containsKey("none"));
    }

    @Test
    void columnsBelowOneAreClamped() {
        final DialogParser.Parsed parsed = parse("""
                title: t
                type: multi-action
                buttons:
                  a: { label: 'A' }
                  columns: 0
                """);
        assertEquals(List.of("buttons.columns"), paths(parsed.errors()));
        assertEquals(Optional.of(1), parsed.file().buttons().columns());
    }

    // --- other mistakes ------------------------------------------------------------------------------------------

    @Test
    void badInputsAndBodyEntriesAreDroppedWithTheirPath() {
        final DialogParser.Parsed parsed = parse("""
                title: t
                type: notice
                body:
                  - { message: 'a', item: { slot: hand } }
                  - { width: 10 }
                  - { item: { slot: hand, placeholder: x } }
                  - { item: { amount: 2 } }
                  - { message: 'ok', description: 'x' }
                inputs:
                  Bad_Key: { type: text, label: 'A' }
                  untyped: { label: 'B' }
                  unlabelled: { type: bool }
                  good: { type: bool, label: 'G', colour: red }
                errors:
                  nothing: '<red>x'
                """);
        assertEquals(1, parsed.file().body().size());
        assertEquals(List.of("good"), List.copyOf(parsed.file().inputs().keySet()));
        final List<String> paths = paths(parsed.errors());
        for (final String expected : List.of("body[0]", "body[1]", "body[2].item", "body[3].item", "body[4]",
                "inputs.Bad_Key", "inputs.untyped", "inputs.unlabelled", "inputs.good.colour", "errors.nothing")) {
            assertTrue(paths.contains(expected), expected + " not in " + paths);
        }
    }

    @Test
    void topLevelMistakesAreReportedAndSafeDefaultsUsed() {
        final DialogParser.Parsed parsed = parse("""
                type: wobbly
                colour: red
                """);
        final DialogFile file = parsed.file();
        assertEquals(DialogKind.NOTICE, file.kind());
        assertEquals("", file.title());
        assertTrue(file.canCloseWithEscape());
        final List<String> paths = paths(parsed.errors());
        assertTrue(paths.containsAll(List.of("title", "type", "colour")), paths.toString());
    }

    @Test
    void textThatIsNotYamlGivesNoDialog() {
        final DialogParser.Parsed parsed = parse("title: [unclosed");
        assertNull(parsed.file());
        assertEquals(1, parsed.errors().size());
    }

    @Test
    void badClickActionsLeaveTheButtonInertAndAreReported() {
        final DialogParser.Parsed parsed = parse("""
                title: t
                type: confirmation
                buttons:
                  yes: { label: 'Y', click: [close, 'url:ftp://x.y', 'action:sell'] }
                  no: { label: 'N', click: fly }
                """);
        assertEquals(List.of("buttons.yes.click[1]", "buttons.no.click"), paths(parsed.errors()));
        assertTrue(parsed.file().buttons().named().get("yes").clicks().isEmpty());
        assertTrue(parsed.file().buttons().named().get("no").clicks().isEmpty());
    }

    @Test
    void anUnknownKeyInAButtonIsAnError() {
        final DialogParser.Parsed parsed = parse("""
                title: t
                type: notice
                buttons:
                  ok: { label: 'Y', colour: red }
                """);
        assertEquals(List.of("buttons.ok.colour"), paths(parsed.errors()));
        assertEquals("Y", parsed.file().buttons().named().get("ok").label());
    }
}
