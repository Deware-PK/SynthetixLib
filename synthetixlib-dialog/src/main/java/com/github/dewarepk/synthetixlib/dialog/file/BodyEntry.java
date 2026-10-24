package com.github.dewarepk.synthetixlib.dialog.file;

import java.util.Objects;
import java.util.Optional;

/**
 * One entry of {@code body:} as written in the file, before it is told apart: a {@code message} or an {@code item}.
 * The parser turns it into a {@link DialogBody}; not meant for plugins.
 *
 * <pre>{@code
 * - message: '<gray>Starting price for <white><item-name></white>'
 *   width: 250
 * - item: { slot: hand }
 *   description: '<gray>The item you are selling'
 * }</pre>
 *
 * @param message        text of a message entry (MiniMessage)
 * @param width          width in pixels
 * @param item           the item of an item entry
 * @param description    text next to the item
 * @param showDecorations draws the amount and durability bar over the item, default true
 * @param showTooltip    shows the item tooltip on hover, default true
 * @param height         height of an item entry in pixels
 */
public record BodyEntry(
        Optional<String> message,
        Optional<Integer> width,
        Optional<ItemSpec> item,
        Optional<String> description,
        Optional<Boolean> showDecorations,
        Optional<Boolean> showTooltip,
        Optional<Integer> height) {

    public BodyEntry {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(showDecorations, "showDecorations");
        Objects.requireNonNull(showTooltip, "showTooltip");
        Objects.requireNonNull(height, "height");
    }
}
