package com.github.dewarepk.synthetixlib.config.yaml;

import com.github.dewarepk.synthetixlib.config.annotation.Comment;
import com.github.dewarepk.synthetixlib.config.annotation.Key;
import com.github.dewarepk.synthetixlib.config.annotation.Range;
import java.time.Duration;
import com.github.dewarepk.synthetixlib.config.annotation.NotEmpty;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Config records for the file tests; records have to be public, so they live here. */
public final class FileFixtures {

    private FileFixtures() {
    }

    public record AntiSnipe(@Comment("Extend the auction on late bids") boolean enabled,
                            @Range(min = 1) int extendSeconds) {
    }

    public record Zone(String world, @Range(min = 1) int radius) {
    }

    @Comment({"Test config", "second header line"})
    public record AuctionConfig(
            @Comment("Minimum starting bid") @Range(min = 1) int minBid,
            @Comment({"How long bidding lasts", "e.g. 30s, 2m"}) Duration bidTime,
            @Key("anti-snipe") AntiSnipe antiSnipe,
            List<String> blacklist,
            Map<String, Zone> zones) {

        public static AuctionConfig defaults() {
            return new AuctionConfig(100, Duration.ofSeconds(60), new AntiSnipe(true, 10),
                    List.of("BEDROCK"), Map.of("spawn", new Zone("world", 8)));
        }
    }

    public record ListConfig(List<Integer> numbers, Set<Integer> tags, @NotEmpty List<Integer> required,
                             List<Zone> zoneList) {

        public static ListConfig defaults() {
            return new ListConfig(List.of(1, 2, 3), Set.of(5), List.of(9), List.of(new Zone("world", 8)));
        }
    }

    /** Contains itself through a nullable component. */
    @Comment("A node")
    public record Node(@Comment("Name of the node") String name, @Nullable Node next) {
    }

    public record Inner(@Comment("Value inside") int value) {
    }

    public record Outer(@Comment("Optional section") Optional<Inner> inner, @Comment("Plain") int plain) {
    }
}
