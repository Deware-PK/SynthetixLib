package com.github.dewarepk.synthetixexample.selftest;

import static com.github.dewarepk.synthetixlib.selftest.SelfTest.require;

import com.github.dewarepk.synthetixlib.config.ConfigHandle;
import com.github.dewarepk.synthetixlib.config.Configs;
import com.github.dewarepk.synthetixlib.config.ReloadResult;
import com.github.dewarepk.synthetixlib.config.annotation.Comment;
import com.github.dewarepk.synthetixlib.config.annotation.Range;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.selftest.SelfTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Self-tests for YAML file configs: Bukkit's YamlConfiguration comments, the real data folder, async reload. */
final class ConfigFileSelfTests {

    /** Every Bukkit type the codecs support, in one file. */
    @Comment("Self-test config")
    public record Showcase(
            @Comment("Item shown in menus") Material icon,
            Sound sound,
            Location spawn,
            ItemStack reward,
            @Range(min = 1) int limit,
            Duration every) {

        static Showcase defaults() {
            final ItemStack reward = new ItemStack(Material.GOLD_INGOT, 4);
            reward.editMeta(meta -> meta.customName(
                    net.kyori.adventure.text.Component.text("Gold").decoration(TextDecoration.ITALIC, false)));
            return new Showcase(Material.GOLD_INGOT, Sound.ENTITY_PLAYER_LEVELUP,
                    new Location(Bukkit.getWorlds().get(0), 10.5, 70, -20.5, 45f, 5f), reward, 5,
                    Duration.ofMinutes(10));
        }
    }

    public record Plain(@Range(min = 1) int number, String text) {

        static Plain defaults() {
            return new Plain(1, "one");
        }
    }

    private ConfigFileSelfTests() {
    }

    private static Path fresh(final JavaPlugin plugin, final String name) throws IOException {
        final Path file = plugin.getDataFolder().toPath().resolve(name);
        Files.deleteIfExists(file);
        return file;
    }

    private static String read(final Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static void edit(final Path file, final String from, final String to) throws IOException {
        final String text = read(file);
        require(text.contains(from), "expected \"" + from + "\" in\n" + text);
        Files.writeString(file, text.replace(from, to), StandardCharsets.UTF_8);
    }

    static List<SelfTest> all(final JavaPlugin plugin, final SynthetixLib lib) {
        final Configs configs = Configs.create(lib);
        return List.of(
                SelfTest.sync("config.file.bukkit-types", () -> {
                    final Path file = fresh(plugin, "selftest/showcase.yml");
                    final ConfigHandle<Showcase> handle = configs.file("selftest/showcase.yml", Showcase.class,
                            Showcase::defaults).load();
                    final String created = read(file);
                    require(created.startsWith("# Self-test config\n"), "header: " + created);
                    require(created.contains("config-version: 1\n# Item shown in menus\nicon: gold_ingot\n"),
                            "version first, comment above icon: " + created);
                    require(created.contains("sound: entity.player.levelup\n"), "sound written as a key");
                    require(created.contains("reward:\n  material: gold_ingot\n  amount: 4\n  name: Gold\n"),
                            "item in readable form: " + created);
                    require(handle.get().equals(Showcase.defaults()), "first load returns the defaults");

                    final AtomicInteger notified = new AtomicInteger();
                    handle.onReload(next -> notified.incrementAndGet());
                    final ReloadResult unchanged = handle.reload();
                    require(unchanged.ok() && !unchanged.changed() && read(file).equals(created),
                            "an unchanged reload must not rewrite the file");
                    require(notified.get() == 0, "no listener call without a change");

                    edit(file, "icon: gold_ingot", "icon: EMERALD");
                    final ReloadResult changed = handle.reload();
                    require(changed.ok() && changed.changed() && handle.get().icon() == Material.EMERALD,
                            "edited icon: " + changed);
                    require(notified.get() == 1, "listener called once");
                    require(read(file).contains("icon: EMERALD"), "the user's text stays as written");

                    edit(file, "sound: entity.player.levelup", "sound: entity.player.levelp");
                    final ReloadResult invalid = handle.reload();
                    require(invalid.errors().size() == 1 && invalid.errors().get(0)
                                    .contains("did you mean entity.player.levelup")
                                    && invalid.errors().get(0).endsWith("-> using entity.player.levelup"),
                            "invalid sound: " + invalid.errors());
                    require(handle.get().sound() == Sound.ENTITY_PLAYER_LEVELUP, "default used for the sound");
                    require(read(file).contains("sound: entity.player.levelp\n"), "the invalid text stays in the file");

                    edit(file, "limit: 5\n", "");
                    final ReloadResult added = handle.reload();
                    require(added.errors().size() == 1 && read(file).contains("limit: 5\n"),
                            "missing key added, the invalid sound still reported: " + read(file));
                    final String stable = read(file);
                    handle.reload();
                    require(read(file).equals(stable), "second reload leaves the file alone");
                }),
                SelfTest.sync("config.file.migrate", () -> {
                    final Path file = fresh(plugin, "selftest/migrate.yml");
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, "number: 7\nname: hello\n", StandardCharsets.UTF_8);
                    final ConfigHandle<Plain> handle = configs.file("selftest/migrate.yml", Plain.class, Plain::defaults)
                            .version(2)
                            .migrate(1, 2, tree -> tree.rename("name", "text"))
                            .load();
                    require(handle.get().equals(new Plain(7, "hello")), "migrated value: " + handle.get());
                    require(read(file).startsWith("config-version: 2\n") && read(file).contains("text: hello"),
                            "migrated file: " + read(file));
                    require(Files.exists(file.resolveSibling("migrate.yml.v1.bak")), "backup of the old file");
                }),
                SelfTest.async("config.file.reload-async", () -> {
                    final ConfigHandle<Plain> handle;
                    try {
                        final Path file = fresh(plugin, "selftest/async.yml");
                        handle = configs.file("selftest/async.yml", Plain.class, Plain::defaults).load();
                        edit(file, "number: 1", "number: 2");
                    } catch (final IOException e) {
                        throw new IllegalStateException(e);
                    }
                    final AtomicBoolean listenerOnGlobalThread = new AtomicBoolean();
                    handle.onReload(next -> listenerOnGlobalThread.set(lib.scheduler().isGlobalThread()));
                    return handle.reloadAsync().thenAccept(result -> {
                        require(result.ok() && result.changed(), "async reload: " + result);
                        require(handle.get().number() == 2, "snapshot swapped");
                        require(listenerOnGlobalThread.get(), "listeners run on the global thread");
                        require(lib.scheduler().isGlobalThread(), "the future completes on the global thread");
                    });
                })
        );
    }
}
