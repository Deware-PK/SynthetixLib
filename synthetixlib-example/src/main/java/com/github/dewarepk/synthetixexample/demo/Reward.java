package com.github.dewarepk.synthetixexample.demo;

/**
 * What an airdrop gives. A sealed interface is written as a list of {@code type: ...} entries; the type is the record
 * name without the interface name ({@code ItemReward} is {@code item}).
 */
// @snippet:config-sealed-reward
public sealed interface Reward permits ItemReward, CommandReward {
}
// @end
