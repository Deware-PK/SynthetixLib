package com.github.dewarepk.synthetixlib.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.config.annotation.NotEmpty;
import com.github.dewarepk.synthetixlib.config.annotation.Range;
import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.dialog.file.DialogInput;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The answers of a click: server-side limits, the record mapping, the response view and the async hop. */
public class FormSubmitTest {

    public enum Length {
        SHORT, LONG
    }

    public record Form(@Range(min = 1, max = 100) double price, int amount, long count, String note, boolean anon,
                       Length length) {
    }

    public record Named(@NotEmpty String title, @Range(min = 5, max = 10) int size) {
    }

    private static final String FILE = """
            title: t
            type: notice
            inputs:
              price: { type: number, label: 'P', min: 1, max: 100, step: 0.5 }
              amount: { type: number, label: 'A', min: 0, max: 50, step: 1 }
              count: { type: number, label: 'C', min: 0, max: 1000000 }
              note: { type: text, label: 'N', max-length: 8, multiline: { max-lines: 2 } }
              anon: { type: bool, label: 'B' }
              length:
                type: option
                label: 'L'
                options:
                  short: {}
                  long: {}
            errors:
              price: '<red>Between <min> and <max>, not <value>'
            """;

    private final Codecs codecs = DialogFixtures.codecs();
    private final Map<String, DialogInput> inputs = new DialogParser(codecs).parse(FILE).file().inputs();
    private final Map<String, String> lines = new DialogParser(codecs).parse(FILE).file().errors();

    private static Map<String, Object> good() {
        return new java.util.HashMap<>(Map.of("price", 12.5f, "amount", 3f, "count", 99f, "note", "hi", "anon", true,
                "length", "long"));
    }

    private FormSubmit.Outcome decode(final Map<String, Object> answers) {
        return FormSubmit.decode(codecs, inputs, lines, Form.class, answers);
    }

    private void refused(final Map<String, Object> answers, final String key) {
        final FormSubmit.Outcome outcome = decode(answers);
        assertNull(outcome.form(), "reached the record: " + answers);
        assertEquals(List.of(key), outcome.failures().stream().map(FormSubmit.Failure::key).toList());
    }

    @Test
    void everyKindMapsOntoTheRecord() {
        final FormSubmit.Outcome outcome = decode(good());
        assertEquals(new Form(12.5, 3, 99, "hi", true, Length.LONG), outcome.form());
        assertTrue(outcome.failures().isEmpty());
    }

    @Test
    void aFloatIsReadAsItsDecimalForm() {
        final Map<String, Object> answers = good();
        answers.put("price", 1.5f);
        assertEquals(1.5, ((Form) decode(answers).form()).price());
        assertEquals(0.1, FormValidator.exact(0.1f));
    }

    @Test
    void anIntegerComponentTakesWholeNumbersOnly() {
        final Map<String, Object> whole = good();
        whole.put("amount", 12.0f);
        assertEquals(12, ((Form) decode(whole).form()).amount());

        final Map<String, Object> fraction = good();
        fraction.put("count", 12.5f);
        // 12.5 is a valid slider value (no step) but not a long
        final FormSubmit.Outcome outcome = decode(fraction);
        assertNull(outcome.form());
        assertEquals("count", outcome.failures().get(0).key());
    }

    @Test
    void textBeyondMaxLengthIsRefused() {
        final Map<String, Object> answers = good();
        answers.put("note", "123456789");
        refused(answers, "note");
    }

    @Test
    void multilineTextBeyondMaxLinesIsRefused() {
        final Map<String, Object> answers = good();
        answers.put("note", "a\nb");
        assertNotNull(decode(answers).form());
        answers.put("note", "a\nb\nc");
        refused(answers, "note");
    }

    @Test
    void aNewLineInASingleLineInputIsRefused() {
        final Map<String, DialogInput> single = new DialogParser(codecs).parse("""
                title: t
                type: notice
                inputs:
                  note: { type: text, label: 'N', max-length: 8 }
                """).file().inputs();
        assertEquals(List.of("note"), FormValidator.validate(single, Map.of("note", "a\nb")).bad());
        assertTrue(FormValidator.validate(single, Map.of("note", "a b")).bad().isEmpty());
    }

