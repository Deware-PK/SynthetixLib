package com.github.dewarepk.synthetixlib.command;

import com.github.dewarepk.synthetixlib.config.TimeUnits;
import com.github.dewarepk.synthetixlib.config.annotation.Comment;

/**
 * The messages the command module sends on its own. Put it in your lang record so the keys appear in
 * {@code lang/<tag>.yml} under {@code commands:}, then hand it to {@link CommandService#messages}.
 *
 * <pre>{@code
 * public record Messages(CommandMessages commands, String bidPlaced) {
 *     static Messages defaults() { return new Messages(CommandMessages.defaults(), "<green>Bid placed"); }
 * }
 * commandService.messages(lang, Messages::commands);
 * }</pre>
 *
 * <p>Every message may use {@code <label>} (the alias typed) and {@code <sender>}; an empty message is not sent.
 * The exception is {@code invalidValue}: it is raised while Brigadier parses the command, before the alias is known,
 * so it has {@code <sender>}, {@code <input>} and {@code <reason>} only. Known limit: the {@code <reason>} texts the
 * module builds itself (for example {@code expected one of: ...} or the duration syntax hint) are English and not
 * translatable.
 *
 * @param playerOnly     a non-player ran a player-only node
 * @param cooldown       the node is on cooldown; also {@code <time>}
 * @param busy           the same player started this {@code executesAsync} node again before its handler returned
 * @param playerNotFound a player selector matched nobody; also {@code <input>}
 * @param invalidValue   an argument could not be converted; {@code <input>} and {@code <reason>}, no {@code <label>}
 * @param error          the handler threw an unexpected exception (details go to the console)
 * @param helpHeader     first line of the help; also {@code <command>} (for example {@code auction admin})
 * @param helpLine       one line per subcommand the sender may use; also {@code <command>}, {@code <usage>} and
 *                       {@code <description>} (empty when the node has none)
 * @param disabled       reserved for a disabled command; empty means silent
 * @param timeUnits      the unit words of {@code <time>} in {@code cooldown} (for example {@code 1m 12s})
 */
public record CommandMessages(
        @Comment("Placeholders: <label> <sender>") String playerOnly,
        @Comment("Placeholders: <label> <sender> <time>") String cooldown,
        @Comment("Placeholders: <label> <sender>") String busy,
        @Comment("Placeholders: <label> <sender> <input>") String playerNotFound,
        @Comment("Placeholders: <sender> <input> <reason>") String invalidValue,
        @Comment("Placeholders: <label> <sender>") String error,
        @Comment("First line of /<command> help. Placeholders: <label> <sender> <command>") String helpHeader,
        @Comment("One line per subcommand. Placeholders: <label> <sender> <command> <usage> <description>")
        String helpLine,
        @Comment("Empty = send nothing") String disabled,
        @Comment("Unit words of <time> in the cooldown message, e.g. 1m 12s") TimeUnits timeUnits) {

    /** @return the English defaults */
    public static CommandMessages defaults() {
        return new CommandMessages(
                "<red>Only players can use this.",
                "<red>Wait <time> before using /<label> again.",
                "<red>Please wait, /<label> is still running.",
                "<red>No player found: <input>",
                "<red><input> is not valid: <reason>",
                "<red>Something went wrong, see the console.",
                "<yellow>Commands of /<command>:",
                "<yellow>/<command> <usage> <gray>- <description>",
                "",
                TimeUnits.defaults());
    }
}
