package com.github.dewarepk.synthetixexample.demo;

import com.github.dewarepk.synthetixlib.command.CommandMessages;
import com.github.dewarepk.synthetixlib.config.annotation.Comment;
import java.util.List;
import java.util.Map;

/** Every message of the plugin; one file per language in {@code lang/}. */
public record Messages(@Comment("Messages of the command module itself") CommandMessages commands,
                       @Comment("Placeholders: <name>") String welcome,
                       @Comment("Placeholders: <count>, <ids>") String airdropsLoaded,
                       @Comment("Placeholders: <id>") String airdropCreated,
                       @Comment("Placeholders: <id>") String airdropExists,
                       @Comment("Placeholders: <id>") String airdropInvalidId,
                       @Comment("Placeholders: <id>") String airdropNotFound,
                       @Comment("Placeholders: <id>") String airdropDeleted,
                       @Comment("Placeholders: <count>, <ids>") String airdropList,
                       @Comment("Placeholders: <amount>, <material>, <player>") String gave,
                       String reloaded,
                       @Comment("Placeholders: <errors>") String reloadFailed,
                       @Comment("One line per entry") List<String> help,
                       @Comment("Placeholders: <count>") String inputConfirmed,
                       String inputEmpty,
                       @Comment("Texts of the menus (menus/*.yml), used there as <lang:key>") Map<String, String> gui) {

    public static Messages defaults() {
        return new Messages(
                CommandMessages.defaults(),
                "<green>Welcome, <name>!",
                "<gray>Loaded <count> airdrop(s): <ids>",
                "<green>Created the airdrop <id>.",
                "<red>The airdrop <id> exists already.",
                "<red>\"<id>\" is not a valid id (a-z, 0-9, _ and -).",
                "<red>There is no airdrop <id>.",
                "<green>Deleted the airdrop <id>.",
                "<gray>Airdrops (<count>): <ids>",
                "<green>Gave <amount> x <material> to <player>.",
                "<green>Reloaded settings, airdrops and messages.",
                "<red>Reload found errors: <errors>",
                List.of("<gold>SynthetixExample", "<gray>Edit plugins/SynthetixExample/airdrops/*.yml"),
                "<green>Received <count> item(s); they are in your inventory again.",
                "<red>Put an item in the slot first.",
                Map.ofEntries(
                        Map.entry("demo-title", "<dark_gray>Example menu"),
                        Map.entry("demo-balance", "<gold>XP levels:"),
                        Map.entry("demo-clock", "<aqua>Server time:"),
                        Map.entry("demo-notify-on", "<green>Notifications ON"),
                        Map.entry("demo-notify-off", "<gray>Notifications OFF"),
                        Map.entry("demo-toggle-hint", "<dark_gray>Click to switch"),
                        Map.entry("demo-previous", "<yellow>Previous page"),
                        Map.entry("demo-next", "<yellow>Next page"),
                        Map.entry("demo-page", "<gray>Page <page> of <pages>"),
                        Map.entry("demo-input", "<aqua>Drop an item in the slot on the left"),
                        Map.entry("demo-confirm", "<green>Confirm"),
                        Map.entry("demo-close", "<red>Close"),
                        Map.entry("demo-back", "<yellow>Back"),
                        Map.entry("airdrop-title", "<dark_gray>Airdrop <name>"),
                        Map.entry("airdrop-interval", "<gray>Every <interval>"),
                        Map.entry("airdrop-players", "<gray>Needs <players> player(s) online"),
                        Map.entry("airdrop-rewards", "<gray>Rewards: <rewards>"),
                        Map.entry("airdrop-open", "<gray>Click to open")));
    }
}
