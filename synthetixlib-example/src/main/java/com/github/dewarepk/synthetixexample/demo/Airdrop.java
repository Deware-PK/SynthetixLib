package com.github.dewarepk.synthetixexample.demo;

import com.github.dewarepk.synthetixlib.config.annotation.Comment;
import com.github.dewarepk.synthetixlib.config.annotation.NotEmpty;
import com.github.dewarepk.synthetixlib.config.annotation.Range;
import java.time.Duration;
import java.util.List;
import org.bukkit.Material;

/** One airdrop; stored as {@code airdrops/<id>.yml}. */
// @snippet:config-airdrop-record
@Comment("An airdrop. Edit this file and reload, or let a command change it.")
public record Airdrop(@Comment("Name shown to players") String name,
                      @Comment("Time between two drops, e.g. 30m or 1h30m") Duration interval,
                      @Comment("The drop waits until this many players are online") @Range(min = 0) int minPlayers,
                      @Comment({"What a drop gives. Types: item, command", "A command can use %player%"})
                      @NotEmpty List<Reward> rewards) {

    /** A starting point for every new airdrop; the plugin's settings may replace it. */
    public static Airdrop standard() {
        return new Airdrop("Airdrop", Duration.ofMinutes(30), 1, List.of(
                new ItemReward(Material.DIAMOND, 3, 25),
                new ItemReward(Material.GOLDEN_APPLE, 1, 50),
                new CommandReward("say %player% found an airdrop!")));
    }

    /** @return a copy with another name */
    public Airdrop withName(final String name) {
        return new Airdrop(name, interval, minPlayers, rewards);
    }
}
// @end
