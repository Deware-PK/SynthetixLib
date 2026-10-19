package com.github.dewarepk.synthetixlib.command;

import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * The context of one command execution, handed to every {@link CommandHandler}. An interface, so products can mock
 * it in their own unit tests; the module creates one implementation per execution and never shares it.
 *
 * <pre>{@code
 * private void run(CommandCall ctx) {
 *     final Player player = ctx.player();
 *     if (player.getInventory().getItemInMainHand().isEmpty()) {
 *         throw ctx.fail(lang, Messages::holdAnItem);          // message sent, cooldown not used
 *     }
 *     ctx.reply(lang, Messages::listed, Placeholder.unparsed("price", Long.toString(ctx.get(price))));
 * }
 * }</pre>
 *
 * <p>{@code <label>} (the alias typed) and {@code <sender>} (the sender's name) are available in every reply, fail
 * and locale message. {@code reply} and {@code fail} are safe from any thread.
 */
public interface CommandCall {

    // --- who ran it -----------------------------------------------------------------------------------------

    /** @return console, player, command block, ... */
    CommandSender sender();

    /** @return {@code true} if the sender is a {@link Player} */
    boolean isPlayer();

    /** @return {@code true} if the sender is the server console */
    boolean isConsole();

    /**
     * @return the player who ran the command
     * @throws IllegalStateException if the sender is not a player (never on {@code playerOnly} nodes)
     */
    Player player();

    /** @return the player, for nodes that also allow the console */
    Optional<Player> playerIfPresent();

    /**
     * @param permission the permission node
     * @return whether the sender has it
     */
    boolean hasPermission(String permission);

    /** @return the sender's language: the player's client locale, else {@link Locale#US} */
    Locale locale();

    // --- where (vanilla /execute aware) ---------------------------------------------------------------------

    /** @return Paper's source, for advanced use */
    CommandSourceStack source();

    /** @return the entity of {@code /execute as <entity> ...}; usually the player; {@code null} for the console */
    @Nullable Entity executor();

    /** @return where the command runs: {@code /execute at / positioned ...}, else the sender's location or spawn */
    Location location();

    // --- what was typed -------------------------------------------------------------------------------------

    /**
     * The value of an argument of this node: what was typed, or the default of an {@code optional} argument.
     *
     * @param arg the argument handle
     * @param <T> the value type
     * @return the value, never {@code null}
     * @throws IllegalArgumentException if {@code arg} does not belong to the executed node
     */
    <T> T get(Arg<T> arg);

    /**
     * Like {@link #get(Arg)}, for arguments declared inline.
     *
     * @param name the argument name
     * @param type the (boxed) value type, e.g. {@code Long.class}
     * @param <T>  the value type
     * @return the value, never {@code null}
     * @throws IllegalArgumentException if the node has no such argument or its value is not a {@code type}
     */
    <T> T get(String name, Class<T> type);

    /**
     * @param arg the argument handle
     * @param <T> the value type
     * @return the value if it was typed (not the declared default)
     */
    <T> Optional<T> optional(Arg<T> arg);

    /**
     * @param arg      the argument handle
     * @param fallback used if the argument was not typed
     * @param <T>      the value type
     * @return the typed value, else {@code fallback}
     */
    <T> T getOrDefault(Arg<T> arg, T fallback);

    /**
     * @param arg the argument handle
     * @return whether it was typed
     */
    boolean has(Arg<?> arg);

    /** @return the alias actually typed, without {@code /} and {@code namespace:}: {@code ah} for {@code /ah bid 10} */
    String label();

    /** @return the full input without the leading {@code /}, e.g. {@code ah bid 10} */
    String input();

    // --- answering ------------------------------------------------------------------------------------------

    /**
     * Sends MiniMessage to the sender. Player-provided text belongs in {@code Placeholder.unparsed}, never in
     * the markup.
     *
     * @param miniMessage the markup; {@code <label>} and {@code <sender>} are available
     * @param resolvers   extra tags
     */
    void reply(String miniMessage, TagResolver... resolvers);

    /** @param message sent as is */
    void reply(Component message);

    /**
     * Sends a message from the product's lang files, in the sender's language.
     *
     * @param lang      the locale handle
     * @param message   picks the message, e.g. {@code Messages::bidPlaced}
     * @param resolvers extra tags
     * @param <M>       the record of messages
     */
    <M> void reply(LocaleHandle<M> lang, Function<M, String> message, TagResolver... resolvers);

    /**
     * Sends each line of a list message as its own chat message, in the sender's language.
     *
     * @param lang      the locale handle
     * @param lines     picks the list message
     * @param resolvers extra tags
     * @param <M>       the record of messages
     */
    <M> void replyLines(LocaleHandle<M> lang, Function<M, List<String>> lines, TagResolver... resolvers);

    /**
     * @param miniMessage the markup; {@code <label>} and {@code <sender>} are available
     * @param resolvers   extra tags
     */
    void actionBar(String miniMessage, TagResolver... resolvers);

    /**
     * Use as {@code throw ctx.fail("<red>No")}: the message is sent, the handler stops and Brigadier gets 0.
     *
     * @param miniMessage the markup; {@code <label>} and {@code <sender>} are available
     * @param resolvers   extra tags
     * @return the exception to throw
     */
    CommandFailure fail(String miniMessage, TagResolver... resolvers);

    /**
     * Like {@link #fail(String, TagResolver...)} with a message from the product's lang files.
     *
     * @param lang      the locale handle
     * @param message   picks the message
     * @param resolvers extra tags
     * @param <M>       the record of messages
     * @return the exception to throw
     */
    <M> CommandFailure fail(LocaleHandle<M> lang, Function<M, String> message, TagResolver... resolvers);

    // --- cooldown of this node ------------------------------------------------------------------------------

    /**
     * @return how long this player must still wait for this node; {@link Duration#ZERO} if there is no cooldown,
     *         none is left, the sender is not a player or has the bypass permission
     */
    Duration cooldownLeft();

    /**
     * Takes the sender off cooldown for this node, e.g. to refund it after an external failure. Called before the
     * handler returns it also stops the cooldown from starting; called later (from {@link #runOnSender}) it removes
     * the one that started.
     */
    void resetCooldown();

    /** This run succeeds but does not start the cooldown. Only meaningful before the handler returns. */
    void skipCooldown();

    // --- threads --------------------------------------------------------------------------------------------

    /** @return whether this is the handler of {@code executesAsync}, running off the server threads */
    boolean isAsync();

    /**
     * Runs {@code task} on the sender's thread: the entity scheduler for a player (dropped silently if the player is
     * gone by then), the global one otherwise. Does nothing after the library is closed.
     *
     * @param task what to run
     */
    void runOnSender(Runnable task);

    /** @return the library instance, for the scheduler, text and so on */
    SynthetixLib lib();
}