    @Test
    void numbersOutsideMinAndMaxAreRefused() {
        final Map<String, Object> low = good();
        low.put("price", 0.5f);
        refused(low, "price");
        final Map<String, Object> high = good();
        high.put("price", 100.5f);
        refused(high, "price");
        final Map<String, Object> nan = good();
        nan.put("price", Float.NaN);
        refused(nan, "price");
        final Map<String, Object> inf = good();
        inf.put("price", Float.POSITIVE_INFINITY);
        refused(inf, "price");
    }

    @Test
    void numbersOffTheStepAreRefused() {
        final Map<String, Object> off = good();
        off.put("price", 12.3f);
        refused(off, "price");
        final Map<String, Object> onStep = good();
        onStep.put("price", 99.5f);
        assertNotNull(decode(onStep).form());
    }

    @Test
    void theMaximumIsReachableOffTheStepGrid() {
        final Map<String, DialogInput> slider = new DialogParser(codecs).parse("""
                title: t
                type: notice
                inputs:
                  n: { type: number, label: 'N', min: 0, max: 10, step: 3 }
                """).file().inputs();
        assertTrue(FormValidator.validate(slider, Map.of("n", 10f)).bad().isEmpty());
        assertTrue(FormValidator.validate(slider, Map.of("n", 9f)).bad().isEmpty());
        assertEquals(List.of("n"), FormValidator.validate(slider, Map.of("n", 8f)).bad());
    }

    @Test
    void anOptionThatIsNotInTheListIsRefused() {
        final Map<String, Object> answers = good();
        answers.put("length", "medium");
        refused(answers, "length");
    }

    @Test
    void aMissingOrWrongKindOfAnswerIsRefusedNotAnError() {
        final Map<String, Object> missing = good();
        missing.remove("note");
        refused(missing, "note");
        final Map<String, Object> wrong = good();
        wrong.put("anon", "yes");
        refused(wrong, "anon");
        assertFalse(decode(Map.of()).failures().isEmpty());
    }

    @Test
    void miniMessageInTextStaysLiteral() {
        final Map<String, DialogInput> text = new DialogParser(codecs).parse("""
                title: t
                type: notice
                inputs:
                  title: { type: text, label: 'T', max-length: 40 }
                  size: { type: number, label: 'S', min: 5, max: 10, step: 1 }
                """).file().inputs();
        final FormSubmit.Outcome outcome = FormSubmit.decode(codecs, text, Map.of(), Named.class,
                Map.of("title", "<red><click:run_command:/op me>hi", "size", 7f));
        assertEquals(new Named("<red><click:run_command:/op me>hi", 7), outcome.form());
    }

    @Test
    void theRecordRulesComeFromTheConfigCodecs() {
        final Map<String, DialogInput> text = new DialogParser(codecs).parse("""
                title: t
                type: notice
                inputs:
                  title: { type: text, label: 'T' }
                  size: { type: number, label: 'S', min: 0, max: 20, step: 1 }
                """).file().inputs();
        final FormSubmit.Outcome small = FormSubmit.decode(codecs, text, Map.of(), Named.class,
                Map.of("title", "x", "size", 3f));
        assertNull(small.form(), "@Range(min = 5)");
        assertEquals(List.of("size"), small.failures().stream().map(FormSubmit.Failure::key).toList());
        final FormSubmit.Outcome empty = FormSubmit.decode(codecs, text, Map.of(), Named.class,
                Map.of("title", "", "size", 7f));
        assertNull(empty.form(), "@NotEmpty");
        assertEquals(List.of("title"), empty.failures().stream().map(FormSubmit.Failure::key).toList());
    }

