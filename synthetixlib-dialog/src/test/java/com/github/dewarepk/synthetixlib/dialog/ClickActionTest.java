package com.github.dewarepk.synthetixlib.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.dewarepk.synthetixlib.dialog.file.ClickAction;
import java.net.URI;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClickActionTest {

    @Test
    void everyBuiltInParses() {
        assertEquals(new ClickAction.Close(), ClickAction.parse("close"));
        assertEquals(new ClickAction.Back(), ClickAction.parse(" BACK "));
        assertEquals(new ClickAction.Open("confirm"), ClickAction.parse("open:confirm"));
        assertEquals(new ClickAction.Menu("shop"), ClickAction.parse("menu:shop"));
        assertEquals(new ClickAction.Command("auction sell"), ClickAction.parse("command:/auction sell"));
        assertEquals(new ClickAction.Console("give <player> stone"), ClickAction.parse("console:give <player> stone"));
        assertEquals(new ClickAction.Message("<green>Done"), ClickAction.parse("message:<green>Done"));
        assertEquals(new ClickAction.Url(URI.create("https://example.com/a?b=c:d")),
                ClickAction.parse("url:https://example.com/a?b=c:d"));
        assertEquals(new ClickAction.Copy("a:b"), ClickAction.parse("copy:a:b"));
        assertEquals(new ClickAction.PlaySound(NamespacedKey.minecraft("ui.button.click")),
                ClickAction.parse("sound:ui.button.click"));
        assertEquals(new ClickAction.Run("sell"), ClickAction.parse("action:sell"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"close", "back", "open:x", "menu:x", "command:say hi", "console:say hi", "message:hi",
            "url:https://example.com", "copy:x", "sound:minecraft:ui.button.click", "action:x-y"})
    void formatReadsBack(final String text) {
        final ClickAction action = ClickAction.parse(text);
        assertEquals(action, ClickAction.parse(action.format()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "fly", "close:now", "open", "open:Bad Id", "menu:", "command:", "command:/",
            "message:", "url:", "url:ftp://example.com", "url:javascript:alert(1)", "url:example.com",
            "url:https://", "copy:", "sound:", "sound:Bad Key", "action:", "action:UPPER"})
    void badActionsAreRejected(final String text) {
        assertThrows(IllegalArgumentException.class, () -> ClickAction.parse(text));
    }
}
