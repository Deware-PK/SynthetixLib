package com.github.dewarepk.synthetixexample.demo;

import com.github.dewarepk.synthetixlib.config.annotation.Comment;
import java.util.List;

/** Every message of the plugin; one file per language in {@code lang/}. */
public record Messages(@Comment("Placeholders: <name>") String welcome,
                       @Comment("Placeholders: <count>, <ids>") String airdropsLoaded,
                       @Comment("Placeholders: <id>") String airdropCreated,
                       @Comment("One line per entry") List<String> help) {

    public static Messages defaults() {
        return new Messages(
                "<green>Welcome, <name>!",
                "<gray>Loaded <count> airdrop(s): <ids>",
                "<green>Created the airdrop <id>.",
                List.of("<gold>SynthetixExample", "<gray>Edit plugins/SynthetixExample/airdrops/*.yml"));
    }
}
