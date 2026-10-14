package com.github.dewarepk.synthetixlib.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.junit.jupiter.api.Test;

class TextServiceTest {

    private final TextService text = TextService.create();

    @Test
    void placeholders() {
        assertEquals("Lot 12", text.plain(text.parse("Lot <id>", Placeholder.unparsed("id", "12"))));
        assertEquals("a b", text.plain(text.parse("<p>", Placeholder.parsed("p", "a <red>b"))));
        assertEquals("hi Bob", text.plain(text.parse("hi <n>", Placeholder.component("n", Component.text("Bob")))));
    }

    @Test
    void withDoesNotMutateOriginal() {
        final TextService derived = text.with(Placeholder.unparsed("x", "1"));
        assertNotSame(text, derived);
        assertEquals("1", derived.plain(derived.parse("<x>")));
        assertEquals("<x>", text.plain(text.parse("<x>")));
    }

    @Test
    void escapePreventsTagInjection() {
        assertEquals("<red>x", text.plain(text.parse(text.escape("<red>x"))));
    }

    @Test
    void parseItemItalicDefaultFalse() {
        assertEquals(TextDecoration.State.FALSE, text.parseItem("x").decoration(TextDecoration.ITALIC));
        assertEquals(TextDecoration.State.TRUE, text.parseItem("<italic>x").decoration(TextDecoration.ITALIC));
    }

    @Test
    void plain() {
        assertEquals("ab", text.plain(text.parse("<gold>a<b>b")));
        assertTrue(text.plain(Component.empty()).isEmpty());
    }
}
