package com.github.dewarepk.synthetixlib.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.dialog.file.DialogInput;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.junit.jupiter.api.Test;

/** The number-field input: parsing the typed text, limits (file and code), record kinds, the response, error lines. */
public class NumberFieldTest {

    public record Whole(long price, int amount) {
    }

    public record Money(BigDecimal price) {
    }

    public record Plain(double price) {
    }

    public record Text(String price) {
    }

    private static final String FILE = """
            title: t
            type: notice
            inputs:
              price: { type: number-field, label: 'P', min: 1, max: 1000000, initial: 100 }
              amount: { type: number-field, label: 'A', min: 0, max: 50 }
              money: { type: number-field, label: 'M', format: decimal, min: 0.01, max: 1000, max-length: 10 }
              free: { type: number-field, label: 'F', format: decimal }
              big: { type: number-field, label: 'B', max-length: 25 }
            errors:
              price: '<red>Between <min> and <max>, not <value>'
            """;

    private final Codecs codecs = DialogFixtures.codecs();
    private final DialogParser.Parsed parsed = new DialogParser(codecs).parse(FILE);
    private final Map<String, DialogInput> inputs = parsed.file().inputs();

    private Object clean(final String key, final Object raw) {
        return FormValidator.clean(key, inputs.get(key), raw, InputLimits.file());
    }

    // --- the file -----------------------------------------------------------------------------------------------

    @Test
    void theFileKeysAreRead() {
        assertTrue(parsed.errors().isEmpty(), parsed.errors().toString());
        final var price = assertInstanceOf(DialogInput.NumberFieldInput.class, inputs.get("price"));
        assertTrue(price.isInteger(), "integer is the default");
        assertEquals(Optional.of(1.0), price.min());
        assertEquals(Optional.of(1000000.0), price.max());
        assertEquals(Optional.of(100.0), price.initial());
        assertEquals(Optional.empty(), price.maxLength());
        final var money = (DialogInput.NumberFieldInput) inputs.get("money");
        assertEquals(DialogInput.NumberFormat.DECIMAL, money.format().orElseThrow());
        assertEquals(Optional.of(10), money.maxLength());
        assertEquals(Optional.empty(), ((DialogInput.NumberFieldInput) inputs.get("free")).min());
    }

    @Test
    void badFileValuesAreRepairedOrDropped() {
        final DialogParser.Parsed bad = new DialogParser(codecs).parse("""
                title: t
                type: notice
                inputs:
                  a: { type: number-field, label: 'A', min: 10, max: 1 }
                  b: { type: number-field, label: 'B', min: 1, max: 5, initial: 99, max-length: 0 }
                """);
        assertTrue(!bad.file().inputs().containsKey("a"), "min > max drops the input");
        final var b = (DialogInput.NumberFieldInput) bad.file().inputs().get("b");
        assertEquals(Optional.of(5.0), b.initial(), "initial is clamped into min..max");
        assertEquals(Optional.of(1), b.maxLength());
        assertTrue(bad.errors().stream().anyMatch(problem -> problem.path().equals("inputs.a")), bad.errors().toString());
        assertTrue(bad.errors().stream().anyMatch(problem -> problem.path().equals("inputs.b.initial")));
    }

    // --- parsing ------------------------------------------------------------------------------------------------

    @Test
    void integerTextTable() {
        assertEquals(42L, clean("amount", " 42 "));
        assertEquals(1000L, clean("price", "1,000"));
        assertEquals(1000L, clean("price", "1_000"));
        assertEquals(1234567L, clean("big", "1,234,567"));
        assertEquals(7L, clean("price", "+7"));
        assertEquals(5L, clean("price", "0005"));
        assertEquals(-1L, clean("big", "-1"), "no min: negative is fine");
        assertNull(clean("amount", "-1"), "below min 0");
        assertNull(clean("price", "1e3"), "no exponent");
        assertNull(clean("price", "12.5"), "no fraction in an integer");
        assertNull(clean("price", "12,5"), "a decimal comma is not a thousands separator");
        assertNull(clean("price", "1,00"));
        assertNull(clean("price", "1__000"));
        assertNull(clean("price", ",100"));
        assertNull(clean("price", ""));
        assertNull(clean("price", "   "));
        assertNull(clean("price", "NaN"));
        assertNull(clean("price", "Infinity"));
        assertNull(clean("price", "0x10"));
        assertNull(clean("price", "1 000"));
        assertNull(clean("price", "١٢"), "only ASCII digits");
        assertNull(clean("price", null));
        assertNull(clean("price", 5f), "a number input's float is not text");
    }

