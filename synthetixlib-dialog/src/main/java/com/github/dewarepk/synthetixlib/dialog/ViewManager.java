package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.dialog.file.DialogInput;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.Nullable;

/**
 * Opens, tracks and closes the views of one {@link DialogService}: who has what open, the back stacks, the one-use
 * clicks and the threads. All per-viewer state is here or in the views (never static); it ends when a dialog ends, the
 * viewer quits, and when the service closes.
 *
 * <p>Whatever thread Paper fires a button callback on, the callback only checks who clicked, takes the one use of the
 * view and reads the answers; the steps and the handlers of the plugin run on the viewer's entity scheduler.
 */
final class ViewManager implements ViewHost {

    /** What {@code back} needs to open a dialog again. */
    record BackEntry(DialogType<?> type, @Nullable Object context) {
    }

    /** {@link DialogService#fallback}: viewers whose client cannot show a dialog get the handler instead. */
    record Fallback(Predicate<Player> cannotShow, BiConsumer<Player, DialogView<?>> handler) {
    }

    /** Most distinct problems that are logged while running; after that they stay quiet. */
    private static final int MAX_WARNINGS = 256;

    private final SynthetixLib lib;
    private final ViewRegistry<DialogViewImpl<?>, BackEntry> registry = new ViewRegistry<>();
    private final Listener listener = new Listener() {
    };
    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    private volatile @Nullable DialogMessages messages;
    private volatile DialogSettings settings = DialogSettings.defaults();
    private volatile @Nullable Fallback fallback;
    private volatile Function<String, @Nullable DialogType<?>> siblings = id -> null;
    private volatile @Nullable BiConsumer<Player, Runnable> executor;

    ViewManager(final SynthetixLib lib) {
        this.lib = lib;
        // No reflection: a plain executor, the way the gui and command modules hook the quit event.
        lib.plugin().getServer().getPluginManager().registerEvent(PlayerQuitEvent.class, listener,
                EventPriority.MONITOR, (l, event) -> {
                    if (event instanceof PlayerQuitEvent quit) {
                        drop(quit.getPlayer());
                    }
                }, lib.plugin());
    }

    // --- configuration ------------------------------------------------------------------------------------------

    void messages(final DialogMessages source) {
        this.messages = source;
    }

    void settings(final DialogSettings value) {
        this.settings = value;
    }

    void fallback(final Fallback value) {
        this.fallback = value;
    }

    void executor(final BiConsumer<Player, Runnable> value) {
        this.executor = value;
    }

    void siblings(final Function<String, @Nullable DialogType<?>> lookup) {
        this.siblings = lookup;
    }

    private DialogRenderer.Env env() {
        return new DialogRenderer.Env(lib.text(), lib.items(), () -> messages, () -> settings, this::warnOnce, warned,
                id -> siblings.apply(id));
    }

    // --- opening ------------------------------------------------------------------------------------------------

    /** Opens {@code type} for {@code player}; any thread. The dialog the player has open now is what {@code back} returns to. */
    @Override
    public <C> void open(final DialogType<C> type, final Player player, final @Nullable C context) {
        if (closed) {
            throw new IllegalStateException("DialogService is closed");
        }
        onPlayerThread(player, () -> openNow(type, player, context, true));
    }

    private <C> void openNow(final DialogType<C> type, final Player player, final @Nullable C context,
                             final boolean push) {
        if (closed) {
            return;
        }
        final DialogViewImpl<C> view = new DialogViewImpl<>(this, type, player, player.getName(), context, null);
        final Fallback fb = fallback;
        if (fb != null && fb.cannotShow().test(player)) {
            try {
                fb.handler().accept(player, view);
            } catch (final RuntimeException e) {
                lib.plugin().getLogger().log(Level.SEVERE, "[dialogs/" + type.id() + ".yml] the fallback threw", e);
            }
            return;
        }
        final Dialog dialog = renderOrNull(view);
        if (dialog == null) {
            return;
        }
        final DialogViewImpl<?> old = registry.get(player.getUniqueId());
        if (old != null) {
            old.state().end();
            if (push) {
                registry.push(player.getUniqueId(), new BackEntry(old.type(), old.context()));
            }
        }
        registry.put(player.getUniqueId(), view);
        player.showDialog(dialog);
    }

    @SuppressWarnings("unchecked")
    private <N> void openUntyped(final DialogType<N> type, final Player player, final @Nullable Object context,
                                 final boolean push) {
        openNow(type, player, (N) context, push);
    }

