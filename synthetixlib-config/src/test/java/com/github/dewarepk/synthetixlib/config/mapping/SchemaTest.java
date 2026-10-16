package com.github.dewarepk.synthetixlib.config.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.dewarepk.synthetixlib.config.yaml.FileFixtures.AuctionConfig;
import com.github.dewarepk.synthetixlib.config.yaml.FileFixtures.Node;
import com.github.dewarepk.synthetixlib.config.yaml.FileFixtures.Outer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaTest {

    @Test
    void commentsFollowNestedRecordsByDottedPath() {
        assertEquals(Map.of(
                "min-bid", List.of("Minimum starting bid"),
                "bid-time", List.of("How long bidding lasts", "e.g. 30s, 2m"),
                "anti-snipe.enabled", List.of("Extend the auction on late bids")),
                Schema.comments(AuctionConfig.class));
        assertEquals(List.of("Test config", "second header line"), Schema.header(AuctionConfig.class));
    }

    @Test
    void aRecordContainingItselfDoesNotRecurseForever() {
        assertEquals(Map.of("name", List.of("Name of the node")), Schema.comments(Node.class));
    }

    @Test
    void optionalRecordsAreFollowed() {
        assertEquals(Map.of("inner", List.of("Optional section"), "inner.value", List.of("Value inside"),
                "plain", List.of("Plain")), Schema.comments(Outer.class));
    }

    @Test
    void aTypeThatIsNotARecordIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Schema.comments(String.class));
    }
}
