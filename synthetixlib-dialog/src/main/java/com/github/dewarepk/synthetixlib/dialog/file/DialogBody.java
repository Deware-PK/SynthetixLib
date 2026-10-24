package com.github.dewarepk.synthetixlib.dialog.file;

import java.util.Objects;
import java.util.Optional;

/**
 * One entry of the body of a dialog, after parsing: a text or an item with a description.
 *
 * <pre>{@code
 * body:
 *   - message: '<gray>Are you sure?'
 *   - item: { slot: hand }
 * }</pre>
 */
public sealed interface DialogBody {

    /**
     * A text block.
     *
     * @param text  MiniMessage text
     * @param width width in pixels, 1 to 1024; the client default (200) if absent
     */
    record Message(String text, Optional<Integer> width) implements DialogBody {
        public Message {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(width, "width");
        }
    }

    /**
     * An item with an optional description.
     *
     * @param item            where the item comes from
     * @param description     MiniMessage text next to the item
     * @param showDecorations amount and durability bar over the item
     * @param showTooltip     item tooltip on hover
     * @param width           1 to 256; the client default (16) if absent
     * @param height          1 to 256; the client default (16) if absent
     */
    record Item(ItemSpec item, Optional<String> description, boolean showDecorations, boolean showTooltip,
                Optional<Integer> width, Optional<Integer> height) implements DialogBody {
        public Item {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(width, "width");
            Objects.requireNonNull(height, "height");
        }
    }
}
