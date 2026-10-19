package com.github.dewarepk.synthetixlib.command;

import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** The context of one execution: created by {@link Executor}, never shared. */
final class CommandCallImpl implements CommandCall {

    private final CommandHost host;
    private final CommandContext<CommandSourceStack> context;
    private final Node node;
    private final String label;
    private final String input;
    private final CommandSender sender;
    private final Set<String> typed;
    private final TagResolver builtins;
    /** Arguments already read: a selector or position is resolved once per run, on the command thread. */
    private final ConcurrentHashMap<String, Object> values = new ConcurrentHashMap<>();
    /** The player this run's cooldown belongs to; {@code null} if the node has none for this sender. */
    private final @Nullable UUID cooldownPlayer;
    private final Object cooldownLock = new Object();
    /** Guarded by {@link #cooldownLock}: set by {@link #skipCooldown} and {@link #resetCooldown}. */
    private boolean cooldownSkipped;
    private volatile boolean async;

    CommandCallImpl(final CommandHost host, final CommandContext<CommandSourceStack> context, final Node node,
                    final String label, final String input) {
        this.host = host;
        this.context = context;
        this.node = node;
        this.label = label;
        this.input = input;
        this.sender = context.getSource().getSender();
        final Set<String> names = new HashSet<>();
        for (final ParsedCommandNode<CommandSourceStack> parsed : context.getNodes()) {
            if (parsed.getNode() instanceof ArgumentCommandNode<?, ?>) {
                names.add(parsed.getNode().getName());
            }
        }
        this.typed = Set.copyOf(names);
        this.builtins = Inputs.builtins(label, sender);
        final Node.Cooldown cooldown = node.cooldown();
        if (cooldown != null && sender instanceof Player player
                && (cooldown.bypassPermission() == null || !player.hasPermission(cooldown.bypassPermission()))) {
            this.cooldownPlayer = player.getUniqueId();
        } else {
            this.cooldownPlayer = null;
        }
    }

    // --- who ran it ---------------------------------------------------------------------------------------------

    @Override
    public CommandSender sender() {
        return sender;
    }

    @Override
    public boolean isPlayer() {
        return sender instanceof Player;
    }

    @Override
    public boolean isConsole() {
        return sender instanceof ConsoleCommandSender;
    }

    @Override
    public Player player() {
        if (sender instanceof Player player) {
            return player;
        }
        throw new IllegalStateException("/" + input + " was not run by a player but by " + sender.getName()
                + "; mark the node playerOnly() or use playerIfPresent()");
    }

    @Override
    public Optional<Player> playerIfPresent() {
        return sender instanceof Player player ? Optional.of(player) : Optional.empty();
    }

    @Override
    public boolean hasPermission(final String permission) {
        return sender.hasPermission(Objects.requireNonNull(permission, "permission"));
    }

    @Override
    public Locale locale() {
        return sender instanceof Player player ? player.locale() : host.defaultLocale();
    }

    // --- where --------------------------------------------------------------------------------------------------

    @Override
    public CommandSourceStack source() {
        return context.getSource();
    }

    @Override
    public @Nullable Entity executor() {
        return context.getSource().getExecutor();
    }

    @Override
    public Location location() {
        return context.getSource().getLocation();
    }

    // --- what was typed -----------------------------------------------------------------------------------------

    @Override
    public <T> T get(final Arg<T> arg) {
        Objects.requireNonNull(arg, "arg");
        final Node.Step step = node.step(arg.name());
        if (step == null) {
            throw new IllegalArgumentException(arg + " is not an argument of /" + input);
        }
        if (typed.contains(arg.name())) {
            return read(arg);
        }
        if (step.optional()) {
            @SuppressWarnings("unchecked")
            final T fallback = (T) step.fallback();
            return fallback;
        }
        throw new IllegalStateException(arg + " was not typed in /" + input);
    }

