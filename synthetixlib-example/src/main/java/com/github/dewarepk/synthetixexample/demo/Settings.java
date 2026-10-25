package com.github.dewarepk.synthetixexample.demo;

import com.github.dewarepk.synthetixlib.config.annotation.Comment;
import com.github.dewarepk.synthetixlib.config.annotation.Key;
import com.github.dewarepk.synthetixlib.database.DatabaseSettings;
import com.github.dewarepk.synthetixlib.dialog.DialogSettings;
import java.time.Duration;

/** The plugin-wide settings, {@code settings.yml}. */
// @snippet:config-settings-record
@Comment("SynthetixExample settings")
public record Settings(@Comment("Give every joining player a demo ticket") boolean welcomeTicket,
                       @Key("default-airdrop")
                       @Comment("New airdrops start from this one (the name is replaced by the id)")
                       Airdrop defaultAirdrop,
                       @Comment("Where the airdrop claims are stored (sqlite or mysql); a restart applies a change")
                       DatabaseSettings database,
                       @Comment("Dialogs: how long their buttons work; debug logs the thread of the first click at INFO")
                       DialogSettings dialog) {

    public static Settings defaults() {
        return new Settings(true, Airdrop.standard(),
                DatabaseSettings.sqlite("example.db").withTablePrefix("ex_"),
                new DialogSettings(Duration.ofMinutes(5), true));
    }
}
// @end
