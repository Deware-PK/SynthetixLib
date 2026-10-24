package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.dialog.file.ClickAction;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * One viewer's copy of an open dialog: who sees it, what it is about, what they answered at their last click and which
 * callbacks the current showing has. All per-viewer state lives here (never in a {@link DialogType} or a static).
 * A list dialog shows other dialogs inside itself; those get a child view that shares the {@link ViewState} of the
 * root, so the one-use rule covers the whole screen.
 */
final class DialogViewImpl<C> implements DialogView<C> {

    /** What a handler asked for last; applied when the click is over. */
    sealed interface Pending {
        record Close() implements Pending {
        }

        record Reopen() implements Pending {
        }

        record Next(DialogType<?> type, @Nullable Object context) implements Pending {
        }
    }

    private final ViewManager manager;
    private final DialogType<C> type;
    private final DialogLoader.Resolved resolved;
    private final @Nullable Player player;
    private final String playerName;
    private final @Nullable UUID playerId;
    private final @Nullable C context;
    private final ViewState state;
    /** The view the player sees; {@code this} unless this one is a dialog inside a list. */
    private final DialogViewImpl<?> root;
    /** Callbacks of the current showing by button path, so a self-test can click through the real code path. */
    private final Map<String, DialogActionCallback> callbacks = new ConcurrentHashMap<>();
    private volatile Map<String, Object> answers = Map.of();
    /** Only touched on the viewer's thread; a view inside a list uses the fields of its root. */
    private @Nullable Pending pending;
    private boolean inClick;

    /**
     * A view of a dialog shown to {@code player}; {@code player} is {@code null} for a preview.
     *
     * @param parent the view of the list this dialog is shown inside, {@code null} for a dialog of its own
     */
    DialogViewImpl(final ViewManager manager, final DialogType<C> type, final @Nullable Player player,
                   final String playerName, final @Nullable C context, final @Nullable DialogViewImpl<?> parent) {
        this.manager = manager;
        this.type = type;
        this.resolved = type.resolved();
        this.player = player;
        this.playerName = playerName;
        this.playerId = player == null ? null : player.getUniqueId();
        this.context = context;
        this.state = parent == null ? new ViewState() : parent.state;
        this.root = parent == null ? this : parent.root;
    }

    @Override
    public Player player() {
        if (player == null) {
            throw new IllegalStateException("this view has no player: it renders a preview");
        }
        return player;
    }

    @Override
    public @Nullable C context() {
        return context;
    }

    @Override
    public void reopen() {
        if (root.inClick) {
            root.pending = new Pending.Reopen();
        } else {
            manager.reshow(root);
        }
    }

    @Override
    public void close() {
        if (root.inClick) {
            root.pending = new Pending.Close();
        } else {
            manager.closeView(root);
        }
    }

    @Override
    public <N> void next(final DialogType<N> next, final @Nullable N nextContext) {
        if (root.inClick) {
            root.pending = new Pending.Next(next, nextContext);
        } else {
            manager.openNext(root, next, nextContext);
        }
    }

    // --- used by the manager and the renderer -------------------------------------------------------------------

    @Nullable Player playerOrNull() {
        return player;
    }

    String playerName() {
        return playerName;
    }

    DialogType<C> type() {
        return type;
    }

    DialogLoader.Resolved resolved() {
        return resolved;
    }

    ViewState state() {
        return state;
    }

    Map<String, Object> answers() {
        return answers;
    }

    void registerCallback(final String path, final DialogActionCallback callback) {
        callbacks.put(path, callback);
    }

    void clearCallbacks() {
        callbacks.clear();
    }

    @Nullable DialogActionCallback callback(final String path) {
        return callbacks.get(path);
    }

    /** Starts a click on the viewer's thread: {@code reopen}, {@code close} and {@code next} wait until it is over. */
    void enterClick() {
        root.inClick = true;
        root.pending = null;
    }

    /** @return what the handlers asked for, {@code null} if nothing */
    @Nullable Pending leaveClick() {
        root.inClick = false;
        final Pending result = root.pending;
        root.pending = null;
        return result;
    }

    /** @return the view the player sees: this one, or the list this dialog is inside */
    DialogViewImpl<?> root() {
        return root;
    }

    ViewManager manager() {
        return manager;
    }

    /**
     * What the callback of a button does when Paper fires it, on whatever thread that is: checks who clicked, takes the
     * one use of the view and remembers the answers, then hands the steps to the viewer's thread.
     */
    DialogActionCallback callbackOf(final int generation, final List<ClickAction> actions, final boolean exit) {
        return (response, audience) -> onCallback(generation, actions, exit, response, audience);
    }

    private void onCallback(final int generation, final List<ClickAction> actions, final boolean exit,
                            final DialogResponseView response, final Audience audience) {
        if (player == null || !isViewer(audience)) {
            manager.debug("[dialogs/" + type.id() + ".yml] ignored a click from somebody who is not the viewer");
            return;
        }
        if (!manager.isCurrent(root) || !type.resolved().equals(resolved)) {
            manager.debug("[dialogs/" + type.id() + ".yml] ignored a click on a dialog that is not current any more");
            return;
        }
        if (!state.tryConsume(generation)) {
            manager.debug("[dialogs/" + type.id() + ".yml] ignored a second click on the same showing");
            return;
        }
        answers = manager.readAnswers(this, response);
        manager.onPlayerThread(player, () -> manager.click(this, actions, exit));
    }

    private boolean isViewer(final Audience audience) {
        return audience instanceof Player clicker && clicker.getUniqueId().equals(playerId);
    }
}