    @Test
    void nineteenDigitsAndLongLimits() {
        assertNull(clean("price", "1234567890123456789"), "19 digits are over the default max-length 18");
        assertEquals(Long.MAX_VALUE, clean("big", "9223372036854775807"));
        assertNull(clean("big", "9223372036854775808"), "does not fit a long");
        assertNull(clean("big", "99999999999999999999"));
        assertEquals(Long.MIN_VALUE, clean("big", "-9223372036854775808"));
    }

    @Test
    void decimalTextTable() {
        assertEquals(new BigDecimal("12.50"), clean("money", "12.50"));
        assertEquals(new BigDecimal("1000"), clean("money", "1,000"));
        assertEquals(new BigDecimal("0.01"), clean("money", "0.01"));
        assertEquals(new BigDecimal("1234.5"), clean("free", "1,234.5"));
        assertEquals(new BigDecimal("-0.5"), clean("free", "-0.5"));
        assertEquals(new BigDecimal("3"), clean("money", "3"), "a whole number is a decimal too");
        assertNull(clean("money", ".5"));
        assertNull(clean("money", "5."));
        assertNull(clean("money", "1e2"));
        assertNull(clean("money", "1.2.3"));
        assertNull(clean("money", "1,5"), "comma is a separator, not a decimal point");
        assertNull(clean("money", "NaN"));
        assertNull(clean("money", "Infinity"));
        assertNull(clean("money", "-Infinity"));
        assertNull(clean("money", "0.001"), "below min 0.01");
        assertNull(clean("money", "1000.01"), "above max");
        assertNull(clean("money", "12345678901"), "over max-length 10");
    }

    @Test
    void minAndMaxAreInclusive() {
        assertEquals(1L, clean("price", "1"));
        assertEquals(1000000L, clean("price", "1000000"));
        assertNull(clean("price", "0"));
        assertNull(clean("price", "1000001"));
    }

    @Test
    void aDecimalThatOverflowsADoubleIsRefused() {
        final String huge = "9".repeat(400);
        final Map<String, DialogInput> wide = new DialogParser(codecs).parse("""
                title: t
                type: notice
                inputs:
                  x: { type: number-field, label: 'X', format: decimal, max-length: 500 }
                """).file().inputs();
        assertNull(FormValidator.clean("x", wide.get("x"), huge, InputLimits.file()));
    }

    // --- limits from code ---------------------------------------------------------------------------------------

    @Test
    void codeLimitsReplaceTheFileValuesForTheCheck() {
        final InputLimits limits = new InputLimits(
                Map.of("price", new InputLimits.Bounds(10, 500, OptionalDouble.empty())), Map.of("price", 4));
        final DialogInput price = inputs.get("price");
        assertEquals(10L, FormValidator.clean("price", price, "10", limits));
        assertEquals(500L, FormValidator.clean("price", price, "500", limits));
        assertNull(FormValidator.clean("price", price, "9", limits), "file min 1 would take it");
        assertNull(FormValidator.clean("price", price, "501", limits));
        assertNull(FormValidator.clean("price", price, "1,000", limits), "5 characters, code max-length is 4");
        final DialogInput amount = inputs.get("amount");
        assertEquals(50L, FormValidator.clean("amount", amount, "50", limits), "another input keeps its own limits");
        assertNull(FormValidator.clean("amount", amount, "51", limits));
    }

    @Test
    void aSliderRangeAndStepFromCodeAreUsed() {
        final Map<String, DialogInput> slider = new DialogParser(codecs).parse("""
                title: t
                type: notice
                inputs:
                  n: { type: number, label: 'N', min: 0, max: 10, step: 1 }
                """).file().inputs();
        final InputLimits limits = new InputLimits(Map.of("n", new InputLimits.Bounds(0, 100, OptionalDouble.of(10))),
                Map.of());
        assertTrue(FormValidator.validate(slider, Map.of("n", 50f), limits).bad().isEmpty());
        assertEquals(List.of("n"), FormValidator.validate(slider, Map.of("n", 55f), limits).bad(), "off the code step");
        assertEquals(List.of("n"), FormValidator.validate(slider, Map.of("n", 50f)).bad(), "the file range ends at 10");
    }

