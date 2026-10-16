package com.github.dewarepk.synthetixlib.config.codec;

import com.github.dewarepk.synthetixlib.config.annotation.Key;
import com.github.dewarepk.synthetixlib.config.annotation.NotEmpty;
import com.github.dewarepk.synthetixlib.config.annotation.Range;
import com.github.dewarepk.synthetixlib.config.annotation.TypeName;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.jspecify.annotations.Nullable;

/** Types mapped by the codec tests. Records have to be public, so they live here. */
public final class Fixtures {

    private Fixtures() {
    }

    public enum Mode { SURVIVAL, DARK_OAK, CREATIVE }

    public record AntiSnipe(boolean enabled, @Range(min = 1) int extendSeconds) {
    }

    public record Zone(String world, int x, int y, int z, @Range(min = 1) int radius) {
    }

    public record AuctionConfig(
            @Range(min = 1) int minBid,
            Duration bidTime,
            @Key("anti-snipe") AntiSnipe antiSnipe,
            @NotEmpty List<String> blacklist,
            Map<String, Zone> zones) {

        public static AuctionConfig defaults() {
            return new AuctionConfig(100, Duration.ofSeconds(60), new AntiSnipe(true, 10),
                    List.of("BEDROCK"), Map.of("spawn", new Zone("world", 0, 64, 0, 8)));
        }
    }

    public record Everything(
            boolean flag, Boolean boxedFlag, int number, Integer boxedNumber, long big, double decimal, float small,
            String text, Mode mode, Duration duration, UUID id, NamespacedKey namespaced, net.kyori.adventure.key.Key adventure,
            Set<Mode> modes, Optional<String> maybe, Optional<String> absent, @Nullable String nothing,
            List<Map<String, Zone>> nested) {
    }

    public sealed interface Reward permits ItemReward, CommandReward, MoneyReward {
    }

    public record ItemReward(String material, @Range(min = 1, max = 100) int chance) implements Reward {
    }

    @TypeName("cmd")
    public record CommandReward(String command) implements Reward {
    }

    public record MoneyReward(double amount) implements Reward {
    }

    public record Airdrop(String name, List<Reward> rewards) {
    }

    /** Sealed hierarchy whose leaves are nested one level deeper. */
    public sealed interface Shape permits Round, Angular {
    }

    public sealed interface Angular extends Shape permits SquareShape, TriangleShape {
    }

    public record Round(double radius) implements Shape {
    }

    public record SquareShape(double side) implements Angular {
    }

    public record TriangleShape(double base, double height) implements Angular {
    }

    public sealed interface Clash permits ClashA, ClashB {
    }

    @TypeName("same")
    public record ClashA(int a) implements Clash {
    }

    @TypeName("same")
    public record ClashB(int b) implements Clash {
    }

    public sealed interface Tagged permits TaggedRecord {
    }

    public record TaggedRecord(String type) implements Tagged {
    }

    public sealed interface Open permits OpenRecord, OpenClass {
    }

    public record OpenRecord(int x) implements Open {
    }

    public non-sealed interface OpenClass extends Open {
    }

    public record Page<T>(List<T> items, Optional<T> featured) {
    }

    public record Node(String name, List<Node> children) {
    }

    public record Tree<T>(T value, List<Tree<T>> children) {
    }

    public record HttpSettings(String baseUrl, int maxHTTPRetries, String uuid2Name) {
    }

    public record Checked(@Range(min = 0) int value) {
        public Checked {
            if (value == 13) {
                throw new IllegalArgumentException("unlucky number");
            }
        }
    }

    public record BadRange(@Range(min = 1) String name) {
    }

    public record BadNotEmpty(@NotEmpty int number) {
    }

    public record DuplicateKey(@Key("same") int a, @Key("same") int b) {
    }

    public record Doubles(@Range(max = 1.5) double chance, @Range(min = 0.5, max = 2) Optional<Double> ratio) {
    }

    public record Money(long cents) {
    }

    public record Wallet(Money balance, List<Money> history) {
    }

    /** Package-private on purpose: must be rejected, not mapped through setAccessible. */
    record Hidden(int x) {
    }

    /** Map with non-String keys is not supported. */
    public record IntKeys(Map<Integer, String> names) {
    }

    public record RawList(List names) {
    }
}
