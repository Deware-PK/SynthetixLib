package com.github.dewarepk.synthetixexample.demo;

import com.github.dewarepk.synthetixlib.config.annotation.Comment;
import com.github.dewarepk.synthetixlib.config.annotation.Key;

/** The plugin-wide settings, {@code settings.yml}. */
@Comment("SynthetixExample settings")
public record Settings(@Comment("Give every joining player a demo ticket") boolean welcomeTicket,
                       @Key("default-airdrop")
                       @Comment("New airdrops start from this one (the name is replaced by the id)")
                       Airdrop defaultAirdrop) {

    public static Settings defaults() {
        return new Settings(true, Airdrop.standard());
    }
}
