package com.github.dewarepk.synthetixlib.dialog.file;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.bukkit.NamespacedKey;

/**
 * One step a button click runs, written as text in a dialog file: a bare name ({@code close}) or
 * {@code name:argument} ({@code open:confirm}). Several run in order.
 *
 * <pre>{@code
 * click: [close, 'command:auction sell', 'url:https://example.com/help', 'action:sell']
 *
 * ClickAction action = ClickAction.parse("open:confirm");   // new ClickAction.Open("confirm")
 * }</pre>
 *
 * <p>Built-in names: {@code close back open:<dialog> menu:<gui menu> command:<cmd> console:<cmd> message:<text>
 * url:<https://...> copy:<text> sound:<key> action:<name>}. {@code menu:} always parses; it only works when the
 * dialog service is linked to a gui service.
 */
public sealed interface ClickAction {

    /** Pattern of dialog ids, menu ids, action names, placeholder names and input keys. */
    String NAME_PATTERN = "[a-z0-9_-]{1,64}";

    /** {@code close}: closes the dialog. */
    record Close() implements ClickAction {
    }

    /** {@code back}: opens the previous dialog of the viewer. */
    record Back() implements ClickAction {
    }

    /** {@code open:<dialog>}: opens another dialog by its id. */
    record Open(String dialog) implements ClickAction {
        public Open {
            Objects.requireNonNull(dialog, "dialog");
        }
    }

    /** {@code menu:<id>}: opens a menu of the linked gui service. */
    record Menu(String menu) implements ClickAction {
        public Menu {
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

    /** {@code url:<https://...>}: asks the client to open a web page; only {@code http} and {@code https}. */
    record Url(URI url) implements ClickAction {
        public Url {
            Objects.requireNonNull(url, "url");
        }
    }

    /** {@code copy:<text>}: copies the text to the clipboard of the player. */
    record Copy(String text) implements ClickAction {
        public Copy {
            Objects.requireNonNull(text, "text");
        }
    }

    /** {@code sound:<key>}: plays a sound to the player. */
    record PlaySound(NamespacedKey sound) implements ClickAction {
        public PlaySound {
            Objects.requireNonNull(sound, "sound");
        }
    }

    /** {@code action:<name>}: runs the handler that code registered under {@code name} (also a {@code submit}). */
    record Run(String name) implements ClickAction {
        public Run {
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * Reads the text form.
     *
     * @param raw e.g. {@code close} or {@code open:confirm}; the name is not case sensitive, the argument is kept as
     *            written (minus surrounding spaces)
     * @return the action
     * @throws IllegalArgumentException with a message fit for a config error if the text is empty, the name is not
     *                                  known, an argument is missing, one is given to an action that takes none, or a
     *                                  {@code url:} is not an http(s) address
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
            case "open" -> new Open(identifier(name, "dialog id", argument));
            case "menu" -> new Menu(identifier(name, "menu id", argument));
            case "command" -> new Command(withoutSlash(required(name, "a command", argument)));
            case "console" -> new Console(withoutSlash(required(name, "a command", argument)));
            case "message" -> new Message(required(name, "text", argument));
            case "url" -> new Url(webAddress(required(name, "an address", argument)));
            case "copy" -> new Copy(required(name, "text", argument));
            case "sound" -> new PlaySound(soundKey(required(name, "a sound key", argument)));
            case "action" -> new Run(identifier(name, "action name", argument));
            default -> throw new IllegalArgumentException("unknown action '" + (name.isEmpty() ? text : name)
                    + "' (use close, back, open:<dialog>, menu:<id>, command:<cmd>, console:<cmd>, message:<text>, "
                    + "url:<https://...>, copy:<text>, sound:<key> or action:<name>)");
        };
    }

    /** @return the text form that {@link #parse} reads back to an equal action */
    default String format() {
        return switch (this) {
            case Close ignored -> "close";
            case Back ignored -> "back";
            case Open open -> "open:" + open.dialog();
            case Menu menu -> "menu:" + menu.menu();
            case Command command -> "command:" + command.command();
            case Console console -> "console:" + console.command();
            case Message message -> "message:" + message.text();
            case Url url -> "url:" + url.url();
            case Copy copy -> "copy:" + copy.text();
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

    private static URI webAddress(final String text) {
        final URI uri;
        try {
            uri = new URI(text);
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("invalid address '" + text + "'");
        }
        final String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null) {
            throw new IllegalArgumentException("'" + text + "' is not an http or https address");
        }
        return uri;
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
