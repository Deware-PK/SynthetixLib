package com.github.dewarepk.synthetixlib.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The fluent methods shared by {@link CommandSpec#literal the root} and {@link SubSpec#literal subcommands}.
 * Builders are for one thread (your {@code onEnable}); what they produce is immutable.
 *
 * <p>Shape of a node: the literal, then its arguments in order, then {@link #executes the handler}; subcommands hang
 * off the literal itself. Brigadier has no optional arguments, so an {@link #optional optional} argument makes the
 * node runnable with and without it.
 *
 * <p>{@link #permission}, {@link #playerOnly}, {@link #allowConsole} and {@link #cooldown} are inherited by
 * subcommands unless they set their own. Brigadier checks the permission of <em>every</em> node on the path, so a subcommand can ask for more
 * than its parent but never for less.
 *
 * @param <B> the concrete builder, for chaining
 */
public abstract sealed class SpecBuilder<B extends SpecBuilder<B>> permits SubSpec, CommandSpec.Builder {

    private final String name;
    private final List<Node.Step> steps = new ArrayList<>();
    private final List<Supplier<SubSpec>> children = new ArrayList<>();
    private @Nullable CommandHandler handler;
    private boolean async;
    private Node.@Nullable Cooldown cooldown;
    private @Nullable String permission;
    private @Nullable Boolean playerOnly;
    private @Nullable String usage;
    private @Nullable String description;
    private boolean helpSub;

    SpecBuilder(final String name) {
        this.name = Names.check(name, "command name");
    }

    abstract B self();

    /**
     * Adds a required argument after the previous ones.
     *
     * @param arg the argument handle
     * @return this builder
     * @throws IllegalStateException if an {@link #optional optional} argument was already added
     */
    public B argument(final Arg<?> arg) {
        Objects.requireNonNull(arg, "arg");
        if (!steps.isEmpty() && steps.get(steps.size() - 1).optional()) {
            throw new IllegalStateException("/" + name + ": a required argument cannot follow an optional one");
        }
        steps.add(new Node.Step(arg, false, null));
        return self();
    }

    /**
     * Adds an argument the sender may leave out: the node runs with and without it.
     * {@link CommandCall#get(Arg)} returns {@code fallback} when it is missing.
     *
     * @param arg      the argument handle
     * @param fallback the value used when it is missing
     * @param <T>      the value type
     * @return this builder
     */
    public <T> B optional(final Arg<T> arg, final T fallback) {
        Objects.requireNonNull(arg, "arg");
        Objects.requireNonNull(fallback, "fallback");
        steps.add(new Node.Step(arg, true, fallback));
        return self();
    }

    /**
     * Adds a subcommand written as its own class. {@link SubCommand#spec()} is called once, when this spec is built.
     *
     * @param sub the subcommand
     * @return this builder
     */
    public B sub(final SubCommand sub) {
        Objects.requireNonNull(sub, "sub");
        children.add(sub::spec);
        return self();
    }

    /**
     * Adds an inline subcommand, for one-liners.
     *
     * @param sub the subcommand
     * @return this builder
     */
    public B sub(final SubSpec sub) {
        Objects.requireNonNull(sub, "sub");
        children.add(() -> sub);
        return self();
    }

    /**
     * Sets what runs once all required arguments are typed. Runs on the thread Paper executes the command on.
     *
     * @param handler the handler
     * @return this builder
     * @throws IllegalStateException if the node already has a handler
     */
    public B executes(final CommandHandler handler) {
        Objects.requireNonNull(handler, "handler");
        if (this.handler != null) {
            throw new IllegalStateException("/" + name + " already has a handler");
        }
        this.handler = handler;
        return self();
    }

    /**
     * Like {@link #executes}, but the handler runs on {@code lib.scheduler().async()}, off the server threads, so it
     * may block (database, HTTP). The arguments are parsed and read on the command thread before the handler is
     * scheduled, so {@link CommandCall#get} is safe in it; anything else that touches players or the world must hop
     * back with {@link CommandCall#runOnSender}. The cooldown starts when the handler returns normally; an exception
     * is logged and the sender gets {@link CommandMessages#error()}, like in {@link #executes}.
     *
     * @param handler the handler
     * @return this builder
     * @throws IllegalStateException if the node already has a handler
     */
    public B executesAsync(final CommandHandler handler) {
        executes(handler);
        this.async = true;
        return self();
    }

    /**
     * The node is unavailable for {@code duration} after a successful run, per player and per executable node
     * ({@code /auction bid} and {@code /auction sell} have separate cooldowns). Inherited by subcommands unless they
     * set their own; {@link Duration#ZERO} removes an inherited cooldown. The console and command blocks have none.
     *
     * @param duration how long to wait, not negative
     * @return this builder
     */
    public B cooldown(final Duration duration) {
        return cooldown(duration, null);
    }

    /**
     * Like {@link #cooldown(Duration)}; senders with {@code bypassPermission} are never put on cooldown.
     *
     * @param duration         how long to wait, not negative
     * @param bypassPermission the permission that skips the cooldown, {@code null} for none
     * @return this builder
     */
    public B cooldown(final Duration duration, final @Nullable String bypassPermission) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("/" + name + ": cooldown must not be negative");
        }
        if (bypassPermission != null && bypassPermission.isBlank()) {
            throw new IllegalArgumentException("/" + name + ": bypass permission must not be blank");
        }
        this.cooldown = new Node.Cooldown(duration, bypassPermission);
        return self();
    }

    /**
     * Hides the node from sender without the permission and refuses it. Inherited by subcommands.
     *
     * @param permission the permission node
     * @return this builder
     */
    public B permission(final String permission) {
        Objects.requireNonNull(permission, "permission");
        if (permission.isBlank()) {
            throw new IllegalArgumentException("permission must not be blank");
        }
        this.permission = permission;
        return self();
    }

    /**
     * Non-players get {@link CommandMessages#playerOnly()} (the node stays visible to them). Inherited by
     * subcommands.
     *
     * @return this builder
     */
    public B playerOnly() {
        this.playerOnly = Boolean.TRUE;
        return self();
    }

    /**
     * Lets the console and command blocks run this node, overriding an inherited {@link #playerOnly()}.
     *
     * @return this builder
     */
    public B allowConsole() {
        this.playerOnly = Boolean.FALSE;
        return self();
    }

    /**
     * @param usage the arguments for the help, e.g. {@code <amount>}
     * @return this builder
     */
    public B usage(final String usage) {
        this.usage = Objects.requireNonNull(usage, "usage");
        return self();
    }

    /**
     * @param description one line for the help; on the root also Paper's command description
     * @return this builder
     */
    public B description(final String description) {
        this.description = Objects.requireNonNull(description, "description");
        return self();
    }

    /**
     * Adds {@code /<command> help}, which lists the subcommands the sender may use, one line each from their
     * {@link #usage} and {@link #description}. Groups below this node without a handler of their own show the
     * same list for their branch.
     *
     * <pre>{@code
     * CommandSpec.literal("auction")
     *         .sub(SubSpec.literal("bid").argument(amount).usage("<amount>").description("Bid on the lot")
     *                 .executes(this::bid))
     *         .helpSub()
     *         .build();
     * }</pre>
     *
     * @return this builder
     */
    public B helpSub() {
        this.helpSub = true;
        return self();
    }

    /**
     * Freezes this builder (and, recursively, the subcommands) into immutable nodes.
     *
     * @param parentPermission  effective permission of the parent
     * @param parentPermissions every distinct permission on the path down to the parent
     * @param parentPlayerOnly effective player-only flag of the parent
     * @param parentCooldown   effective cooldown of the parent, {@code null} if none
     * @param parentHelp        whether a node above has {@link #helpSub()}
     * @param parentPath        space-separated names above this node, the key of its cooldowns and used in messages
     * @return the node
     * @throws IllegalArgumentException if the node is not usable, e.g. duplicate children
     */
    final Node toNode(final @Nullable String parentPermission, final List<String> parentPermissions,
                      final boolean parentPlayerOnly, final Node.@Nullable Cooldown parentCooldown,
                      final boolean parentHelp, final String parentPath) {
        final String path = parentPath.isEmpty() ? name : parentPath + " " + name;
        final String effectivePermission = permission != null ? permission : parentPermission;
        final List<String> permissions = new ArrayList<>(parentPermissions);
        if (permission != null && !permissions.contains(permission)) {
            permissions.add(permission);
        }
        final boolean effectivePlayerOnly = playerOnly != null ? playerOnly : parentPlayerOnly;
        final Node.Cooldown effectiveCooldown;
        if (cooldown == null) {
            effectiveCooldown = parentCooldown;
        } else {
            effectiveCooldown = cooldown.duration().isZero() ? null : cooldown;
        }

        if (handler == null && !steps.isEmpty()) {
            throw new IllegalArgumentException("/" + path + " has arguments but no handler");
        }
        if (handler == null && children.isEmpty()) {
            throw new IllegalArgumentException("/" + path + " has neither a handler nor subcommands");
        }
        final Set<String> argNames = new HashSet<>();
        for (int i = 0; i < steps.size(); i++) {
            final Arg<?> arg = steps.get(i).arg();
            if (!argNames.add(arg.name())) {
                throw new IllegalArgumentException("/" + path + " has two arguments named \"" + arg.name() + "\"");
            }
            if (isGreedy(arg) && i != steps.size() - 1) {
                throw new IllegalArgumentException("/" + path + ": greedy argument \"" + arg.name()
                        + "\" must be the last one");
            }
        }

        final List<Node> built = new ArrayList<>();
        final Set<String> childNames = new HashSet<>();
        for (final Supplier<SubSpec> child : children) {
            final SubSpec spec = Objects.requireNonNull(child.get(), "SubCommand.spec() returned null in /" + path);
            final Node node = spec.toNode(effectivePermission, permissions, effectivePlayerOnly, effectiveCooldown,
                    helpSub || parentHelp, path);
            if (!childNames.add(node.name())) {
                throw new IllegalArgumentException("/" + path + " has two subcommands named \"" + node.name() + "\"");
            }
            built.add(node);
        }

        CommandHandler nodeHandler = handler;
        Node.Cooldown nodeCooldown = effectiveCooldown;
        if (helpSub || (parentHelp && handler == null)) {
            final Help help = new Help(path, built);
            if (handler == null) {
                nodeHandler = help;       // a group shows the list of its branch
                nodeCooldown = null;
            }
            if (helpSub) {
                if (!childNames.add("help")) {
                    throw new IllegalArgumentException("/" + path + " has helpSub() and a subcommand named \"help\"");
                }
                built.add(new Node("help", path + " help", null, null, effectivePermission, permissions,
                        effectivePlayerOnly, null, List.of(), help, false, List.of()));
            }
        }
        return new Node(name, path, description, usage, effectivePermission, permissions, effectivePlayerOnly,
                nodeCooldown, steps, nodeHandler, async, built);
    }

    private static boolean isGreedy(final Arg<?> arg) {
        return arg.type().nativeType() instanceof StringArgumentType string
                && string.getType() == StringArgumentType.StringType.GREEDY_PHRASE;
    }
}
