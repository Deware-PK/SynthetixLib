package com.github.dewarepk.synthetixlib.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.dialog.file.ClickAction;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

/** The steps of a click run in file order, one failing step does not stop the others, and the result says how it ended. */
class ClickFlowTest {

    /** Records what was asked; {@code fail} makes the step with that name throw. */
    private static final class Recorder implements ClickFlow.Steps {
        final List<String> log = new ArrayList<>();
        String fail = "";
        boolean opens = true;

        private void did(final String what) {
            log.add(what);
            if (!fail.isEmpty() && what.startsWith(fail)) {
                throw new IllegalStateException(what);
            }
        }

        @Override
        public void command(final String command) {
            did("command:" + command);
        }

        @Override
        public void console(final String command) {
            did("console:" + command);
        }

        @Override
        public void message(final String text) {
            did("message:" + text);
        }

        @Override
        public void sound(final NamespacedKey key) {
            did("sound:" + key.getKey());
        }

        @Override
        public boolean open(final String dialog) {
            did("open:" + dialog);
            return opens;
        }

        @Override
        public boolean menu(final String menu) {
            did("menu:" + menu);
            return opens;
        }

        @Override
        public boolean back() {
            did("back");
            return opens;
        }

        @Override
        public void run(final String action) {
            did("action:" + action);
        }
    }

    private static List<ClickAction> parse(final String... texts) {
        return java.util.Arrays.stream(texts).map(ClickAction::parse).toList();
    }

    @Test
    void stepsRunInOrder() {
        final Recorder steps = new Recorder();
        final ClickFlow.Result result = ClickFlow.run(parse("command:a", "action:b", "sound:ui.click", "message:c",
                "console:d", "close"), steps, e -> { });
        assertEquals(List.of("command:a", "action:b", "sound:click".replace("click", "ui.click"), "message:c",
                "console:d"), steps.log);
        assertTrue(result.closed());
        assertFalse(result.replaced());
    }

    @Test
    void aStepThatThrowsIsReportedAndTheNextOnesStillRun() {
        final Recorder steps = new Recorder();
        steps.fail = "action:boom";
        final List<RuntimeException> failures = new ArrayList<>();
        ClickFlow.run(parse("command:a", "action:boom", "message:after"), steps, failures::add);
        assertEquals(List.of("command:a", "action:boom", "message:after"), steps.log);
        assertEquals(1, failures.size());
        assertEquals("action:boom", failures.get(0).getMessage());
    }

    @Test
    void openingAnotherDialogOrMenuOrGoingBackReplacesTheScreen() {
        for (final String step : List.of("open:other", "menu:shop", "back")) {
            final Recorder steps = new Recorder();
            final ClickFlow.Result result = ClickFlow.run(parse("message:x", step), steps, e -> { });
            assertTrue(result.replaced(), step);
            assertFalse(result.closed(), step);
        }
    }

    @Test
    void aStepThatCouldNotOpenAnythingDoesNotReplaceTheScreen() {
        final Recorder steps = new Recorder();
        steps.opens = false;
        final ClickFlow.Result result = ClickFlow.run(parse("open:missing", "back"), steps, e -> { });
        assertFalse(result.replaced());
    }

    @Test
    void urlAndCopyAreNotRunByCallbacks() {
        final Recorder steps = new Recorder();
        final ClickFlow.Result result = ClickFlow.run(parse("url:https://example.com", "copy:x"), steps, e -> { });
        assertEquals(List.of(), steps.log);
        assertFalse(result.closed() || result.replaced());
    }

    @Test
    void anEmptyButtonDoesNothing() {
        final Recorder steps = new Recorder();
        final ClickFlow.Result result = ClickFlow.run(List.of(), steps, e -> { });
        assertEquals(List.of(), steps.log);
        assertFalse(result.closed() || result.replaced());
    }
}
