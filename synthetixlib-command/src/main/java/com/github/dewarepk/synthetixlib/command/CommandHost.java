package com.github.dewarepk.synthetixlib.command;

import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.core.text.TextService;
import com.mojang.brigadier.Message;
import java.util.Locale;
import java.util.function.Function;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.Nullable;

/** What executions need from their surroundings; a seam so the command tree can be tested without a server. */
interface CommandHost {

    boolean isClosed();

    Logger logger();

    TextService text();

    /** @return the library instance; unit-test hosts have none */
    SynthetixLib lib();

    /** @return the locale of senders that have none (the console); {@code Locale.US} without a lang handle */
    Locale defaultLocale();

    /** @return who is on cooldown; one per service, never shared between plugins */
    Cooldowns cooldowns();

    /** Runs {@code task} on the sender's thread; does nothing if closed. */
    void runOnSender(CommandSender sender, Runnable task);

    /**
     * Runs {@code task} on an async thread.
     *
     * @throws IllegalStateException if closed: the task will not run
     */
    void runAsync(Runnable task);

    /**
     * @param sender the receiver, {@code null} if unknown (then the default language is used)
     * @return one of the module's own messages in the sender's language; empty if it is disabled
     */
    Component render(@Nullable CommandSender sender, Function<CommandMessages, String> message,
                     TagResolver resolvers);

    /** @return {@code message} as the Brigadier message of a {@code CommandSyntaxException} */
    Message toMessage(Component message);

    /** @return the message of a {@code CommandSyntaxException} as a component */
    Component fromMessage(Message message);
}