    @Test
    void aFailureCarriesTheLineOfTheFileAndItsPlaceholders() {
        final Map<String, Object> answers = good();
        answers.put("price", 200f);
        final FormSubmit.Failure failure = decode(answers).failures().get(0);
        assertEquals("<red>Between <min> and <max>, not <value>", failure.markup());
        assertEquals("1", failure.min());
        assertEquals("100", failure.max());
        assertEquals("200", failure.value());

        final Map<String, Object> other = good();
        other.put("note", "123456789");
        assertNull(decode(other).failures().get(0).markup(), "no errors.note line: the default message is used");
    }

    @Test
    void theResponseGivesCheckedValuesOrEmpty() {
        final ResponseImpl response = new ResponseImpl(inputs, good());
        assertEquals(OptionalDouble.of(12.5), response.number("price"));
        assertEquals(OptionalInt.of(3), response.integer("amount"));
        assertEquals(OptionalInt.empty(), response.integer("price"), "12.5 is not an int");
        assertEquals(Optional.of("hi"), response.text("note"));
        assertEquals(Optional.of(true), response.bool("anon"));
        assertEquals(Optional.of("long"), response.option("length"));

        assertEquals(OptionalDouble.empty(), response.number("nope"));
        assertEquals(Optional.empty(), response.text("price"), "a number input is not text");
        assertEquals(Optional.empty(), new ResponseImpl(inputs, Map.of()).text("note"));

        final Map<String, Object> hacked = good();
        hacked.put("note", "123456789");
        hacked.put("length", "medium");
        hacked.put("price", 500f);
        final ResponseImpl bad = new ResponseImpl(inputs, hacked);
        assertEquals(Optional.empty(), bad.text("note"));
        assertEquals(Optional.empty(), bad.option("length"));
        assertEquals(OptionalDouble.empty(), bad.number("price"));
    }

    @Test
    void anAsyncHandlerRunsOnAnotherThreadAndThenCallsBack() throws Exception {
        final var pool = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "async-test"));
        try {
            final AtomicReference<String> handlerThread = new AtomicReference<>();
            final AtomicReference<String> afterThread = new AtomicReference<>();
            final CountDownLatch done = new CountDownLatch(1);
            FormSubmit.run((form, click) -> handlerThread.set(Thread.currentThread().getName()), "form", "click",
                    true, pool, e -> { }, () -> {
                        afterThread.set(Thread.currentThread().getName());
                        done.countDown();
                    });
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals("async-test", handlerThread.get());
            assertNotEquals(Thread.currentThread().getName(), handlerThread.get());
            assertEquals("async-test", afterThread.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void anAsyncHandlerThatThrowsIsReportedAndStillEnds() throws Exception {
        final AtomicReference<RuntimeException> failed = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);
        FormSubmit.run((form, click) -> {
            throw new IllegalStateException("boom");
        }, "form", "click", true, Runnable::run, failed::set, done::countDown);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals("boom", failed.get().getMessage());
    }

    @Test
    void aSyncHandlerRunsRightAway() {
        final AtomicReference<String> thread = new AtomicReference<>();
        final AtomicReference<String> after = new AtomicReference<>();
        FormSubmit.run((form, click) -> thread.set(Thread.currentThread().getName()), "form", "click", false,
                runnable -> {
                    throw new AssertionError("no executor for a sync handler");
                }, e -> { }, () -> after.set("called"));
        assertEquals(Thread.currentThread().getName(), thread.get());
        assertNull(after.get(), "after belongs to the async path");
    }

    @Test
    void keysCollidingOnTheWireDropTheSecondInput() {
        final DialogParser.Parsed parsed = new DialogParser(codecs).parse("""
                title: t
                type: notice
                inputs:
                  a-b: { type: bool, label: 'X' }
                  a_b: { type: bool, label: 'Y' }
                  c-d: { type: bool, label: 'Z' }
                """);
        assertEquals(List.of("a-b", "c-d"), List.copyOf(parsed.file().inputs().keySet()));
        assertEquals(List.of("inputs.a_b"), parsed.errors().stream().map(p -> p.path()).toList());
        assertEquals("my_key", InputKeys.wire("my-key"));
    }
}
