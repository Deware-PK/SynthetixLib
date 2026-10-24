package com.github.dewarepk.synthetixlib.dialog;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** Where the {@code <lang:key>} tag of dialog texts reads its messages: one language file per viewer's locale. */
interface DialogMessages {

    /**
     * @param viewer the player whose language to use; {@code null} for the default language (previews)
     * @param key    the key after {@code lang:}
     * @param nested placeholders usable inside the message
     * @return the parsed message, {@code null} if there is no such key
     */
    @Nullable Component get(@Nullable Player viewer, String key, TagResolver nested);
}
