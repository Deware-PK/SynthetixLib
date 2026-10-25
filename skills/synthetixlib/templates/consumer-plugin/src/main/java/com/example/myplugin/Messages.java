package com.example.myplugin;

import com.github.dewarepk.synthetixlib.command.CommandMessages;
import com.github.dewarepk.synthetixlib.config.annotation.Comment;
import java.util.Map;

/** Every text the player sees; one file per language in {@code lang/} (the defaults below become en_US.yml). */
public record Messages(@Comment("Messages of the command module itself") CommandMessages commands,
                       @Comment("Placeholders: <name>") String welcome,
                       @Comment("Placeholders: <name>") String greeted,
                       String reloaded,
                       @Comment("Placeholders: <errors>") String reloadFailed,
                       @Comment("Texts of the menus (menus/*.yml), used there as <lang:key>") Map<String, String> gui,
                       @Comment("Texts of the dialogs (dialogs/*.yml), used there as <lang:key>") Map<String, String> dialog) {

    public static Messages defaults() {
        return new Messages(
                CommandMessages.defaults(),
                "<green>Welcome, <name>!",
                "<green>Hello, <name>!",
                "<green>Reloaded config and messages.",
                "<red>Reload found errors: <errors>",
                Map.ofEntries(
                        Map.entry("main-title", "<dark_gray>MyPlugin"),
                        Map.entry("main-greet", "<yellow>Say hello (dialog)"),
                        Map.entry("main-close", "<red>Close")),
                Map.ofEntries(
                        Map.entry("greet-title", "<gold>Say hello"),
                        Map.entry("greet-name", "Your name"),
                        Map.entry("greet-yes", "<green>Send"),
                        Map.entry("greet-no", "<red>Cancel")));
    }
}
