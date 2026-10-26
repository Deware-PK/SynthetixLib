package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.config.ReloadResult;
import com.github.dewarepk.synthetixlib.dialog.file.DialogFile;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import java.util.Objects;
import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * A dialog: its file plus the code registered for it. Immutable apart from the file contents that
 * {@link DialogService#reload()} swaps, and shared by every viewer. Obtained from {@link DialogBuilder#build()}.
 *
 * <pre>{@code
 * DialogType<Void> help = dialogs.dialog("help").build();
 * DialogFile file = help.file();      // title, body, inputs and buttons as parsed
 * }</pre>
 *
 * @param <C> the type of the context a view is opened for; {@link Void} if none
 */
public final class DialogType<C> {

    private final String id;
    private final Registrations<C> registrations;
    private final @Nullable DialogLoader loader;
    private final @Nullable ViewHost host;
    private volatile DialogLoader.Resolved resolved = DialogLoader.Resolved.empty();

    DialogType(final String id, final Registrations<C> registrations, final DialogLoader loader, final ViewHost host) {
        this.id = id;
        this.registrations = registrations;
        this.loader = loader;
        this.host = host;
    }

    /**
     * A dialog of the registry (bootstrap): its file was read already and never changes, so it has no loader.
     *
     * @param host {@code null} for the throwaway dialog the bootstrap renders from, which is never opened
     */
    DialogType(final String id, final Registrations<C> registrations, final DialogLoader.Resolved resolved,
               final @Nullable ViewHost host) {
        this.id = id;
        this.registrations = registrations;
        this.loader = null;
        this.host = host;
        this.resolved = resolved;
    }

    /** @return the id, which is also the name of the file {@code dialogs/<id>.yml} */
    public String id() {
        return id;
    }

    /** @return the dialog file as parsed by the last load; an empty notice if the file has never been usable */
    public DialogFile file() {
        return resolved.file();
    }

    /**
     * @param path the path of a button in the file, e.g. {@code buttons.yes} or {@code buttons.exit}
     * @return {@code true} if the button uses an action that code did not register, or a submit whose form does not
     *         match the inputs, so it does nothing
     */
    public boolean isInert(final String path) {
        return resolved.inert().contains(path);
    }

    /**
     * Shows the dialog to {@code player}, who gets their own view of it. May be called from any thread: the dialog is
     * shown on the player's thread. A dialog of this service that the player has open is replaced and {@code back} (in a
     * click action) returns to it. Nothing is shown, and the problem is logged, if the file describes something Paper
     * refuses.
     *
     * <pre>{@code
     * help.open(player);
     * }</pre>
     *
     * @param player the viewer
     * @throws IllegalStateException if the service is closed
     */
    public void open(final Player player) {
        open(player, null);
    }

    /**
     * Like {@link #open(Player)} for a dialog that is opened for something; handlers read it as
     * {@link DialogView#context()}.
     *
     * <pre>{@code
     * sell.open(player, player.getInventory().getItemInMainHand());
     * }</pre>
     *
     * @param player  the viewer
     * @param context what the dialog is about, {@code null} for none
     * @throws IllegalStateException if the service is closed
     */
    public void open(final Player player, final @Nullable C context) {
        Objects.requireNonNull(player, "player");
        requireHost().open(this, player, context);
    }

    /**
     * Builds the Paper dialog for a viewer that does not exist, so a self-test can check that every kind of dialog is
     * accepted by the server. Registered code is called with a view that has no player. Must run on a thread that may
     * touch the server.
     *
     * @param playerName the value of {@code <player>}
     * @param context    the context of the view, {@code null} for none
     * @return a new dialog; nothing is shown
     */
    @ApiStatus.Internal
    public Dialog preview(final String playerName, final @Nullable C context) {
        return requireHost().preview(this, Objects.requireNonNull(playerName, "playerName"), context);
    }

    /**
     * Creates and renders the view of {@code player} and registers it as theirs, but shows nothing, so a self-test with
     * no real client can click its buttons with {@link #click}. A test double is fine as the player (identity, name,
     * inventory and scheduler ownership are read). Must run on a thread that may touch the server.
     *
     * @param player  the viewer
     * @param context the context of the view, {@code null} for none
     * @return the view
     */
    @ApiStatus.Internal
    public DialogView<C> attach(final Player player, final @Nullable C context) {
        return requireHost().attach(this, Objects.requireNonNull(player, "player"), context);
    }

    /** The error lines (plain text) shown by the last rendering of {@code view}. For self-tests. */
    @ApiStatus.Internal
    public java.util.List<String> errorLines(final DialogView<C> view) {
        return ((DialogViewImpl<C>) view).errorTexts();
    }

    /**
     * Fires the callback of the current showing of a button of {@code view}, the same object that Paper's dialog holds,
     * as if {@code audience} clicked it with {@code response}. For self-tests.
     *
     * @param view     a view from {@link #attach}
     * @param path     the button, e.g. {@code buttons.yes}
     * @param response what the client sent
     * @param audience who clicked
     * @throws IllegalArgumentException if the showing has no such button
     */
    @ApiStatus.Internal
    public void click(final DialogView<C> view, final String path, final DialogResponseView response,
                      final Audience audience) {
        final var callback = ((DialogViewImpl<C>) view).callback(path);
        if (callback == null) {
            throw new IllegalArgumentException("the showing has no button " + path);
        }
        callback.accept(response, audience);
    }

    private ViewHost requireHost() {
        if (host == null) {
            throw new IllegalStateException("Dialog '" + id + "' is not shown by a service");
        }
        return host;
    }

    Registrations<C> registrations() {
        return registrations;
    }

    DialogLoader.Resolved resolved() {
        return resolved;
    }

    /** Reads the file again; a file that cannot be used leaves the dialog as it was. */
    ReloadResult reload() {
        if (loader == null) {
            return new ReloadResult(java.util.List.of(), java.util.List.of(), false); // registry dialogs are fixed until restart
        }
        final DialogLoader.Loaded loaded = loader.load(id, registrations, resolved);
        if (loaded.resolved() != null) {
            resolved = Objects.requireNonNull(loaded.resolved());
        }
        return loaded.report();
    }

    @Override
    public String toString() {
        return "DialogType[" + id + "]";
    }
}
