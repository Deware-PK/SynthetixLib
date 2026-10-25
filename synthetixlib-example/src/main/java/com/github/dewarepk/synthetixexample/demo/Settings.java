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
                       DialogSettings dialog,
                       @Key("sell-min-price")
                       @Comment("The sell dialog: the lowest price a player may type")
                       long sellMinPrice,
                       @Key("sell-max-price")
                       @Comment("The sell dialog: the highest price a player may type")
                       long sellMaxPrice,
                       @Key("give-wait")
                       @Comment("/example give: how long a player waits between uses; a reload applies a change")
                       Duration giveWait) {

    public static Settings defaults() {
        return new Settings(true, Airdrop.standard(),
                DatabaseSettings.sqlite("example.db").withTablePrefix("ex_"),
                new DialogSettings(Duration.ofMinutes(5), true), 1, 1_000_000, Duration.ofSeconds(10));
    }
}
// @end
