package com.github.dewarepk.synthetixlib.command;

import com.github.dewarepk.synthetixlib.config.codec.Durations;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.registry.RegistryKey;
import java.time.Duration;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.jspecify.annotations.Nullable;

/**
 * The argument types. Each maps to a native Brigadier type, so the client validates the input and colours it.
 *
 * <pre>{@code
 * Arg<Long> amount = Arg.of("amount", Args.longRange(1, 1_000_000));
 * Arg<String> reason = Arg.of("reason", Args.greedy());
 * Arg<Player> target = Arg.of("target", Args.player());
 * Arg<Duration> time = Arg.of("time", Args.duration());
 * }</pre>
 *
 * <p>Types that convert text on the server ({@link #duration()}, {@link #enumOf}, {@link #oneOf}, {@link #custom})
 * reject bad input while Brigadier parses, with {@link CommandMessages#invalidValue()} in the sender's language, shown
 * red under the input. Selector based types ({@link #player()}, {@link #players()}, {@link #offlinePlayer()}) answer
 * {@link CommandMessages#playerNotFound()} when the handler reads them. Vanilla errors (a number out of range, an
 * unknown selector) stay vanilla: the client translates them itself.
 *
 * <p>Suggestions run on Paper's suggestion thread, which may not be a server thread: they must read only
 * thread-safe data.
 */
public final class Args {

    private Args() {
    }

    /** A value that maps 1:1 to a native Brigadier type. */
    private record Native<T>(ArgumentType<T> nativeType, Class<T> valueType) implements ArgType<T> {

        @Override
        public T read(final CommandContext<CommandSourceStack> context, final String name) {
            return context.getArgument(name, valueType);
        }
    }

    /** @return a single word (no spaces) */
    public static ArgType<String> word() {
        return new Native<>(StringArgumentType.word(), String.class);
    }

    /** @return a word, or text in quotes when it contains spaces */
    public static ArgType<String> string() {
        return new Native<>(StringArgumentType.string(), String.class);
    }

    /** @return the rest of the input; only valid as the last argument of a node */
    public static ArgType<String> greedy() {
        return new Native<>(StringArgumentType.greedyString(), String.class);
    }

    /**
     * @param min lowest accepted value
     * @param max highest accepted value
     * @return an int in range, checked by the client
     */
    public static ArgType<Integer> integer(final int min, final int max) {
        return new Native<>(IntegerArgumentType.integer(min, max), Integer.class);
    }

    /**
     * @param min lowest accepted value
     * @param max highest accepted value
     * @return a long in range, checked by the client
     */
    public static ArgType<Long> longRange(final long min, final long max) {
        return new Native<>(LongArgumentType.longArg(min, max), Long.class);
    }

    /**
     * @param min lowest accepted value
     * @param max highest accepted value
     * @return a double in range, checked by the client
     */
    public static ArgType<Double> decimal(final double min, final double max) {
        return new Native<>(DoubleArgumentType.doubleArg(min, max), Double.class);
    }

    /** @return {@code true} or {@code false} */
    public static ArgType<Boolean> bool() {
        return new Native<>(BoolArgumentType.bool(), Boolean.class);
    }

    // --- players --------------------------------------------------------------------------------------------------

    /**
     * One online player, by name or selector ({@code @p}, {@code @s}, ...), resolved against the source. Nobody
     * matched: {@link CommandMessages#playerNotFound()} with {@code <input>}.
     *
     * @return the argument type
     */
    public static ArgType<Player> player() {
        return ArgTypeImpl.lazy(ArgumentTypes::player, (context, name, host) ->
                resolvePlayers(context, name, host).get(0));
    }

    /**
     * Any number of online players, by name or selector ({@code @a}, {@code @r[distance=..10]}, ...), resolved against
     * the source. Nobody matched: {@link CommandMessages#playerNotFound()} with {@code <input>}.
     *
     * @return the argument type; the list is never empty
     */
    public static ArgType<List<Player>> players() {
        return ArgTypeImpl.lazy(ArgumentTypes::players, Args::resolvePlayers);
    }

    private static List<Player> resolvePlayers(final CommandContext<CommandSourceStack> context, final String name,
                                               final @Nullable CommandHost host) throws CommandSyntaxException {
        final List<Player> found;
        try {
            found = context.getArgument(name, PlayerSelectorArgumentResolver.class).resolve(context.getSource());
        } catch (final CommandSyntaxException e) {
            // Paper reports "nobody matched" by throwing vanilla's argument.entity.notfound.*; that one becomes our
            // locale message, every other vanilla error stays as it is.
            if (host != null && isNobodyFound(host.fromMessage(e.getRawMessage()))) {
                throw ArgErrors.playerNotFound(host, context.getSource().getSender(), Inputs.label(context),
                        typed(context, name));
            }
            throw e;
        }
        if (found.isEmpty()) {
            throw ArgErrors.playerNotFound(host, context.getSource().getSender(), Inputs.label(context),
                    typed(context, name));
        }
        return List.copyOf(found);
    }

