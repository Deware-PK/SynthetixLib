package com.github.dewarepk.synthetixlib.command;

import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.core.text.TextService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.lang.reflect.InvocationHandler;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.pointer.Pointers;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** Proxy senders, a Proxy {@link CommandSourceStack}, a host without a server and a dispatcher like Paper's. */
final class CommandFixtures {

    private CommandFixtures() {
    }

    /** What a sender received, in order; safe to share between threads. */
    static final class Inbox {
        final List<String> messages = Collections.synchronizedList(new ArrayList<>());

        List<String> copy() {
            synchronized (messages) {
                return List.copyOf(messages);
            }
        }
    }

    private static String plain(final Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static Object sender(final Class<?> type, final String name, final Set<String> permissions,
                                 final Inbox inbox, final Locale locale) {
        final InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "sendMessage" -> {
                final Object first = args[0];
                if (first instanceof ComponentLike like) {
                    inbox.messages.add(plain(like.asComponent()));
                } else if (first instanceof String text) {
                    inbox.messages.add(text);
                }
                yield null;
            }
            case "sendActionBar" -> {
                inbox.messages.add("actionbar:" + plain(((ComponentLike) args[0]).asComponent()));
                yield null;
            }
            case "getName" -> name;
            case "getUniqueId" -> UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
            case "hasPermission" -> permissions.contains("*") || permissions.contains((String) args[0]);
            case "locale" -> locale;
            case "pointers" -> Pointers.empty();
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> type.getSimpleName() + "(" + name + ")";
            default -> defaultValue(method.getReturnType());
        };
        return Proxy.newProxyInstance(CommandFixtures.class.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(final Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == float.class) {
            return 0F;
        }
        return null;
    }

    static CommandSender console(final Inbox inbox) {
        return (CommandSender) sender(ConsoleCommandSender.class, "CONSOLE", Set.of("*"), inbox, Locale.US);
    }

    static Player player(final String name, final Set<String> permissions, final Inbox inbox) {
        return (Player) sender(Player.class, name, permissions, inbox, Locale.US);
    }

    static CommandSourceStack source(final CommandSender sender) {
        return (CommandSourceStack) Proxy.newProxyInstance(CommandFixtures.class.getClassLoader(),
                new Class<?>[]{CommandSourceStack.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getSender" -> sender;
                    case "getExecutor" -> sender instanceof Player ? sender : null;
                    case "getLocation" -> new Location(null, 0, 0, 0);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /** A host without a server: messages are the defaults, log records are kept. */
    static final class TestHost implements CommandHost {
        final TextService text = TextService.create();
        final List<LogRecord> logs = Collections.synchronizedList(new ArrayList<>());
        final Logger logger = Logger.getAnonymousLogger();
        volatile boolean closed;
        /** Makes {@link #runAsync} throw, like the scheduler of a library that closed meanwhile. */
        volatile boolean failAsync;
        volatile Locale defaultLocale = Locale.US;
        /** The clock of the cooldowns; tests move it by hand. */
        final AtomicLong nanos = new AtomicLong(1_000_000_000L);
        /** Called right before a reservation is released; lets a test look at the state at that moment. */
        volatile Runnable onRelease;
        final Cooldowns cooldowns = new Cooldowns(nanos::get) {
            @Override
            void release(final UUID player, final String node, final Object token) {
                final Runnable hook = onRelease;
                if (hook != null) {
                    hook.run();
                }
                super.release(player, node, token);
            }
        };
        /** What {@link #runOnSender} was asked to run, as {@code sender name}. */
        final List<String> senderRuns = Collections.synchronizedList(new ArrayList<>());
        /** Names of the threads that ran async tasks. */
        final List<String> asyncThreads = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger asyncCounter = new AtomicInteger();
        private final AtomicInteger pending = new AtomicInteger();
        private final ExecutorService async = Executors.newCachedThreadPool(task -> {
            final Thread thread = new Thread(task, "fake-async-" + asyncCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });

        TestHost() {
            logger.setUseParentHandlers(false);
            logger.setLevel(java.util.logging.Level.ALL);
            logger.addHandler(new Handler() {
                @Override
                public void publish(final LogRecord record) {
                    logs.add(record);
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public Logger logger() {
            return logger;
        }

        @Override
        public TextService text() {
            return text;
        }

        @Override
        public SynthetixLib lib() {
            throw new UnsupportedOperationException("no library in unit tests");
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
            senderRuns.add(sender.getName());
            task.run();
        }

        /** Runs the task on a thread of its own, like the async scheduler does. */
        @Override
        public void runAsync(final Runnable task) {
            if (closed || failAsync) {
                throw new IllegalStateException("closed");
            }
            pending.incrementAndGet();
            async.execute(() -> {
                try {
                    asyncThreads.add(Thread.currentThread().getName());
                    task.run();
                } finally {
                    pending.decrementAndGet();
                }
            });
        }

        /** Waits until every async task queued so far has finished; the fake scheduler stays usable. */
        void awaitAsyncIdle() throws InterruptedException {
            final long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (pending.get() > 0) {
                if (System.nanoTime() > end) {
                    throw new AssertionError("async tasks did not finish");
                }
                Thread.sleep(2);
            }
        }

        /** Stops the fake async scheduler after letting what was queued finish. */
        void shutdownAsync() throws InterruptedException {
            async.shutdown();
            if (!async.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new AssertionError("async tasks did not finish");
            }
        }

        @Override
        public Component render(final @Nullable CommandSender sender,
                                final Function<CommandMessages, String> message, final TagResolver resolvers) {
            final String raw = message.apply(CommandMessages.defaults());
            return raw.isEmpty() ? Component.empty() : text.parse(raw, resolvers);
        }

        @Override
        public Message toMessage(final Component message) {
            return new LiteralMessage(plain(message));
        }

        @Override
        public Component fromMessage(final Message message) {
            return Component.text(message.getString());
        }
    }

    /**
     * A dispatcher with the specs registered the way Paper does it: the root literal, and every alias (and extra
     * name, e.g. a namespaced one) as a literal that redirects to it.
     */
    static CommandDispatcher<CommandSourceStack> dispatcher(final CommandHost host, final String extraName,
                                                            final CommandSpec... specs) {
        final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        for (final CommandSpec spec : specs) {
            final LiteralCommandNode<CommandSourceStack> node = spec.compile(host);
            dispatcher.getRoot().addChild(node);
            final List<String> names = new ArrayList<>(spec.aliases());
            if (extraName != null) {
                names.add(extraName + ":" + spec.name());
            }
            for (final String alias : names) {
                dispatcher.getRoot().addChild(Commands.literal(alias).requires(node.getRequirement())
                        .executes(node.getCommand()).redirect(node).build());
            }
        }
        return dispatcher;
    }

    static CommandDispatcher<CommandSourceStack> dispatcher(final CommandHost host, final CommandSpec... specs) {
        return dispatcher(host, null, specs);
    }

    /**
     * The shape of a node as text: {@code name} for a literal, {@code <name>} for an argument, {@code *} where a
     * command runs, children sorted by name in parentheses, {@code ->target} for a redirect.
     */
    static String shape(final CommandNode<CommandSourceStack> node) {
        final StringBuilder out = new StringBuilder();
        if (node instanceof ArgumentCommandNode<?, ?>) {
            out.append('<').append(node.getName()).append('>');
        } else {
            out.append(node.getName());
        }
        if (node.getCommand() != null) {
            out.append('*');
        }
        if (node.getRedirect() != null) {
            out.append("->").append(node.getRedirect().getName());
        }
        final List<CommandNode<CommandSourceStack>> children = node.getChildren().stream()
                .sorted(Comparator.comparing(CommandNode::getName)).toList();
        if (!children.isEmpty()) {
            out.append('(').append(children.stream().map(CommandFixtures::shape).collect(Collectors.joining(",")))
                    .append(')');
        }
        return out.toString();
    }

    static String shape(final CommandDispatcher<CommandSourceStack> dispatcher, final String name) {
        return shape(dispatcher.getRoot().getChild(name));
    }
}
