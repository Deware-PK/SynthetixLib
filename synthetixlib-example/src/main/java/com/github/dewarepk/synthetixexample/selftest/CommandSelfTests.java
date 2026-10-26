package com.github.dewarepk.synthetixexample.selftest;

import static com.github.dewarepk.synthetixlib.selftest.SelfTest.require;

import com.github.dewarepk.synthetixlib.command.Arg;
import com.github.dewarepk.synthetixlib.command.Args;
import com.github.dewarepk.synthetixlib.command.CommandCooldowns;
import com.github.dewarepk.synthetixlib.command.CommandMessages;
import com.github.dewarepk.synthetixlib.command.CommandService;
import com.github.dewarepk.synthetixlib.command.CommandSpec;
import com.github.dewarepk.synthetixlib.command.SubSpec;
import com.github.dewarepk.synthetixlib.config.Configs;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.config.TimeFormat;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.selftest.SelfTest;
import io.papermc.paper.registry.RegistryKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Self-tests of the command module. Paper fires the COMMANDS event right after {@code onEnable}, long before the
 * self-tests run, so the commands are registered in {@link #install}, which {@link ExampleSelfTests} calls from
 * {@code onEnable}.
 */
final class CommandSelfTests {

    /** A lang record carrying the built-in command messages, as a product would. */
    public record Messages(CommandMessages commands) {

        static Messages defaults() {
            return new Messages(CommandMessages.defaults());
        }
    }

    private final JavaPlugin plugin;
    private final SynthetixLib lib;
    private CommandCooldowns cooldowns;
    /** What the handlers did, in order. Only appended to on the global region thread. */
    private final List<String> log = Collections.synchronizedList(new ArrayList<>());
    /** Completed by the follow-up of the async test command, on the thread {@code runOnSender} chose. */
    private volatile CompletableFuture<String> asyncResult = new CompletableFuture<>();

    private CommandSelfTests(final JavaPlugin plugin, final SynthetixLib lib) {
        this.plugin = plugin;
        this.lib = lib;
    }

    /** Registers the test command; must run during {@code onEnable}. */
    static CommandSelfTests install(final JavaPlugin plugin, final SynthetixLib lib) {
        final CommandSelfTests tests = new CommandSelfTests(plugin, lib);
        tests.register();
        return tests;
    }

    private void register() {
        final Configs configs = Configs.create(lib);
        final LocaleHandle<Messages> lang = configs.locale("selftest-command-lang", Messages.class,
                        Messages::defaults)
                .defaultLocale(Locale.US)
                .load();

        final Arg<Integer> left = Arg.of("left", Args.integer(-100, 100));
        final Arg<Integer> right = Arg.of("right", Args.integer(-100, 100));
        final Arg<String> word = Arg.of("word", Args.word());
        final Arg<Integer> times = Arg.of("times", Args.integer(1, 5));
        final Arg<Enchantment> enchantment = Arg.of("enchantment", Args.registry(RegistryKey.ENCHANTMENT));
        final Arg<Material> material = Arg.of("material", Args.material());
        final Arg<World> world = Arg.of("world", Args.world());
        final Arg<Location> block = Arg.of("block", Args.blockPosition());
        final Arg<Location> position = Arg.of("position", Args.position());
        final Arg<Player> player = Arg.of("player", Args.player());
        final Arg<Duration> time = Arg.of("time", Args.duration());
        final Arg<GameMode> mode = Arg.of("mode", Args.enumOf(GameMode.class));
        final Arg<String> fruit = Arg.of("fruit", Args.oneOf(() -> List.of("apple", "pear")));
        final Arg<ItemStack> item = Arg.of("item", Args.itemStack());

        final CommandService service = CommandService.create(lib).messages(lang, Messages::commands);
        this.cooldowns = service.cooldowns();
        service.register(CommandSpec.literal("sxtest")
                        .aliases("sxt")
                        .description("SynthetixLib command self-test")
                        .executes(ctx -> log.add("root " + ctx.label()))
                        .sub(SubSpec.literal("add").argument(left).argument(right).executes(ctx ->
                                log.add("add " + (ctx.get(left) + ctx.get(right)) + " via " + ctx.label())))
                        .sub(SubSpec.literal("echo").argument(word).optional(times, 1).executes(ctx ->
                                log.add("echo " + ctx.get(word) + " x" + ctx.get(times))))
                        .sub(SubSpec.literal("players").playerOnly().executes(ctx ->
                                log.add("players ran")))
                        .sub(SubSpec.literal("fail").executes(ctx -> {
                            log.add("fail reached");
                            throw ctx.fail("<red>Stopped on purpose, <sender>");
                        }))
                        .sub(SubSpec.literal("registry").argument(enchantment).executes(ctx ->
                                log.add("registry " + ctx.get(enchantment).key().asString())))
                        .sub(SubSpec.literal("material").argument(material).executes(ctx ->
                                log.add("material " + ctx.get(material))))
                        .sub(SubSpec.literal("world").argument(world).executes(ctx ->
                                log.add("world " + ctx.get(world).getName())))
                        .sub(SubSpec.literal("block").argument(block).executes(ctx ->
                                log.add("block " + describe(ctx.get(block)))))
                        .sub(SubSpec.literal("position").argument(position).executes(ctx ->
                                log.add("position " + describe(ctx.get(position)))))
                        .sub(SubSpec.literal("player").argument(player).executes(ctx ->
                                log.add("player " + ctx.get(player).getName())))
                        .sub(SubSpec.literal("duration").argument(time).executes(ctx ->
                                log.add("duration " + ctx.get(time))))
                        .sub(SubSpec.literal("mode").argument(mode).executes(ctx ->
                                log.add("mode " + ctx.get(mode))))
                        .sub(SubSpec.literal("fruit").argument(fruit).executes(ctx ->
                                log.add("fruit " + ctx.get(fruit))))
                        .sub(SubSpec.literal("item").argument(item).executes(ctx ->
                                log.add("item " + ctx.get(item).getType())))
                        .sub(SubSpec.literal("cool").cooldown(Duration.ofHours(1)).executes(ctx ->
                                log.add("cool")))
                        .sub(SubSpec.literal("coolfn").cooldown(sender -> Duration.ofHours(1)).executes(ctx ->
                                log.add("coolfn")))
                        .sub(SubSpec.literal("async").executesAsync(ctx -> {
                            final boolean offGlobal = ctx.isAsync() && !lib.scheduler().isGlobalThread();
                            ctx.runOnSender(() -> asyncResult.complete("offGlobal=" + offGlobal
                                    + " backOnGlobal=" + lib.scheduler().isGlobalThread()));
                        }))
                        .helpSub()
                        .build());
    }

    private static String describe(final Location location) {
        return location.getWorld().getName() + " " + location.getX() + " " + location.getY() + " "
                + location.getZ();
    }

    /** Dispatches a command; Bukkit throws {@code CommandException} for some vanilla parse errors. */
    private static void dispatch(final CommandSender sender, final String command) {
        try {
            Bukkit.dispatchCommand(sender, command);
        } catch (final CommandException expected) {
            // a vanilla parse error (unknown key, out of range): the handler simply must not have run
        }
    }

    List<SelfTest> all() {
        return List.of(
                SelfTest.sync("command.basic", () -> {
                    final CommandSender console = plugin.getServer().getConsoleSender();
                    log.clear();
                    Bukkit.dispatchCommand(console, "sxtest");
                    Bukkit.dispatchCommand(console, "sxtest add 2 3");
                    Bukkit.dispatchCommand(console, "sxt add 4 5");
                    Bukkit.dispatchCommand(console, "sxtest echo hi");
                    Bukkit.dispatchCommand(console, "sxtest echo hi 3");
                    Bukkit.dispatchCommand(console,
                            plugin.getName().toLowerCase(Locale.ROOT) + ":sxtest add 1 1");
                    require(log.equals(List.of("root sxtest", "add 5 via sxtest", "add 9 via sxt", "echo hi x1",
                                    "echo hi x3", "add 2 via sxtest")),
                            "executed nodes, alias label and optional default: " + log);

                    // Brigadier rejects these before any handler runs; Bukkit throws or just reports, depending on why.
                    for (final String invalid : List.of("sxtest add 1000 1", "sxtest echo")) {
                        try {
                            Bukkit.dispatchCommand(console, invalid);
                        } catch (final CommandException expected) {
                            // fine: out of range
                        }
                    }
                    require(log.size() == 6, "no handler ran for invalid input: " + log);
                }),
                SelfTest.sync("command.player-only", () -> {
                    final CommandSender console = plugin.getServer().getConsoleSender();
                    log.clear();
                    // The console sees "Only players can use this." (checked in the unit tests); the handler must not run.
                    Bukkit.dispatchCommand(console, "sxtest players");
                    require(log.isEmpty(), "player-only node ran for the console: " + log);
                    Bukkit.dispatchCommand(console, "sxtest fail");
                    require(log.equals(List.of("fail reached")), "fail handler ran once: " + log);
                }),
                SelfTest.sync("command.args.registry", () -> {
                    final CommandSender console = plugin.getServer().getConsoleSender();
                    log.clear();
                    dispatch(console, "sxtest registry sharpness");
                    dispatch(console, "sxtest registry minecraft:efficiency");
                    dispatch(console, "sxtest registry not_an_enchantment");
                    require(log.equals(List.of("registry minecraft:sharpness", "registry minecraft:efficiency")),
                            "known keys resolved, unknown key refused by the server: " + log);
                }),
                SelfTest.sync("command.args.material", () -> {
                    final CommandSender console = plugin.getServer().getConsoleSender();
                    log.clear();
                    dispatch(console, "sxtest material diamond");
                    dispatch(console, "sxtest material minecraft:oak_log");
                    dispatch(console, "sxtest material not_a_material");
                    require(log.equals(List.of("material DIAMOND", "material OAK_LOG")), "materials: " + log);
                    dispatch(console, "sxtest item diamond_sword");
                    require(log.size() == 3 && log.get(2).equals("item DIAMOND_SWORD"), "item stack: " + log);
                }),
                SelfTest.sync("command.args.world", () -> {
                    final CommandSender console = plugin.getServer().getConsoleSender();
                    final World first = plugin.getServer().getWorlds().get(0);
                    log.clear();
                    dispatch(console, "sxtest world " + first.getKey().asString());
                    dispatch(console, "sxtest world minecraft:not_a_dimension");
                    require(log.equals(List.of("world " + first.getName())), "world by key: " + log);
                }),
                SelfTest.sync("command.args.position", () -> {
                    final CommandSender console = plugin.getServer().getConsoleSender();
                    final String world = plugin.getServer().getWorlds().get(0).getName();
                    log.clear();
                    dispatch(console, "sxtest block 10 64 -3");
                    dispatch(console, "sxtest position 10 64 -3");
                    dispatch(console, "sxtest position 1.25 70.5 2.75");
                    require(log.equals(List.of("block " + world + " 10.0 64.0 -3.0",
                                    "position " + world + " 10.5 64.0 -2.5",
                                    "position " + world + " 1.25 70.5 2.75")),
                            "absolute coordinates (whole numbers of a position are centred like vanilla): " + log);
                }),
                SelfTest.sync("command.args.player-not-found", () -> {
                    final List<String> received = Collections.synchronizedList(new ArrayList<>());
                    final CommandSender capture = Bukkit.createCommandSender(message ->
                            received.add(PlainTextComponentSerializer.plainText().serialize(message)));
                    log.clear();
                    final boolean dispatched;
                    try {
                        dispatched = Bukkit.dispatchCommand(capture, "sxtest player nobody_here");
                    } catch (final CommandException e) {
                        throw new AssertionError("dispatch failed: " + e.getCause(), e);
                    }
                    require(log.isEmpty(), "handler ran although nobody matched: " + log);
                    require(dispatched || !received.isEmpty(), "dispatch returned " + dispatched);
                    require(received.equals(List.of("No player found: nobody_here")),
                            "sender gets the locale message with <input>: " + received);
                }),
                SelfTest.sync("command.args.text", () -> {
                    final CommandSender console = plugin.getServer().getConsoleSender();
                    log.clear();
                    dispatch(console, "sxtest duration 1h30m");
                    dispatch(console, "sxtest duration 5q");
                    dispatch(console, "sxtest mode creative");
                    dispatch(console, "sxtest mode not-a-mode");
                    dispatch(console, "sxtest fruit PEAR");
                    dispatch(console, "sxtest fruit banana");
                    require(log.equals(List.of("duration PT1H30M", "mode CREATIVE", "fruit pear")),
                            "valid values converted, invalid ones rejected while parsing: " + log);
                }),
                SelfTest.sync("command.cooldown-console", () -> {
                    final CommandSender console = plugin.getServer().getConsoleSender();
                    log.clear();
                    dispatch(console, "sxtest cool");
                    dispatch(console, "sxtest cool");
                    require(log.equals(List.of("cool", "cool")), "the console has no cooldown: " + log);
                }),
                SelfTest.sync("command.cooldown-reset", () -> {
                    final CommandSender console = plugin.getServer().getConsoleSender();
                    log.clear();
                    dispatch(console, "sxtest coolfn");
                    dispatch(console, "sxtest coolfn");
                    require(log.equals(List.of("coolfn", "coolfn")), "a function cooldown runs; console has none: " + log);
                    final java.util.UUID nobody = java.util.UUID.randomUUID();
                    require(cooldowns.remaining(nobody, "sxtest coolfn").isZero(), "nobody is on cooldown");
                    cooldowns.reset(nobody, "sxtest coolfn");
                    cooldowns.reset(nobody);
                    require(TimeFormat.english().format(Duration.ofSeconds(72)).equals("1m 12s"), "readable time");
                    require(TimeFormat.english().format(Duration.ofHours(1)).equals("1h 0m"), "readable hours");
                }),
                SelfTest.async("command.async", () -> {
                    asyncResult = new CompletableFuture<>();
                    dispatch(plugin.getServer().getConsoleSender(), "sxtest async");
                    return asyncResult.thenAccept(result -> require(
                            result.equals("offGlobal=true backOnGlobal=true"),
                            "async handler off the global thread, runOnSender back on it: " + result));
                }),
                SelfTest.sync("command.help", () -> {
                    final List<String> received = Collections.synchronizedList(new ArrayList<>());
                    final CommandSender capture = Bukkit.createCommandSender(message ->
                            received.add(PlainTextComponentSerializer.plainText().serialize(message)));
                    log.clear();
                    dispatch(capture, "sxtest help");
                    final String all = String.join(" | ", received);
                    require(all.contains("sxtest add"), "help lists a normal subcommand: " + received);
                    require(!all.contains("sxtest players"),
                            "help hides a player-only subcommand from a non-player: " + received);
                    require(log.isEmpty(), "help ran no handler of the tree: " + log);
                }),
                SelfTest.sync("command.messages-in-lang", () -> {
                    final Path file = plugin.getDataFolder().toPath().resolve("selftest-command-lang")
                            .resolve("en_US.yml");
                    final String yaml;
                    try {
                        yaml = Files.readString(file);
                    } catch (final IOException e) {
                        throw new AssertionError("lang file not written: " + file, e);
                    }
                    require(yaml.contains("commands:") && yaml.contains("player-only:")
                                    && yaml.contains("invalid-value:") && yaml.contains("cooldown:"),
                            "the built-in messages are keys of the product's lang file");
                })
        );
    }
}