    /** Renders the current showing of {@code view}; a dialog Paper refuses is logged and not shown. */
    private <C> @Nullable Dialog renderOrNull(final DialogViewImpl<C> view) {
        final int generation = view.state().begin();
        try {
            return new DialogRenderer<>(env(), view.type(), view, generation, Set.of()).render();
        } catch (final RuntimeException e) {
            lib.plugin().getLogger().log(Level.SEVERE, "[dialogs/" + view.type().id() + ".yml] the dialog cannot be shown", e);
            return null;
        }
    }

    /** Shows the dialog of {@code root} again with fresh buttons; the answers of the last click are the initial values. */
    void reshow(final DialogViewImpl<?> root) {
        final Player player = root.playerOrNull();
        if (player == null) {
            return;
        }
        onPlayerThread(player, () -> {
            if (closed || !isCurrent(root) || root.state().isEnded()) {
                return;
            }
            final Dialog dialog = renderOrNull(root);
            if (dialog != null) {
                player.showDialog(dialog);
            }
        });
    }

    <N> void openNext(final DialogViewImpl<?> root, final DialogType<N> next, final @Nullable N context) {
        final Player player = root.playerOrNull();
        if (player != null) {
            onPlayerThread(player, () -> {
                if (isCurrent(root)) {
                    openNow(next, player, context, true);
                }
            });
        }
    }

    /** Ends the view and closes the dialog on the viewer's screen. */
    void closeView(final DialogViewImpl<?> root) {
        final Player player = root.playerOrNull();
        root.state().end();
        if (player == null) {
            return;
        }
        onPlayerThread(player, () -> {
            registry.remove(player.getUniqueId(), root);
            player.closeDialog();
        });
    }

    // --- clicks -------------------------------------------------------------------------------------------------

    boolean isCurrent(final DialogViewImpl<?> root) {
        final Player player = root.playerOrNull();
        return !closed && player != null && registry.get(player.getUniqueId()) == root;
    }

    /** The answers of the inputs of the dialog the click came from. */
    Map<String, Object> readAnswers(final DialogViewImpl<?> view, final DialogResponseView response) {
        final Map<String, Object> out = new HashMap<>();
        view.resolved().file().inputs().forEach((key, input) -> {
            try {
                final Object value = switch (input) {
                    case DialogInput.BoolInput ignored -> response.getBoolean(key);
                    case DialogInput.NumberInput ignored -> response.getFloat(key);
                    case DialogInput.TextInput ignored -> response.getText(key);
                    case DialogInput.OptionInput ignored -> response.getText(key);
                };
                if (value != null) {
                    out.put(key, value);
                }
            } catch (final RuntimeException e) {
                warnOnce("[dialogs/" + view.type().id() + ".yml] the answer of input '" + key + "' could not be read: " + e);
            }
        });
        return Map.copyOf(out);
    }

    /** Runs the steps of a button on the viewer's thread, then ends with close, reshow or the next dialog. */
    <C> void click(final DialogViewImpl<C> view, final List<com.github.dewarepk.synthetixlib.dialog.file.ClickAction> actions,
                   final boolean exit) {
        final DialogViewImpl<?> root = view.root();
        final Player player = view.playerOrNull();
        if (closed || player == null || !isCurrent(root)) {
            return;
        }
        view.enterClick();
        ClickFlow.Result result = new ClickFlow.Result(false, false);
        DialogViewImpl.Pending pending;
        try {
            result = ClickFlow.run(actions, steps(view, player), e -> lib.plugin().getLogger().log(Level.SEVERE,
                    "[dialogs/" + view.type().id() + ".yml] a button threw", e));
            final var onClose = view.type().registrations().onClose();
            if (exit && onClose != null) {
                try {
                    onClose.accept(view);
                } catch (final RuntimeException e) {
                    lib.plugin().getLogger().log(Level.SEVERE, "[dialogs/" + view.type().id() + ".yml] onClose threw", e);
                }
            }
        } finally {
            pending = view.leaveClick();
        }
        if (result.replaced()) {
            return; // another dialog took the screen; closing now would close that one
        }
        switch (pending) {
            case DialogViewImpl.Pending.Next next -> openUntyped(next.type(), player, next.context(), true);
            case DialogViewImpl.Pending.Reopen ignored -> reshow(root);
            case null, default -> closeView(root);
        }
    }