    @Test
    void theLimitsOfAShowingAreAskedOnceAndFallBackToTheFile() {
        final List<String> warned = new java.util.ArrayList<>();
        final int[] calls = {0};
        final Map<String, java.util.function.Function<DialogView<Void>, Range>> ranges = Map.of(
                "price", view -> {
                    calls[0]++;
                    return Range.of(5, 50);
                },
                "amount", view -> {
                    throw new IllegalStateException("boom");
                },
                "text", view -> Range.of(1, 2));
        final Map<String, java.util.function.Function<DialogView<Void>, Integer>> lengths = Map.of(
                "price", view -> 6, "big", view -> 0);
        final InputLimits limits = InputLimits.resolve("t", inputs, ranges, lengths, null, warned::add);
        assertEquals(1, calls[0]);
        assertEquals(new InputLimits.Bounds(5, 50, OptionalDouble.empty()), limits.bounds("price", inputs.get("price")));
        assertEquals(new InputLimits.Bounds(0, 50, OptionalDouble.empty()), limits.bounds("amount", inputs.get("amount")),
                "a function that throws leaves the file's range");
        assertEquals(6, limits.maxLength("price", inputs.get("price")));
        assertEquals(25, limits.maxLength("big", inputs.get("big")), "length 0 is refused, the file value stays");
        assertEquals(2, warned.size(), warned.toString());
    }

