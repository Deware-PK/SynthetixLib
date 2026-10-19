package com.github.dewarepk.synthetixlib.command;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Thrown by {@code throw ctx.fail(...)} to stop a handler: the message is sent to the sender, the cooldown is not
 * started and Brigadier gets 0. Unchecked, and without a stack trace (it is control flow, not an error).
 */
public final class CommandFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Component message;

    /**
     * @param message what the sender sees; an empty component means "stop silently"
     */
    public CommandFailure(final Component message) {
        super(PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(message, "message")),
                null, false, false);
        this.message = message;
    }

    /** @return what the sender sees */
    public Component message() {
        return message;
    }
}
