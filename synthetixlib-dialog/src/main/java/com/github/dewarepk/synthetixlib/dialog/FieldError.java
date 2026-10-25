package com.github.dewarepk.synthetixlib.dialog;

import java.util.Objects;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * A problem that code found with an answer: returned by the validator of {@link DialogBuilder#validate}. The dialog is
 * shown again with the line above the body, the typed values kept, and the handler is not called.
 *
 * <p>The text is MiniMessage with the placeholders of the dialog, {@code <lang:key>} for a language message,
 * {@code <min> <max> <value>} of the input, and the resolvers given here.
 *
 * <pre>{@code
 * FieldError.of("price", "<red>At most <limit> coins", Placeholder.unparsed("limit", "500"))
 * FieldError.form("<lang:sell.no-room>")
 * }</pre>
 *
 * @param key      the input the problem belongs to; empty if it is not tied to an input
 * @param markup   the MiniMessage line shown above the body
 * @param resolver extra tags of the line
 */
public record FieldError(String key, String markup, TagResolver resolver) {

    public FieldError {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(markup, "markup");
        Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * @param key       the key of the input in the file
     * @param markup    MiniMessage, e.g. {@code '<red>Too high'} or {@code '<lang:sell.too-high>'}
     * @param resolvers extra tags the line uses
     * @return an error of that input
     */
    public static FieldError of(final String key, final String markup, final TagResolver... resolvers) {
        Objects.requireNonNull(key, "key");
        return new FieldError(key, markup, TagResolver.resolver(resolvers));
    }

    /**
     * @param markup    MiniMessage, e.g. {@code '<red>You have no room'}
     * @param resolvers extra tags the line uses
     * @return an error that belongs to the whole form, not to one input
     */
    public static FieldError form(final String markup, final TagResolver... resolvers) {
        return new FieldError("", markup, TagResolver.resolver(resolvers));
    }
}
