package com.github.dewarepk.synthetixlib.command;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Who is on cooldown, per player and command; from {@link CommandService#cooldowns()}. Safe from any thread.
 *
 * <p>A {@code commandPath} is the command as written in code, space separated, from the root name:
 * {@code "kit"} or {@code "kit reset"}. Aliases are never part of it: {@code /k reset} is {@code "kit reset"} too.
 *
 * <pre>{@code
 * // /kit admin reset <player>
 * commands.cooldowns().reset(target.getUniqueId(), "kit");
 * Duration wait = commands.cooldowns().remaining(player, "kit");
 * }</pre>
 *
 * <p>Cooldowns live in memory: a restart clears them, a quit clears that player's.
 */
public final class CommandCooldowns {

    private final Cooldowns cooldowns;

    CommandCooldowns(final Cooldowns cooldowns) {
        this.cooldowns = cooldowns;
    }

    /**
     * @param player      the player
     * @param commandPath the command path as written in code, e.g. {@code "kit"} or {@code "kit reset"}
     * @return how long {@code player} must still wait; {@link Duration#ZERO} if not at all
     */
    public Duration remaining(final Player player, final String commandPath) {
        return remaining(Objects.requireNonNull(player, "player").getUniqueId(), commandPath);
    }

    /**
     * @param player      the player's id
     * @param commandPath the command path as written in code
     * @return how long the player must still wait; {@link Duration#ZERO} if not at all
     */
    public Duration remaining(final UUID player, final String commandPath) {
        return cooldowns.left(Objects.requireNonNull(player, "player"),
                Objects.requireNonNull(commandPath, "commandPath"));
    }

    /**
     * Lets the player use one command again right away. Does nothing if they are not on cooldown.
     *
     * @param player      the player's id
     * @param commandPath the command path as written in code
     */
    public void reset(final UUID player, final String commandPath) {
        cooldowns.reset(Objects.requireNonNull(player, "player"),
                Objects.requireNonNull(commandPath, "commandPath"));
    }

    /**
     * Lets the player use every command again right away.
     *
     * @param player the player's id
     */
    public void reset(final UUID player) {
        cooldowns.clear(Objects.requireNonNull(player, "player"));
    }
}
