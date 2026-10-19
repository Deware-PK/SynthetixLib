package com.github.dewarepk.synthetixexample.demo;

import com.github.dewarepk.synthetixlib.command.CommandMessages;
import com.github.dewarepk.synthetixlib.config.annotation.Comment;
import java.util.List;

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
                       @Comment("One line per entry") List<String> help) {

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
                List.of("<gold>SynthetixExample", "<gray>Edit plugins/SynthetixExample/airdrops/*.yml"));
    }
}
