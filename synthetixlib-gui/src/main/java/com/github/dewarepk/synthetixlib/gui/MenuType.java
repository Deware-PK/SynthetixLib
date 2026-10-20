package com.github.dewarepk.synthetixlib.gui;

import com.github.dewarepk.synthetixlib.config.ReloadResult;
import com.github.dewarepk.synthetixlib.gui.file.MenuFile;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * A menu: its file plus the code registered for it. Immutable apart from the file contents that
 * {@link GuiService#reload()} swaps, and shared by every viewer; what belongs to one viewer lives in its
 * {@link MenuView}. Obtained from {@link MenuBuilder#build()}.
 *
 * <pre>{@code
 * MenuType<Void> auction = gui.menu("auction").placeholder("balance", view -> ...).build();
 * MenuFile file = auction.file();      // title, layout and items as parsed
 * auction.open(player);                // from any thread
 * }</pre>
 *
 * @param <C> the type of the context a view is opened for; {@link Void} if none
 */
public final class MenuType<C> {

    private final String id;
    private final Registrations<C> registrations;
    private final MenuLoader loader;
    private final ViewHost host;
    private final MenuRegistry registry;
    private volatile MenuLoader.Resolved resolved = MenuLoader.Resolved.empty();

    MenuType(final String id, final Registrations<C> registrations, final MenuLoader loader, final ViewHost host,
             final MenuRegistry registry) {
        this.id = id;
        this.registrations = registrations;
        this.loader = loader;
        this.host = host;
        this.registry = registry;
    }

    /** @return the id, which is also the name of the file {@code menus/<id>.yml} */
    public String id() {
        return id;
    }

    /** @return the menu file as parsed by the last load; an empty chest if the file has never been usable */
    public MenuFile file() {
        return resolved.file();
    }

    /**
     * @param slotChar a layout character
     * @return {@code true} if its item uses an action, list, state or placeholder that code did not register, so it
     *         does nothing
     */
    public boolean isInert(final char slotChar) {
        return resolved.inert().contains(slotChar);
    }

    /**
     * Opens the menu for {@code player}, who gets their own view of it. May be called from any thread: the menu opens
     * on the player's thread. If the player has a menu of this service open, that one is replaced and
     * {@code back} (in a click action or {@link MenuView#back()}) returns to it.
     *
     * <pre>{@code
     * auction.open(player);
     * }</pre>
     *
     * @param player the viewer
     * @throws IllegalStateException if the service is closed
     */
    public void open(final Player player) {
        open(player, null);
    }

    /**
     * Like {@link #open(Player)} for a menu that is opened for something; handlers read it as
     * {@link MenuView#context()}.
     *
     * <pre>{@code
     * bid.open(player, lot);       // view.context() is the lot
     * }</pre>
     *
     * @param player  the viewer
     * @param context what the menu is about, {@code null} for none
     * @throws IllegalStateException if the service is closed
     */
    public void open(final Player player, final @Nullable C context) {
        Objects.requireNonNull(player, "player");
        host.open(this, player, context);
    }

    /**
     * Renders the menu into an inventory that nobody sees: a viewer called {@code playerName} on page {@code page},
     * with a view that has no player. For self-tests; must run on a thread that may touch the server. The registered
     * placeholders, lists and states are called with that view, so they must not call {@link MenuView#player()}.
     *
     * @param playerName the value of {@code <player>}
     * @param context    the context of the view, {@code null} for none
     * @param page       the page, from 0; clamped to the pages that exist
     * @return a new inventory with the library's holder
     */
    @ApiStatus.Internal
    public Inventory preview(final String playerName, final @Nullable C context, final int page) {
        return host.preview(this, Objects.requireNonNull(playerName, "playerName"), context, page);
    }

    /**
     * The title {@link #preview} gives its inventory, for a self-test that checks what reaches the client (an inventory
     * cannot be asked for its title without a viewer). Same rules as {@link #preview}.
     *
     * @param playerName the value of {@code <player>}
     * @param context    the context of the view, {@code null} for none
     * @param page       the page, from 0
     * @return the title component
     */
    @ApiStatus.Internal
    public Component previewTitle(final String playerName, final @Nullable C context, final int page) {
        return host.previewTitle(this, Objects.requireNonNull(playerName, "playerName"), context, page);
    }

    /**
     * Creates the view of {@code player} and registers it as their open menu, but opens nothing for the client, so a
     * self-test with no real player can feed {@code InventoryClickEvent}s to the service's listener. Returns the inventory of
     * the view, which holds the menu with the library's holder. Must run on a thread that may touch the server.
     *
     * @param player  the viewer (a test double is fine; only identity, name and inventory are read)
     * @param context the context of the view, {@code null} for none
     * @return the inventory of the view
     */
    @ApiStatus.Internal
    public Inventory attach(final Player player, final @Nullable C context) {
        return host.attach(this, Objects.requireNonNull(player, "player"), context);
    }

    Registrations<C> registrations() {
        return registrations;
    }

    MenuLoader.Resolved resolved() {
        return resolved;
    }

    /** @return another menu of the same service, {@code null} if there is none with that id */
    @Nullable MenuType<?> sibling(final String id) {
        return registry.find(id);
    }

    /** Reads the file again; a file that cannot be used leaves the menu as it was. */
    ReloadResult reload() {
        final MenuLoader.Loaded loaded = loader.load(id, registrations, resolved);
        if (loaded.resolved() != null) {
            resolved = Objects.requireNonNull(loaded.resolved());
        }
        return loaded.report();
    }

    @Override
    public String toString() {
        return "MenuType[" + id + "]";
    }
}
