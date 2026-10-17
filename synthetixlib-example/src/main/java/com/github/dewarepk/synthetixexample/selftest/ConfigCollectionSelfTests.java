package com.github.dewarepk.synthetixexample.selftest;

import static com.github.dewarepk.synthetixexample.selftest.SelfTest.require;

import com.github.dewarepk.synthetixlib.config.Change;
import com.github.dewarepk.synthetixlib.config.ConfigCollection;
import com.github.dewarepk.synthetixlib.config.Configs;
import com.github.dewarepk.synthetixlib.config.annotation.Comment;
import com.github.dewarepk.synthetixlib.config.annotation.Range;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.bukkit.plugin.java.JavaPlugin;

/** Self-test for config collections: one file per object, async writes, events on the global thread. */
final class ConfigCollectionSelfTests {

    public record Drop(@Comment("Display name") String name, @Comment("Times it ran") @Range(min = 0) int count) {

        static Drop defaults(final String id) {
            return new Drop("Drop " + id, 0);
        }

        Drop withCount(final int count) {
            return new Drop(name, count);
        }
    }

    private ConfigCollectionSelfTests() {
    }

    private static void deleteTree(final Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            for (final Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    static List<SelfTest> all(final JavaPlugin plugin, final SynthetixLib lib) {
        final Configs configs = Configs.create(lib);
        return List.of(
                SelfTest.async("config.collection.basic", () -> {
                    final Path folder = plugin.getDataFolder().toPath().resolve("selftest/drops");
                    final ConfigCollection<Drop> drops;
                    try {
                        deleteTree(folder);
                        drops = configs.collection("selftest/drops", Drop.class, Drop::defaults).load();
                    } catch (final IOException e) {
                        throw new IllegalStateException(e);
                    }
                    final List<String> events = Collections.synchronizedList(new ArrayList<>());
                    final List<Boolean> onGlobal = Collections.synchronizedList(new ArrayList<>());
                    drops.onChange(change -> {
                        onGlobal.add(lib.scheduler().isGlobalThread());
                        events.add(switch (change) {
                            case Change.Added<Drop> add -> "added:" + add.id();
                            case Change.Updated<Drop> upd -> "updated:" + upd.id() + "=" + upd.value().count();
                            case Change.Removed<Drop> rem -> "removed:" + rem.id();
                        });
                    });

                    drops.create("one");
                    drops.update("one", drop -> drop.withCount(3));
                    require(drops.get("one").orElseThrow().count() == 3, "mutations are visible at once");

                    return drops.reloadAsync().thenCompose(first -> {
                        require(first.ok() && !first.changed(), "the reload sees what was queued: " + first);
                        final Path file = folder.resolve("one.yml");
                        try {
                            final String text = Files.readString(file, StandardCharsets.UTF_8);
                            require(text.contains("# Display name\nname: Drop one\n# Times it ran\ncount: 3"),
                                    "file with comments: " + text);
                            Files.writeString(file, text.replace("count: 3", "count: 7"), StandardCharsets.UTF_8);
                        } catch (final IOException e) {
                            throw new IllegalStateException(e);
                        }
                        return drops.reloadAsync();
                    }).thenCompose(second -> {
                        require(second.changed() && drops.get("one").orElseThrow().count() == 7,
                                "hand edit picked up: " + second);
                        drops.create("two");
                        drops.delete("one");
                        return drops.reloadAsync();
                    }).thenAccept(third -> {
                        require(drops.ids().equals(java.util.Set.of("two")), "ids: " + drops.ids());
                        require(Files.exists(folder.resolve("two.yml")) && !Files.exists(folder.resolve("one.yml")),
                                "files follow the objects");
                        try (Stream<Path> trash = Files.list(folder.resolve(".deleted"))) {
                            require(trash.count() == 1, "deleted file is kept in .deleted");
                        } catch (final IOException e) {
                            throw new IllegalStateException(e);
                        }
                        require(events.equals(List.of("added:one", "updated:one=3", "updated:one=7", "added:two",
                                "removed:one")), "events: " + events);
                        require(!onGlobal.contains(false), "listeners run on the global thread");
                    });
                })
        );
    }
}
