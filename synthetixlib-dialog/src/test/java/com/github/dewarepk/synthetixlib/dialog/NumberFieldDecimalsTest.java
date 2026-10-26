package com.github.dewarepk.synthetixlib.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.dialog.file.DialogInput;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** The {@code decimals} limit of a decimal number-field: file key, code override, server check, error line. */
public class NumberFieldDecimalsTest {

    public record Money(BigDecimal price) {
    }

    private static final String FILE = """
            title: t
            type: notice
            inputs:
              price: { type: number-field, label: 'P', format: decimal, decimals: 2, min: 0, max: 100000 }
              whole: { type: number-field, label: 'W', format: decimal, decimals: 0 }
              free: { type: number-field, label: 'F', format: decimal }
            errors:
              price: '<red>At most <decimals> decimals, not <value>'
            """;

    private final Codecs codecs = DialogFixtures.codecs();
    private final DialogParser.Parsed parsed = new DialogParser(codecs).parse(FILE);
    private final Map<String, DialogInput> inputs = parsed.file().inputs();

    private Object clean(final String key, final Object raw) {
        return FormValidator.clean(key, inputs.get(key), raw, InputLimits.file());
    }

    @Test
    void twoDecimalsAcceptsUpToTwo() {
        assertTrue(parsed.errors().isEmpty(), parsed.errors().toString());
        assertEquals(new BigDecimal("12"), clean("price", "12"));
        assertEquals(new BigDecimal("12.5"), clean("price", "12.5"));
        assertEquals(new BigDecimal("12.50"), clean("price", "12.50"));
        assertEquals(new BigDecimal("12.500"), clean("price", "12.500"), "trailing zeros do not count");
        assertEquals(new BigDecimal("1000.25"), clean("price", "1,000.25"));
        assertNull(clean("price", "12.505"));
        assertNull(clean("price", "0.001"));
    }

    @Test
    void zeroDecimalsIsWholeNumbersAsBigDecimal() {
        assertEquals(new BigDecimal("12"), clean("whole", "12"));
        assertEquals(new BigDecimal("12.0"), clean("whole", "12.0"));
        assertNull(clean("whole", "12.5"));
    }

    @Test
    void noKeyMeansNoLimit() {
        assertEquals(new BigDecimal("1.23456789"), clean("free", "1.23456789"));
    }

    @Test
    void decimalsOnAnIntegerFieldIsALoadErrorAndIgnored() {
        final DialogParser.Parsed bad = new DialogParser(codecs).parse("""
                title: t
                type: notice
                inputs:
                  a: { type: number-field, label: 'A', decimals: 2 }
                  b: { type: number-field, label: 'B', format: decimal, decimals: 11 }
                  c: { type: number-field, label: 'C', format: decimal, decimals: 1, initial: 1.25 }
                """);
        final var a = (DialogInput.NumberFieldInput) bad.file().inputs().get("a");
        assertEquals(Optional.empty(), a.decimals());
        assertTrue(bad.errors().stream().anyMatch(p -> p.path().equals("inputs.a.decimals")
                && p.message().contains("only used with format: decimal")), bad.errors().toString());
        assertEquals(Optional.empty(), ((DialogInput.NumberFieldInput) bad.file().inputs().get("b")).decimals());
        assertTrue(bad.errors().stream().anyMatch(p -> p.path().equals("inputs.b.decimals")));
        final var c = (DialogInput.NumberFieldInput) bad.file().inputs().get("c");
        assertEquals(Optional.of(1.3), c.initial(), "rounded HALF_UP to the limit");
        assertTrue(bad.errors().stream().anyMatch(p -> p.path().equals("inputs.c.initial")));
    }

    @Test
    void codeOverridesTheFileAndIsReadOnSubmit() {
        final int[] places = {3};
        final Map<String, Function<DialogView<Void>, Integer>> decimals = Map.of("price", view -> places[0]);
        final List<String> warned = new ArrayList<>();

        final InputLimits shown = InputLimits.resolve("t", inputs, Map.of(), Map.of(), decimals, null, warned::add);
        assertEquals(3, shown.decimals("price", inputs.get("price")));
        assertEquals(new BigDecimal("1.234"), FormValidator.clean("price", inputs.get("price"), "1.234", shown));
        assertNull(FormValidator.clean("price", inputs.get("price"), "1.2345", shown));

        places[0] = 1;   // changed between show and submit: the next showing asks again
        final InputLimits next = InputLimits.resolve("t", inputs, Map.of(), Map.of(), decimals, null, warned::add);
        assertNull(FormValidator.clean("price", inputs.get("price"), "1.25", next));
        assertEquals(new BigDecimal("1.2"), FormValidator.clean("price", inputs.get("price"), "1.2", next));
        assertTrue(warned.isEmpty(), warned.toString());
    }

    @Test
    void outOfRangeFromCodeWarnsAndKeepsTheFilesValue() {
        final List<String> warned = new ArrayList<>();
        final InputLimits limits = InputLimits.resolve("t", inputs, Map.of(), Map.of(),
                Map.of("price", view -> 11), null, warned::add);
        assertEquals(2, limits.decimals("price", inputs.get("price")));
        assertEquals(1, warned.size(), warned.toString());
        assertTrue(warned.get(0).contains("inputDecimals('price')"), warned.get(0));
    }

    @Test
    void failureCarriesTheDecimalsForTheErrorLine() {
        final FormSubmit.Outcome outcome = FormSubmit.decode(codecs, inputs, parsed.file().errors(), Money.class,
                Map.of("price", "12.505", "whole", "1", "free", "1"));
        assertNull(outcome.form());
        final FormSubmit.Failure failure = outcome.failures().get(0);
        assertEquals("2", failure.decimals());
        assertTrue(failure.tooManyDecimals());
        assertEquals("<red>At most <decimals> decimals, not <value>", failure.markup());

        final FormSubmit.Outcome rangeError = FormSubmit.decode(codecs, inputs, parsed.file().errors(), Money.class,
                Map.of("price", "-5", "whole", "1", "free", "1"));
        assertTrue(!rangeError.failures().get(0).tooManyDecimals(), "refused for the range, not the decimals");
    }

    @Test
    void anAcceptedAnswerReachesTheRecord() {
        final FormSubmit.Outcome outcome = FormSubmit.decode(codecs, inputs, parsed.file().errors(), Money.class,
                Map.of("price", "12.34", "whole", "1", "free", "1"));
        assertEquals(new Money(new BigDecimal("12.34")), outcome.form());
    }
}