    @Test
    void aRangeNeedsOrderedFiniteNumbers() {
        assertThrows(IllegalArgumentException.class, () -> Range.of(5, 1));
        assertThrows(IllegalArgumentException.class, () -> Range.of(0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> Range.of(0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Range.of(0, 1, 0));
        assertEquals(OptionalDouble.of(2), Range.of(0, 10, 2).step());
    }

    // --- the record ---------------------------------------------------------------------------------------------

    private FormSubmit.Outcome decode(final Class<?> form, final Map<String, Object> answers) {
        return FormSubmit.decode(codecs, inputs, parsed.file().errors(), form, answers);
    }

    private static Map<String, Object> answers() {
        return new HashMap<>(Map.of("price", "1,500", "amount", "7", "money", "19.99", "free", "2.5", "big", "1"));
    }

    @Test
    void integerGoesToLongAndInt() {
        final FormSubmit.Outcome outcome = decode(Whole.class, answers());
        assertEquals(new Whole(1500, 7), outcome.form());
    }

    @Test
    void decimalGoesToDoubleAndBigDecimal() {
        final Map<String, DialogInput> only = Map.of("price", inputs.get("money"));
        assertEquals(new Money(new BigDecimal("19.99")), FormSubmit.decode(codecs, only, Map.of(), Money.class,
                Map.of("price", "19.99")).form());
        assertEquals(new Plain(19.99), FormSubmit.decode(codecs, only, Map.of(), Plain.class,
                Map.of("price", "19.99")).form());
    }

    @Test
    void anIntComponentRefusesWhatDoesNotFitAnInt() {
        final Map<String, DialogInput> wide = new DialogParser(codecs).parse("""
                title: t
                type: notice
                inputs:
                  price: { type: number-field, label: 'P', min: 0, max: 100000000000 }
                  amount: { type: number-field, label: 'A', min: 0, max: 100000000000, max-length: 20 }
                """).file().inputs();
        final FormSubmit.Outcome outcome = FormSubmit.decode(codecs, wide, Map.of(), Whole.class,
                Map.of("price", "5", "amount", "5000000000"));
        assertNull(outcome.form());
        assertEquals("amount", outcome.failures().get(0).key());
    }

    @Test
    void wrongTextIsAFailureWithTheTypedValueAndTheEffectiveLimits() {
        final Map<String, Object> bad = answers();
        bad.put("price", "12.5");
        final FormSubmit.Outcome outcome = decode(Whole.class, bad);
        assertNull(outcome.form());
        final FormSubmit.Failure failure = outcome.failures().get(0);
        assertEquals("price", failure.key());
        assertEquals("<red>Between <min> and <max>, not <value>", failure.markup());
        assertEquals("1", failure.min());
        assertEquals("1000000", failure.max());
        assertEquals("12.5", failure.value(), "what was typed");

        final InputLimits code = new InputLimits(
                Map.of("price", new InputLimits.Bounds(10, 99.5, OptionalDouble.empty())), Map.of());
        bad.put("price", "5");
        final FormSubmit.Failure withCode = FormSubmit.decode(codecs, inputs, parsed.file().errors(), Whole.class, bad,
                code).failures().get(0);
        assertEquals("10", withCode.min());
        assertEquals("99.5", withCode.max());
        assertEquals("5", withCode.value());
    }

    @Test
    void anOpenEndedFieldHasNoMinOrMaxText() {
        final var only = Map.of("free", inputs.get("free"));
        final FormSubmit.Failure failure = FormSubmit.decode(codecs, only, Map.of(), Plain.class,
                Map.of("free", "abc")).failures().get(0);
        assertEquals("", failure.min());
        assertEquals("", failure.max());
        assertEquals("abc", failure.value());
    }

    // --- the response -------------------------------------------------------------------------------------------

    @Test
    void theResponseReadsTheTypedNumber() {
        final ResponseImpl response = new ResponseImpl(inputs, answers());
        assertEquals(OptionalDouble.of(1500), response.number("price"));
        assertEquals(OptionalInt.of(1500), response.integer("price"));
        assertEquals(OptionalDouble.of(19.99), response.number("money"));
        assertEquals(OptionalInt.empty(), response.integer("money"), "19.99 is not whole");
        assertEquals(OptionalInt.empty(), response.integer("nope"));
        assertEquals(Optional.empty(), response.text("price"), "a number-field is not a text input");

        final Map<String, Object> wrong = answers();
        wrong.put("price", "abc");
        wrong.put("big", "5000000000");
        final ResponseImpl bad = new ResponseImpl(inputs, wrong);
        assertEquals(OptionalDouble.empty(), bad.number("price"));
        assertEquals(OptionalDouble.of(5_000_000_000d), bad.number("big"));
        assertEquals(OptionalInt.empty(), bad.integer("big"), "does not fit an int");

        final ResponseImpl code = new ResponseImpl(inputs, answers(), new InputLimits(
                Map.of("price", new InputLimits.Bounds(1, 100, OptionalDouble.empty())), Map.of()));
        assertEquals(OptionalDouble.empty(), code.number("price"), "1,500 is over the max the code set");
    }

    // --- the form check -----------------------------------------------------------------------------------------

    @Test
    void theFormCheckKnowsWhichComponentsFit() {
        final Map<String, DialogInput> integer = Map.of("price", inputs.get("price"), "amount", inputs.get("amount"));
        assertTrue(FormCheck.check(codecs, "sell", Whole.class, integer).isEmpty());
        final Map<String, DialogInput> decimal = Map.of("price", inputs.get("money"));
        assertTrue(FormCheck.check(codecs, "sell", Money.class, decimal).isEmpty());
        assertTrue(FormCheck.check(codecs, "sell", Plain.class, decimal).isEmpty());
        final var text = FormCheck.check(codecs, "sell", Text.class, decimal);
        assertEquals(1, text.size());
        assertTrue(text.get(0).message().contains("number-field"), text.toString());
        assertEquals("inputs.price", text.get(0).path());
    }

    // --- errors from code ---------------------------------------------------------------------------------------

    @Test
    void fieldErrorsBecomeLinesWithTheValuesOfTheirInput() {
        final List<FieldError> errors = List.of(
                FieldError.of("price", "<red>Too high: <limit>", Placeholder.unparsed("limit", "500")),
                FieldError.form("<red>No room"));
        final InputLimits limits = new InputLimits(
                Map.of("price", new InputLimits.Bounds(1, 500, OptionalDouble.empty())), Map.of());
        final List<FormSubmit.Failure> lines = FormSubmit.failures(errors, inputs, Map.of("price", "700"), limits);
        assertEquals(2, lines.size());
        assertEquals("price", lines.get(0).key());
        assertEquals("<red>Too high: <limit>", lines.get(0).markup());
        assertEquals("1", lines.get(0).min());
        assertEquals("500", lines.get(0).max());
        assertEquals("700", lines.get(0).value());
        assertEquals("", lines.get(1).key());
        assertEquals("", lines.get(1).min());
        assertEquals("", lines.get(1).value());
    }

    @Test
    void anEmptyFieldErrorKeyIsAFormError() {
        assertEquals("", FieldError.form("x").key());
        assertThrows(NullPointerException.class, () -> FieldError.of(null, "x"));
    }
}
