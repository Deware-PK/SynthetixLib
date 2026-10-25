package com.github.dewarepk.synthetixexample.demo;

import com.github.dewarepk.synthetixlib.config.annotation.Comment;
import com.github.dewarepk.synthetixlib.config.annotation.Range;
import org.bukkit.Material;

/** Gives items. */
// @snippet:config-item-reward
public record ItemReward(@Comment("Material key, e.g. diamond") Material material,
                         @Range(min = 1, max = 64) int amount,
                         @Comment("Chance in percent") @Range(min = 1, max = 100) int chance) implements Reward {
}
// @end
