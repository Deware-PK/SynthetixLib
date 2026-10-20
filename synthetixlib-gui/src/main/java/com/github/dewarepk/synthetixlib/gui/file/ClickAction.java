package com.github.dewarepk.synthetixlib.gui.file;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.bukkit.NamespacedKey;

/**
 * One step a click runs, written as text in a menu file: a bare name ({@code close}) or {@code name:argument}
 * ({@code open:my-listings}). Several run in order.
 *
 * <pre>{@code
 * click: [close, 'command:auction sell', 'sound:ui.button.click', 'action:toggle-notify']
 *
 * ClickAction action = ClickAction.parse("open:my-listings");   // new ClickAction.Open("my-listings")
 * }</pre>
 *
 * <p>Built-in names: {@code close back refresh page-next page-previous open:<menu> command:<cmd> console:<cmd>
 * message:<text> sound:<key> action:<name>}.
 */
public sealed interface ClickAction {

    /** Pattern of menu ids, action names, list ids, state ids and placeholder names. */
    String NAME_PATTERN = "[a-z0-9_-]{1,64}";

    /** {@code close}: closes the menu. */
    record Close() implements ClickAction {
    }

    /** {@code back}: opens the previous menu of the viewer. */
    record Back() implements ClickAction {
    }

    /** {@code refresh}: renders the view again. */
    record Refresh() implements ClickAction {
    }

    /** {@code page-next}: shows the next page of the lists. */
    record PageNext() implements ClickAction {
    }

    /** {@code page-previous}: shows the previous page of the lists. */
    record PagePrevious() implements ClickAction {
    }

    /** {@code open:<menu>}: opens another menu by its id. */
    record Open(String menu) implements ClickAction {
        public Open {
            Objects.requireNonNull(menu, "menu");
        }
    }

    /** {@code command:<cmd>}: runs a command as the player (without the leading slash). */
    record Command(String command) implements ClickAction {
        public Command {
            Objects.requireNonNull(command, "command");
        }
    }

    /** {@code console:<cmd>}: runs a command as the console (without the leading slash). */
    record Console(String command) implements ClickAction {
        public Console {
            Objects.requireNonNull(command, "command");
        }
    }

    /** {@code message:<text>}: sends MiniMessage text to the player. */
    record Message(String text) implements ClickAction {
        public Message {
            Objects.requireNonNull(text, "text");
        }
    }

    /** {@code sound:<key>}: plays a sound to the player. */
    record PlaySound(NamespacedKey sound) implements ClickAction {
        public PlaySound {
            Objects.requireNonNull(sound, "sound");
        }
    }

    /** {@code action:<name>}: runs the handler that code registered under {@code name}. */
    record Run(String name) implements ClickAction {
        public Run {
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * Reads the text form.
     *
     * @param raw e.g. {@code close} or {@code open:my-listings}; the name is not case sensitive, the argument is
     *            kept as written (minus surrounding spaces)
     * @return the action
     * @throws IllegalArgumentException with a message fit for a config error if the text is empty, the name is not
     *                                  known, an argument is missing, or one is given to an action that takes none
     */
    static ClickAction parse(final String raw) {
        Objects.requireNonNull(raw, "raw");
        final String text = raw.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("the action is empty");
        }
        final int colon = text.indexOf(':');
        final String name = (colon < 0 ? text : text.substring(0, colon)).strip().toLowerCase(Locale.ROOT);
        final String argument = colon < 0 ? null : text.substring(colon + 1).strip();
        return switch (name) {
            case "close" -> noArgument(name, argument, new Close());
            case "back" -> noArgument(name, argument, new Back());
            case "refresh" -> noArgument(name, argument, new Refresh());
            case "page-next" -> noArgument(name, argument, new PageNext());
            case "page-previous" -> noArgument(name, argument, new PagePrevious());
            case "open" -> new Open(identifier(name, "menu id", argument));
            case "command" -> new Command(withoutSlash(required(name, "a command", argument)));
            case "console" -> new Console(withoutSlash(required(name, "a command", argument)));
            case "message" -> new Message(required(name, "text", argument));
            case "sound" -> new PlaySound(soundKey(required(name, "a sound key", argument)));
            case "action" -> new Run(identifier(name, "action name", argument));
            default -> throw new IllegalArgumentException("unknown action '" + (name.isEmpty() ? text : name)
                    + "' (use close, back, refresh, page-next, page-previous, open:<menu>, command:<cmd>, "
                    + "console:<cmd>, message:<text>, sound:<key> or action:<name>)");
        };
    }

    /** @return the text form that {@link #parse} reads back to an equal action */
    default String format() {
        return switch (this) {
            case Close ignored -> "close";
            case Back ignored -> "back";
            case Refresh ignored -> "refresh";
            case PageNext ignored -> "page-next";
            case PagePrevious ignored -> "page-previous";
            case Open open -> "open:" + open.menu();
            case Command command -> "command:" + command.command();
            case Console console -> "console:" + console.command();
            case Message message -> "message:" + message.text();
            case PlaySound sound -> "sound:" + sound.sound();
            case Run run -> "action:" + run.name();
        };
    }

    private static ClickAction noArgument(final String name, final String argument, final ClickAction action) {
        if (argument != null) {
            throw new IllegalArgumentException("'" + name + "' takes no argument (was '" + name + ":" + argument
                    + "')");
        }
        return action;
    }

    private static String required(final String name, final String what, final String argument) {
        if (argument == null || argument.isEmpty()) {
            throw new IllegalArgumentException("'" + name + "' needs " + what + ", e.g. " + name + ":...");
        }
        return argument;
    }

    private static String identifier(final String name, final String what, final String argument) {
        final String value = required(name, "a " + what, argument);
        if (!Pattern.matches(NAME_PATTERN, value)) {
            throw new IllegalArgumentException("'" + value + "' is not a " + what
                    + " (lower case letters, digits, - and _, at most 64)");
        }
        return value;
    }

    private static String withoutSlash(final String command) {
        final String stripped = command.startsWith("/") ? command.substring(1).strip() : command;
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException("the command is empty");
        }
        return stripped;
    }

    private static NamespacedKey soundKey(final String text) {
        final NamespacedKey key = NamespacedKey.fromString(text);
        if (key == null) {
            throw new IllegalArgumentException("invalid sound key '" + text
                    + "' (expected namespace:key, using a-z 0-9 _ - . /)");
        }
        return key;
    }
}