    @Override
    public <T> T get(final String name, final Class<T> type) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        final Node.Step step = node.step(name);
        if (step == null) {
            throw new IllegalArgumentException("\"" + name + "\" is not an argument of /" + input);
        }
        final Object value = get(step.arg());
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("argument \"" + name + "\" is a " + value.getClass().getSimpleName()
                    + ", not a " + type.getSimpleName());
        }
        return type.cast(value);
    }

    @Override
    public <T> Optional<T> optional(final Arg<T> arg) {
        Objects.requireNonNull(arg, "arg");
        return typed.contains(arg.name()) ? Optional.of(read(arg)) : Optional.empty();
    }

    @Override
    public <T> T getOrDefault(final Arg<T> arg, final T fallback) {
        Objects.requireNonNull(fallback, "fallback");
        return optional(arg).orElse(fallback);
    }

    @Override
    public boolean has(final Arg<?> arg) {
        return typed.contains(Objects.requireNonNull(arg, "arg").name());
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public String input() {
        return input;
    }

    private <T> T read(final Arg<T> arg) {
        final Object cached = values.get(arg.name());
        if (cached != null) {
            @SuppressWarnings("unchecked")
            final T value = (T) cached;
            return value;
        }
        final T value;
        try {
            value = ArgTypeImpl.readFrom(arg.type(), context, arg.name(), host);
        } catch (final CommandSyntaxException e) {
            throw new CommandFailure(host.fromMessage(e.getRawMessage()));
        }
        values.put(arg.name(), value);
        return value;
    }

    /**
     * Reads every typed argument now. {@code executesAsync} calls it on the command thread so the handler only finds
     * ready values.
     *
     * @throws CommandFailure if a typed value cannot be converted
     */
    void readAll() {
        for (final Node.Step step : node.steps()) {
            if (typed.contains(step.arg().name())) {
                read(step.arg());
            }
        }
    }

    // --- answering ----------------------------------------------------------------------------------------------

    private TagResolver all(final TagResolver[] resolvers) {
        return TagResolver.resolver(TagResolver.resolver(resolvers), builtins);
    }

    @Override
    public void reply(final String miniMessage, final TagResolver... resolvers) {
        host.text().send(sender, miniMessage, all(resolvers));
    }

    /** Sends one of the module's own messages in the sender's language; nothing if it is disabled. */
    void replyBuiltIn(final Function<CommandMessages, String> message, final TagResolver... resolvers) {
        final Component component = host.render(sender, message, all(resolvers));
        if (!Component.empty().equals(component)) {
            sender.sendMessage(component);
        }
    }

    @Override
    public void reply(final Component message) {
        sender.sendMessage(Objects.requireNonNull(message, "message"));
    }

    @Override
    public <M> void reply(final LocaleHandle<M> lang, final Function<M, String> message,
                          final TagResolver... resolvers) {
        lang.send(sender, message, all(resolvers));
    }

    @Override
    public <M> void replyLines(final LocaleHandle<M> lang, final Function<M, List<String>> lines,
                               final TagResolver... resolvers) {
        lang.sendLines(sender, lines, all(resolvers));
    }

    @Override
    public void actionBar(final String miniMessage, final TagResolver... resolvers) {
        sender.sendActionBar(host.text().parse(miniMessage, all(resolvers)));
    }

    @Override
    public CommandFailure fail(final String miniMessage, final TagResolver... resolvers) {
        return new CommandFailure(host.text().parse(miniMessage, all(resolvers)));
    }

    @Override
    public <M> CommandFailure fail(final LocaleHandle<M> lang, final Function<M, String> message,
                                   final TagResolver... resolvers) {
        return new CommandFailure(lang.get(sender, message, all(resolvers)));
    }

    // --- cooldown / threads -------------------------------------------------------------------------------------

    @Override
    public Duration cooldownLeft() {
        return cooldownPlayer == null ? Duration.ZERO : host.cooldowns().left(cooldownPlayer, node.path());
    }

    @Override
    public void resetCooldown() {
        if (cooldownPlayer != null) {
            synchronized (cooldownLock) {
                // Skipping as well: a reset before the handler returns must not be undone by the start after it.
                cooldownSkipped = true;
                host.cooldowns().reset(cooldownPlayer, node.path());
            }
        }
    }

    @Override
    public void skipCooldown() {
        synchronized (cooldownLock) {
            cooldownSkipped = true;
        }
    }

    /** The handler returned normally: starts the cooldown unless it was skipped or reset. */
    void finished() {
        final Node.Cooldown cooldown = node.cooldown();
        if (cooldownPlayer == null || cooldown == null) {
            return;
        }
        synchronized (cooldownLock) {
            if (!cooldownSkipped) {
                host.cooldowns().start(cooldownPlayer, node.path(), cooldown.duration());
            }
        }
    }

    /** Marks this run as the handler of {@code executesAsync}. */
    void markAsync() {
        async = true;
    }

    @Override
    public boolean isAsync() {
        return async;
    }

    @Override
    public void runOnSender(final Runnable task) {
        host.runOnSender(sender, Objects.requireNonNull(task, "task"));
    }

    @Override
    public SynthetixLib lib() {
        return host.lib();
    }
}
