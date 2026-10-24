package com.github.dewarepk.synthetixlib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.gui.file.ClickAction;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClickActionTest {

    @Test
    void everyBuiltInIsParsed() {
        assertEquals(new ClickAction.Close(), ClickAction.parse("close"));
        assertEquals(new ClickAction.Back(), ClickAction.parse("back"));
        assertEquals(new ClickAction.Refresh(), ClickAction.parse("refresh"));
        assertEquals(new ClickAction.PageNext(), ClickAction.parse("page-next"));
        assertEquals(new ClickAction.PagePrevious(), ClickAction.parse("page-previous"));
        assertEquals(new ClickAction.Open("my-listings"), ClickAction.parse("open:my-listings"));
        assertEquals(new ClickAction.Command("auction sell"), ClickAction.parse("command:auction sell"));
        assertEquals(new ClickAction.Console("give <player> stone"), ClickAction.parse("console:give <player> stone"));
        assertEquals(new ClickAction.Message("<green>Done: yes"), ClickAction.parse("message:<green>Done: yes"));
        assertEquals(new ClickAction.PlaySound(NamespacedKey.minecraft("ui.button.click")),
                ClickAction.parse("sound:ui.button.click"));
        assertEquals(new ClickAction.Run("toggle-notify"), ClickAction.parse("action:toggle-notify"));
    }

    @Test
    void aNameWithAnArgumentThatIsNotBuiltInIsALink() {
        assertEquals(new ClickAction.Linked("dialog", "sell"), ClickAction.parse("dialog:sell"));
        assertEquals(new ClickAction.Linked("dialog", "sell"), ClickAction.parse(" Dialog : sell "));
        assertEquals("dialog:sell", ClickAction.parse("dialog:sell").format());
    }

    @Test
    void theNameIsNotCaseSensitiveAndSpacesAreIgnored() {
        assertEquals(new ClickAction.Close(), ClickAction.parse("  CLOSE "));
        assertEquals(new ClickAction.Open("shop"), ClickAction.parse("Open : shop"));
    }

    @Test
    void aLeadingSlashOfACommandIsDropped() {
        assertEquals(new ClickAction.Command("auction sell"), ClickAction.parse("command:/auction sell"));
        assertEquals(new ClickAction.Console("say hi"), ClickAction.parse("console: /say hi"));
    }

    @Test
    void everyActionReadsBackFromItsTextForm() {
        for (final String text : List.of("close", "back", "refresh", "page-next", "page-previous", "open:shop",
                "command:auction sell", "console:say hi", "message:<red>no", "sound:minecraft:ui.button.click",
                "action:confirm")) {
            assertEquals(text, ClickAction.parse(text).format());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "teleport", ":x"})
    void unknownOrEmptyIsRejected(final String text) {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ClickAction.parse(text));
        assertTrue(e.getMessage().contains("action"), e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dialog:", "dialog:Bad Id"})
    void aLinkNeedsAValidScreenId(final String text) {
        assertThrows(IllegalArgumentException.class, () -> ClickAction.parse(text));
    }

    @Test
    void theMessageNamesTheUnknownActionAndTheValidOnes() {
        final String message = assertThrows(IllegalArgumentException.class,
                () -> ClickAction.parse("teleport")).getMessage();
        assertTrue(message.contains("'teleport'") && message.contains("page-previous"), message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"open", "open:", "command:", "command:/", "console:", "message:", "sound:", "action:"})
    void anArgumentIsRequired(final String text) {
        assertThrows(IllegalArgumentException.class, () -> ClickAction.parse(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"close:now", "back:1", "refresh:x", "page-next:2", "page-previous:"})
    void anActionWithoutArgumentRejectsOne(final String text) {
        final String message = assertThrows(IllegalArgumentException.class, () -> ClickAction.parse(text))
                .getMessage();
        assertTrue(message.contains("takes no argument"), message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"open:Shop", "open:a b", "action:Toggle", "action:x/y", "sound:Bad Key"})
    void identifiersAndKeysAreValidated(final String text) {
        assertThrows(IllegalArgumentException.class, () -> ClickAction.parse(text));
    }
}