    private static boolean isNobodyFound(final Component message) {
        return message instanceof TranslatableComponent translatable
                && translatable.key().startsWith("argument.entity.notfound");
    }

    /** The text the sender typed for argument {@code name} (a literal may have the same name). */
    private static String typed(final CommandContext<CommandSourceStack> context, final String name) {
        for (final ParsedCommandNode<CommandSourceStack> parsed : context.getNodes()) {
            if (parsed.getNode() instanceof ArgumentCommandNode<?, ?> && parsed.getNode().getName().equals(name)) {
                return parsed.getRange().get(context.getInput());
            }
        }
        return name;
    }

    /**
     * A player who is online or in the server's profile cache; never a blocking lookup. Tab-complete offers the
     * names of online players. Not cached: {@link CommandMessages#playerNotFound()} with {@code <input>}.
     *
     * @return the argument type
     */
    public static ArgType<OfflinePlayer> offlinePlayer() {
        return offlinePlayerWith(
                (source, name) -> source.getSender().getServer().getOfflinePlayerIfCached(name),
                source -> source.getSender().getServer().getOnlinePlayers().stream().map(Player::getName).toList());
    }

    /** {@link #offlinePlayer()} with the profile cache and the online names given, for tests. */
    static ArgType<OfflinePlayer> offlinePlayerWith(
            final BiFunction<CommandSourceStack, String, @Nullable OfflinePlayer> lookup,
            final Function<CommandSourceStack, ? extends Collection<String>> onlineNames) {
        return ArgTypeImpl.<OfflinePlayer>lazy(StringArgumentType::word, (context, name, host) -> {
            final String typed = context.getArgument(name, String.class);
            final OfflinePlayer found = lookup.apply(context.getSource(), typed);
            if (found == null) {
                throw ArgErrors.playerNotFound(host, context.getSource().getSender(), Inputs.label(context), typed);
            }
            return found;
        }).withSuggestions(onlineNames);
    }

    // --- world and positions --------------------------------------------------------------------------------------

    /** @return a loaded world, by its dimension key */
    public static ArgType<World> world() {
        return ArgTypeImpl.direct(ArgumentTypes::world, World.class);
    }

    /**
     * A block position ({@code 10 64 -3}, {@code ~ ~1 ~}); relative coordinates are resolved against the source, so it
     * works behind {@code /execute positioned}. The world is the source's world.
     *
     * @return the argument type; the location is the corner of the block
     */
    public static ArgType<Location> blockPosition() {
        return ArgTypeImpl.lazy(ArgumentTypes::blockPosition, (context, name, host) ->
                context.getArgument(name, BlockPositionResolver.class).resolve(context.getSource())
                        .toLocation(context.getSource().getLocation().getWorld()));
    }

    /**
     * An exact position ({@code 10.5 64 -3.5}, {@code ~ ~1 ~}); relative coordinates are resolved against the source.
     * Whole numbers are centred in the block like vanilla commands do ({@code 10} is {@code 10.5}). The world is the
     * source's world.
     *
     * @return the argument type
     */
    public static ArgType<Location> position() {
        return ArgTypeImpl.lazy(() -> ArgumentTypes.finePosition(true), (context, name, host) ->
                context.getArgument(name, FinePositionResolver.class).resolve(context.getSource())
                        .toLocation(context.getSource().getLocation().getWorld()));
    }

    // --- items and registries -------------------------------------------------------------------------------------

    /** @return an item with optional data components, like {@code diamond_sword[damage=5]} */
    public static ArgType<ItemStack> itemStack() {
        return ArgTypeImpl.direct(ArgumentTypes::itemStack, ItemStack.class);
    }

    /**
     * A value of a Paper registry by key, e.g. {@code Args.registry(RegistryKey.ENCHANTMENT)}.
     *
     * @param key the registry
     * @param <T> the value type of the registry
     * @return the argument type
     */
    public static <T> ArgType<T> registry(final RegistryKey<T> key) {
        Objects.requireNonNull(key, "key");
        return ArgTypeImpl.lazy(() -> ArgumentTypes.resource(key), (context, name, host) -> {
            @SuppressWarnings("unchecked")
            final T value = (T) context.getArgument(name, Object.class);
            return value;
        });
    }

    /** @return an item {@link Material} by key, offered from the item registry */
    public static ArgType<Material> material() {
        return ArgTypeImpl.lazy(() -> ArgumentTypes.resource(RegistryKey.ITEM), (context, name, host) -> {
            // ItemType#asMaterial is deprecated; the key finds the same constant (like the config MaterialCodec).
            final String key = context.getArgument(name, ItemType.class).key().asString();
            final Material material = Material.matchMaterial(key);
            if (material == null || material.isLegacy()) {
                throw ArgErrors.invalid(host, context.getSource().getSender(), key, "not a material");
            }
            return material;
        });
    }

    // --- text based -----------------------------------------------------------------------------------------------

