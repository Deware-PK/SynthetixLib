package com.github.dewarepk.synthetixlib.core.text;

import java.util.Objects;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * MiniMessage helper. Immutable and thread-safe; {@link #with(TagResolver...)} returns a new instance.
 *
 * <pre>{@code
 * Component c = text.parse("<gold>Lot <id>", Placeholder.unparsed("id", "12"));
 * text.send(player, "<green>Bid placed!");
 * TextService auction = text.with(Placeholder.parsed("prefix", "<gold>[Auction]</gold>"));
 * }</pre>
 *
 * <p><b>Player-provided strings must go through {@code Placeholder.unparsed} or {@link #escape}</b>,
 * never straight into the markup.
 */
public final class TextService {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final TagResolver base;

    private TextService(final TagResolver base) {
        this.base = base;
    }

    /**
     * Creates a service without extra tags. Normally obtained from {@code SynthetixLib#text()}.
     *
     * @return a new service
     */
    public static TextService create() {
        return new TextService(TagResolver.empty());
    }

    /**
     * Parses MiniMessage markup. Never throws on bad markup; unknown tags render literally.
     *
     * @param input     the markup
     * @param resolvers extra tags for this call only
     * @return the component
     */
    public Component parse(final String input, final TagResolver... resolvers) {
        Objects.requireNonNull(input, "input");
        return MINI.deserialize(input, TagResolver.resolver(base, TagResolver.resolver(resolvers)));
    }

    /**
     * Like {@link #parse} but italic is {@code false} unless the markup sets it (for item names/lore).
     *
     * @param input     the markup
     * @param resolvers extra tags for this call only
     * @return the component
     */
    public Component parseItem(final String input, final TagResolver... resolvers) {
        return parse(input, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /**
     * Parses and sends to any audience.
     *
     * @param audience  the receiver
     * @param input     the markup
     * @param resolvers extra tags for this call only
     */
    public void send(final Audience audience, final String input, final TagResolver... resolvers) {
        Objects.requireNonNull(audience, "audience").sendMessage(parse(input, resolvers));
    }

    /**
     * Derives a service with additional tags always available.
     *
     * @param resolvers the extra tags
     * @return a new service; this instance is unchanged
     */
    public TextService with(final TagResolver... resolvers) {
        return new TextService(TagResolver.resolver(base, TagResolver.resolver(resolvers)));
    }

    /**
     * Plain text of a component (for logs / DB).
     *
     * @param component the component
     * @return text without formatting
     */
    public String plain(final Component component) {
        return PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(component, "component"));
    }

    /**
     * Escapes MiniMessage tags so user input is never parsed as markup.
     *
     * @param input the raw text
     * @return the escaped text
     */
    public String escape(final String input) {
        return MINI.escapeTags(Objects.requireNonNull(input, "input"));
    }
}
