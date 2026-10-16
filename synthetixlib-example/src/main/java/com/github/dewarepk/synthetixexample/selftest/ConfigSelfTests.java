package com.github.dewarepk.synthetixexample.selftest;

import static com.github.dewarepk.synthetixexample.selftest.SelfTest.require;

import com.github.dewarepk.synthetixlib.config.Configs;
import com.github.dewarepk.synthetixlib.config.annotation.Key;
import com.github.dewarepk.synthetixlib.config.annotation.Range;
import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.config.codec.DecodeException;
import com.github.dewarepk.synthetixlib.config.codec.TypeToken;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/** Self-tests for synthetixlib-config. */
final class ConfigSelfTests {

    /** Mapped by the codec self-test; records must be public. */
    public record Zone(String world, @Range(min = 1) int radius) {
    }

    public sealed interface Reward permits ItemReward, CommandReward {
    }

    public record ItemReward(String material, int chance) implements Reward {
    }

    public record CommandReward(String command) implements Reward {
    }

    public record Settings(@Key("min-bid") @Range(min = 1) int minBid, Duration bidTime, NamespacedKey model,
                           Optional<String> note, @Nullable String owner, Map<String, Zone> zones,
                           List<Reward> rewards) {
    }

    private ConfigSelfTests() {
    }

    static List<SelfTest> all(final JavaPlugin plugin) {
        return List.of(
                SelfTest.sync("config.setup", () -> {
                    // Separate lib instance so closing it does not affect the shared test lib.
                    final SynthetixLib own = SynthetixLib.init(plugin);
                    final Configs configs = Configs.create(own);
                    require(!configs.isClosed(), "Configs must be open after create");
                    own.close();
                    require(configs.isClosed(), "lib.close() must close attached Configs");
                    boolean rejected = false;
                    try {
                        own.attach(() -> { });
                    } catch (final IllegalStateException expected) {
                        rejected = true;
                    }
                    require(rejected, "attach after close must throw IllegalStateException");
                }),
                SelfTest.sync("config.codecs.records-json", () -> {
                    // Gson, jspecify and record reflection on the real server, inside the relocated jar.
                    final Codecs codecs = Codecs.create();
                    final Settings settings = new Settings(100, Duration.ofMinutes(90),
                            new NamespacedKey("synthetix", "model"), Optional.of("hi"), null,
                            Map.of("spawn", new Zone("world", 8)),
                            List.of(new ItemReward("DIAMOND", 50), new CommandReward("say hi")));
                    final Object tree = codecs.encode(settings);
                    require(codecs.decode(tree, Settings.class).equals(settings), "tree round trip");
                    final String json = codecs.toJson(settings);
                    require(json.contains("\"min-bid\":100") && json.contains("\"bid-time\":\"1h30m\"")
                            && json.contains("\"type\":\"item\""), "JSON shape: " + json);
                    require(codecs.fromJson(json, Settings.class).equals(settings), "JSON round trip");
                    require(codecs.fromJson("[{\"world\":\"w\",\"radius\":2}]",
                            new TypeToken<List<Zone>>() { }).size() == 1, "generic JSON list");
                }),
                SelfTest.sync("config.codecs.errors", () -> {
                    final Codecs codecs = Codecs.create();
                    try {
                        codecs.fromJson("{\"world\":\"w\",\"radius\":0}", Zone.class);
                        throw new AssertionError("radius 0 must be rejected");
                    } catch (final DecodeException e) {
                        require(e.problems().size() == 1 && e.problems().get(0).toString()
                                .equals("radius: must be >= 1 (was 0)"), "problem: " + e.getMessage());
                    }
                })
        );
    }
}