    /**
     * An enum constant, typed in lower case with dashes ({@code dark-oak} finds {@code DARK_OAK}); the exact constant
     * name works too. Tab-complete offers the lower kebab case names. Same matching as enums in config files.
     *
     * @param type the enum
     * @param <E>  the enum
     * @return the argument type
     */
    public static <E extends Enum<E>> ArgType<E> enumOf(final Class<E> type) {
        Objects.requireNonNull(type, "type");
        final List<E> constants = List.copyOf(EnumSet.allOf(type));
        final List<String> names = constants.stream()
                .map(constant -> constant.name().toLowerCase(Locale.ROOT).replace('_', '-')).toList();
        return ArgTypeImpl.converted(StringArgumentType::word, text -> {
            for (final E constant : constants) {
                if (constant.name().equals(text)) {
                    return constant;
                }
            }
            final String normalized = text.replace('-', '_');
            for (final E constant : constants) {
                if (constant.name().equalsIgnoreCase(normalized)) {
                    return constant;
                }
            }
            throw new IllegalArgumentException("expected one of: " + preview(names));
        }, source -> names);
    }

    /**
     * A duration in the syntax of config files: {@code 250ms}, {@code 30s}, {@code 1h30m}, {@code 2d}.
     *
     * @return the argument type
     */
    public static ArgType<Duration> duration() {
        return ArgTypeImpl.converted(StringArgumentType::word, Durations::parse, null);
    }

    /** @return a UUID */
    public static ArgType<UUID> uuid() {
        return ArgTypeImpl.direct(ArgumentTypes::uuid, UUID.class);
    }

    /** @return a namespaced key such as {@code minecraft:stone} (the namespace may be left out) */
    public static ArgType<NamespacedKey> key() {
        return ArgTypeImpl.direct(ArgumentTypes::namespacedKey, NamespacedKey.class);
    }

    /**
     * A word that must be one of {@code values}. The supplier is asked for the suggestions and again when the command
     * runs, so a value that vanished since the tab-complete is refused. Matching ignores case and returns the
     * spelling of the set. Values must be single words (letters, digits, {@code _ - . +}).
     *
     * @param values the allowed values, read on every call and possibly from other threads: return a snapshot
     * @return the argument type
     */
    public static ArgType<String> oneOf(final Supplier<? extends Collection<String>> values) {
        Objects.requireNonNull(values, "values");
        return ArgTypeImpl.converted(StringArgumentType::word, text -> {
            final Collection<String> allowed = values.get();
            for (final String value : allowed) {
                if (value.equals(text)) {
                    return value;
                }
            }
            for (final String value : allowed) {
                if (value.equalsIgnoreCase(text)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("expected one of: " + preview(allowed));
        }, source -> values.get());
    }

    // --- your own types -------------------------------------------------------------------------------------------

    /**
     * Your own type on top of a native one: the client validates {@code nativeType}, the server turns its value into a
     * {@code T}. Built on Paper's {@code CustomArgumentType.Converted}.
     *
     * <pre>{@code
     * ArgType<Lot> lot = Args.custom(StringArgumentType.word(), id -> {
     *     Lot found = auctions.find(id);
     *     if (found == null) {
     *         throw new IllegalArgumentException("no such lot");   // shown as "<id> is not valid: no such lot"
     *     }
     *     return found;
     * }, source -> auctions.openLotIds());
     * }</pre>
     *
     * @param nativeType how the client sees the argument, e.g. {@code StringArgumentType.word()}
     * @param parse      converts the parsed value; throw {@link IllegalArgumentException} (its message is the
     *                   {@code <reason>}) to reject it; must not return {@code null}
     * @param suggest    candidates for tab-complete, {@code null} to keep those of {@code nativeType}; runs on Paper's
     *                   suggestion thread
     * @param <N>        the native value
     * @param <T>        your value
     * @return the argument type
     */
    public static <N, T> ArgType<T> custom(final ArgumentType<N> nativeType, final Function<N, T> parse,
                                           final @Nullable Function<CommandSourceStack, ? extends Collection<String>>
                                                   suggest) {
        Objects.requireNonNull(nativeType, "nativeType");
        Objects.requireNonNull(parse, "parse");
        return ArgTypeImpl.converted(() -> nativeType, parse, suggest);
    }

    /**
     * Like {@link #custom(ArgumentType, Function, Function)} with the suggestions of the native type.
     *
     * @param nativeType how the client sees the argument
     * @param parse      converts the parsed value
     * @param <N>        the native value
     * @param <T>        your value
     * @return the argument type
     */
    public static <N, T> ArgType<T> custom(final ArgumentType<N> nativeType, final Function<N, T> parse) {
        return custom(nativeType, parse, null);
    }

    /** At most ten values, then an ellipsis: enough to tell a sender what is allowed without flooding the chat. */
    private static String preview(final Collection<String> values) {
        final String joined = values.stream().limit(10).collect(Collectors.joining(", "));
        return values.size() > 10 ? joined + ", ..." : joined;
    }
}
