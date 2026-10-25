package com.github.dewarepk.synthetixlib.command;

import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.core.text.TextService;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.Nullable;

/**
 * Registers Brigadier commands for one plugin; one instance per {@link SynthetixLib}, closed with it.
 *
 * <pre>{@code
 * CommandService commands = CommandService.create(lib).messages(lang, Messages::commands);
 * commands.register(AuctionCommand.build(auctions, gui, lang));
 * }</pre>
 *
 * <p>The service registers one handler for Paper's {@code LifecycleEvents.COMMANDS} and builds the Brigadier nodes
 * from the stored specs every time Paper fires it (server start and {@code /minecraft:reload}), so registration is
 * idempotent. Paper has no runtime registration: {@link #register} must be called before the first event, in
 * {@code onEnable}. After {@link #close()} executions do nothing and return 0, and suggestions are empty, until the
 * next reload drops the commands.
 */
public final class CommandService implements AutoCloseable {

    /** One of this module's own messages, in the language of the sender. */
    @FunctionalInterface
    private interface BuiltInMessages {
        Component render(@Nullable CommandSender sender, Function<CommandMessages, String> message,
                         TagResolver resolvers);
    }

    /** How often expired cooldowns that nobody looked at again are dropped. */
    private static final Duration SWEEP_EVERY = Duration.ofMinutes(5);

    private final SynthetixLib lib;
    private final CommandRegistry registry = new CommandRegistry();
    private final Cooldowns cooldowns = new Cooldowns(System::nanoTime);
    private final CommandCooldowns cooldownView = new CommandCooldowns(cooldowns);
    private final SenderThreads threads;
    private final Listener quitListener = new Listener() {
    };
    private final CommandHost host = new Host();
    private volatile BuiltInMessages messages;
    private volatile Locale defaultLocale = Locale.US;
    private volatile boolean closed;

    private CommandService(final SynthetixLib lib) {
        this.lib = lib;
        this.threads = new SenderThreads(lib.scheduler(), () -> closed);
        final CommandMessages defaults = CommandMessages.defaults();
        this.messages = (sender, message, resolvers) -> {
            final String raw = message.apply(defaults);
            return raw.isEmpty() ? Component.empty() : lib.text().parse(raw, resolvers);
        };
    }

    /**
     * Creates the service, attaches it to {@code lib} (so {@code lib.close()} closes it) and hooks the COMMANDS
     * event of the plugin.
     *
     * @param lib the owning library instance
     * @return a new service bound to {@code lib}
     * @throws IllegalStateException if {@code lib} is already closed
     */
    public static CommandService create(final SynthetixLib lib) {
        Objects.requireNonNull(lib, "lib");
        final CommandService service = new CommandService(lib);
        lib.attach(service);
        lib.plugin().getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> service.onCommands(event.registrar()));
        // A player who quits takes their cooldowns with them (SnapPlug bug #4); no reflection: a plain executor.
        lib.plugin().getServer().getPluginManager().registerEvent(PlayerQuitEvent.class, service.quitListener,
                EventPriority.MONITOR, (listener, event) -> {
                    if (event instanceof PlayerQuitEvent quit) {
                        service.cooldowns.clear(quit.getPlayer().getUniqueId());
                    }
                }, lib.plugin());
        lib.scheduler().async().runTimer(SWEEP_EVERY, SWEEP_EVERY, task -> service.cooldowns.sweep());
        return service;
    }

    /**
     * Stores commands; they reach Brigadier when Paper fires the COMMANDS event, right after {@code onEnable}.
     *
     * @param specs the commands
     * @return this service
     * @throws IllegalStateException if the COMMANDS event already fired (register in {@code onEnable}) or the
     *                               service is closed
     */
    public CommandService register(final CommandSpec... specs) {
        Objects.requireNonNull(specs, "specs");
        if (closed) {
            throw new IllegalStateException("CommandService is closed");
        }
        registry.register(specs);
        return this;
    }

    /**
     * Takes this module's own messages ({@link CommandMessages}) from the product's lang files, in the language of
     * the sender. Without it {@link CommandMessages#defaults()} (English) is used.
     *
     * @param lang     the locale handle of the product
     * @param messages picks the {@link CommandMessages} out of the record, e.g. {@code Messages::commands}
     * @param <M>      the record of messages
     * @return this service
     */
    public <M> CommandService messages(final LocaleHandle<M> lang, final Function<M, CommandMessages> messages) {
        Objects.requireNonNull(lang, "lang");
        Objects.requireNonNull(messages, "messages");
        this.defaultLocale = lang.defaultLocale();
        this.messages = (sender, message, resolvers) -> {
            final Function<M, String> pick = record -> message.apply(messages.apply(record));
            return sender == null ? lang.get((Locale) null, pick, resolvers) : lang.get(sender, pick, resolvers);
        };
        return this;
    }

    private void onCommands(final Commands registrar) {
        for (final CommandSpec spec : registry.start()) {
            final LiteralCommandNode<CommandSourceStack> node = spec.compile(host);
            final String description = spec.description();
            final Set<String> registered = registrar.register(node, description, spec.aliases());
            if (!registered.contains(spec.name())) {
                lib.plugin().getLogger().warning("/" + spec.name() + " is already taken by another plugin; "
                        + "it is available as /" + lib.plugin().getPluginMeta().getName().toLowerCase(Locale.ROOT)
                        + ":" + spec.name());
            }
        }
    }

    /**
     * Reads and clears the cooldowns of this service, e.g. for an admin command that lets one player try again.
     *
     * @return the cooldowns; the same object every time
     */
    public CommandCooldowns cooldowns() {
        return cooldownView;
    }

    /** @return {@code true} after {@link #close()} */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Stops executions and suggestions and forgets all cooldowns. Idempotent; called by {@code lib.close()}.
     */
    @Override
    public void close() {
        closed = true;
        HandlerList.unregisterAll(quitListener);
        cooldowns.clearAll();
    }

    /** The surroundings of every execution of this service. */
    private final class Host implements CommandHost {

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public Logger logger() {
            return lib.plugin().getLogger();
        }

        @Override
        public TextService text() {
            return lib.text();
        }

        @Override
        public SynthetixLib lib() {
            return lib;
        }

        @Override
        public Locale defaultLocale() {
            return defaultLocale;
        }

        @Override
        public Cooldowns cooldowns() {
            return cooldowns;
        }

        @Override
        public void runOnSender(final CommandSender sender, final Runnable task) {
            threads.runOnSender(sender, task);
        }

        @Override
        public void runAsync(final Runnable task) {
            threads.runAsync(task);
        }

        @Override
        public Component render(final @Nullable CommandSender sender,
                                final Function<CommandMessages, String> message, final TagResolver resolvers) {
            return messages.render(sender, message, resolvers);
        }

        @Override
        public Message toMessage(final Component message) {
            return MessageComponentSerializer.message().serialize(message);
        }

        @Override
        public Component fromMessage(final Message message) {
            return MessageComponentSerializer.message().deserialize(message);
        }
    }
}