    private <C> ClickFlow.Steps steps(final DialogViewImpl<C> view, final Player player) {
        return new ClickFlow.Steps() {
            @Override
            public void command(final String command) {
                player.performCommand(command);
            }

            @Override
            public void console(final String command) {
                lib.scheduler().global().run(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
            }

            @Override
            public void message(final String text) {
                final Component parsed = new DialogRenderer<>(env(), view.type(), view, view.state().generation(),
                        Set.of()).parse(text);
                player.sendMessage(parsed);
            }

            @Override
            public void sound(final NamespacedKey key) {
                player.playSound(Sound.sound(key, Sound.Source.UI, 1f, 1f));
            }

            @Override
            public boolean open(final String dialog) {
                final DialogType<?> found = siblings.apply(dialog);
                if (found == null) {
                    warnOnce("[dialogs/" + view.type().id() + ".yml] open:" + dialog + ": no dialog with this id was built");
                    return false;
                }
                openUntyped(found, player, null, true);
                return true;
            }

            @Override
            public boolean menu(final String menu) {
                warnOnce("[dialogs/" + view.type().id() + ".yml] menu:" + menu + ": menus are not linked to this service");
                return false;
            }

            @Override
            public boolean back() {
                final BackEntry entry = registry.pop(player.getUniqueId());
                if (entry == null) {
                    return false;
                }
                openUntyped(entry.type(), player, entry.context(), false);
                return true;
            }

            @Override
            public void run(final String action) {
                final var registrations = view.type().registrations();
                final var handler = registrations.actions().get(action);
                if (handler != null) {
                    handler.accept(new ClickImpl<>(view));
                } else if (registrations.submits().containsKey(action)) {
                    warnOnce("[dialogs/" + view.type().id() + ".yml] action:" + action
                            + " is a submit, which reads the answers in a later version of the library");
                } else {
                    warnOnce("[dialogs/" + view.type().id() + ".yml] action:" + action + " is not registered");
                }
            }
        };
    }

    /** The click a handler is given. */
    private record ClickImpl<C>(DialogView<C> view) implements DialogClick<C> {
    }

    // --- previews and tests -------------------------------------------------------------------------------------

    /** Renders {@code type} for nobody (self-tests); the registered code is called with a view that has no player. */
    @Override
    public <C> Dialog preview(final DialogType<C> type, final String playerName, final @Nullable C context) {
        final DialogViewImpl<C> view = new DialogViewImpl<>(this, type, null, playerName, context, null);
        final int generation = view.state().begin();
        return new DialogRenderer<>(env(), type, view, generation, Set.of()).render();
    }

    /** Creates and renders the view of {@code player} and registers it as theirs, but shows nothing (self-tests). */
    @Override
    public <C> DialogViewImpl<C> attach(final DialogType<C> type, final Player player, final @Nullable C context) {
        final DialogViewImpl<C> view = new DialogViewImpl<>(this, type, player, player.getName(), context, null);
        final int generation = view.state().begin();
        new DialogRenderer<>(env(), type, view, generation, Set.of()).render();
        registry.put(player.getUniqueId(), view);
        return view;
    }

    // --- threads and logging ------------------------------------------------------------------------------------

    /** Runs {@code task} now if this thread owns the player, else on the player's entity scheduler. */
    void onPlayerThread(final Player player, final Runnable task) {
        final BiConsumer<Player, Runnable> custom = executor;
        if (custom != null) {
            custom.accept(player, task);
            return;
        }
        try {
            if (lib.scheduler().owns(player)) {
                task.run();
            } else {
                lib.scheduler().entity(player).run(task);
            }
        } catch (final IllegalStateException e) {
            // the library closed meanwhile: nothing left to show
        }
    }

    void debug(final String message) {
        lib.plugin().getLogger().fine(message);
    }

    void warnOnce(final String message) {
        if (warned.size() < MAX_WARNINGS && warned.add(message)) {
            lib.plugin().getLogger().warning(message);
        }
    }

    // --- lifecycle ----------------------------------------------------------------------------------------------

    private void drop(final Player player) {
        final DialogViewImpl<?> view = registry.remove(player.getUniqueId());
        if (view != null) {
            view.state().end();
        }
        registry.clearBack(player.getUniqueId());
    }

    /** Ends every view and closes the dialogs on their screens, then stops listening. Idempotent. */
    void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (final DialogViewImpl<?> view : registry.clearAll()) {
            view.state().end();
            final Player player = view.playerOrNull();
            if (player == null) {
                continue;
            }
            try {
                final BiConsumer<Player, Runnable> custom = executor;
                if (custom != null) {
                    custom.accept(player, player::closeDialog);
                } else if (lib.scheduler().owns(player)) {
                    player.closeDialog();
                } else {
                    lib.scheduler().entity(player).run(player::closeDialog);
                }
            } catch (final RuntimeException e) {
                lib.plugin().getLogger().log(Level.WARNING, "[dialogs/" + view.type().id() + ".yml] could not close the"
                        + " dialog of " + player.getName() + ": " + e);
            }
        }
        HandlerList.unregisterAll(listener);
    }

    /** @return how many viewers have a dialog open */
    int openViews() {
        return registry.size();
    }
}
