package com.github.dewarepk.synthetixlib.gui;

import com.github.dewarepk.synthetixlib.core.ScreenOpener;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.gui.file.ClickAction;
import com.github.dewarepk.synthetixlib.core.scheduler.SynthetixTask;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Opens, tracks and closes the views of one {@link GuiService}: the registry of who has what open, the back stacks and
 * the events that end a view. All per-viewer state is here (never static); it ends when the viewer closes the menu,
 * quits, dies or changes world, and when the service closes.
 *
 * <p>Every click, drag and item move that touches a menu goes through {@link ClickPolicy}: the listener only turns the
 * event into facts and does what the verdict says. Input items are handed back whenever a view ends, once.
 */
final class ViewManager implements ViewHost {

    /** What {@code back} needs to open a menu again. */
    record BackEntry(MenuType<?> type, @Nullable Object context, int page) {
    }

    /** Most distinct problems that are logged while rendering; after that they stay quiet. */
    private static final int MAX_WARNINGS = 256;

    private final SynthetixLib lib;
    private final Map<String, ScreenOpener> links;
    private final BukkitStacks stacks = new BukkitStacks();
    private final ViewRegistry<MenuViewImpl<?>, BackEntry> registry = new ViewRegistry<>();
    private final Listener listener = new Listener() {
    };
    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    private volatile @Nullable GuiMessages messages;

    ViewManager(final SynthetixLib lib, final Map<String, ScreenOpener> links) {
        this.lib = lib;
        this.links = links;
        final var plugins = lib.plugin().getServer().getPluginManager();
        // No reflection: plain executors (the same way the command module hooks the quit event). Click safety runs last
        // (HIGHEST) so that it has the final word over plugins that looked at the event before.
        plugins.registerEvent(InventoryClickEvent.class, listener, EventPriority.HIGHEST, (l, event) -> {
            if (event instanceof InventoryClickEvent click) {
                onClick(click);
            }
        }, lib.plugin());
        plugins.registerEvent(InventoryDragEvent.class, listener, EventPriority.HIGHEST, (l, event) -> {
            if (event instanceof InventoryDragEvent drag) {
                onDrag(drag);
            }
        }, lib.plugin());
        plugins.registerEvent(InventoryMoveItemEvent.class, listener, EventPriority.HIGHEST, (l, event) -> {
            if (event instanceof InventoryMoveItemEvent move && (isMenu(move.getDestination())
                    || isMenu(move.getSource()) || isMenu(move.getInitiator()))) {
                move.setCancelled(true); // a hopper or dropper next to nothing: a menu has no block, but plugins fire this
            }
        }, lib.plugin());
        plugins.registerEvent(InventoryPickupItemEvent.class, listener, EventPriority.HIGHEST, (l, event) -> {
            if (event instanceof InventoryPickupItemEvent pickup && isMenu(pickup.getInventory())) {
                pickup.setCancelled(true);
            }
        }, lib.plugin());
        plugins.registerEvent(InventoryCloseEvent.class, listener, EventPriority.MONITOR, (l, event) -> {
            if (event instanceof InventoryCloseEvent close) {
                onClose(close);
            }
        }, lib.plugin());
        plugins.registerEvent(PlayerQuitEvent.class, listener, EventPriority.MONITOR, (l, event) -> {
            if (event instanceof PlayerQuitEvent quit) {
                dropViewOf(quit.getPlayer(), false);
            }
        }, lib.plugin());
        // HIGH, before the plugins that read the drops at MONITOR: the input items join the drops (or the kept inventory)
        plugins.registerEvent(PlayerDeathEvent.class, listener, EventPriority.HIGH, (l, event) -> {
            if (event instanceof PlayerDeathEvent death) {
                onDeath(death);
            }
        }, lib.plugin());
        plugins.registerEvent(PlayerChangedWorldEvent.class, listener, EventPriority.MONITOR, (l, event) -> {
            if (event instanceof PlayerChangedWorldEvent changed) {
                dropViewOf(changed.getPlayer(), true);
            }
        }, lib.plugin());
    }

    BukkitStacks stacks() {
        return stacks;
    }

    private static boolean isMenu(final Inventory inventory) {
        return inventory.getHolder(false) instanceof ViewHolder;
    }

    // ------------------------------------------------------------------------------------------ opening

    @Override
    public void open(final MenuType<?> type, final Player player, final @Nullable Object context) {
        if (closed) {
            throw new IllegalStateException("GuiService is closed");
        }
        onPlayerThread(player, () -> openAs(type, player, context, 0, true));
    }

    /** Opens a view; the viewer's thread. {@code push} remembers the menu that is replaced, for {@code back}. */
    @SuppressWarnings("unchecked")
    private <C> void openAs(final MenuType<C> type, final Player player, final @Nullable Object context,
                            final int page, final boolean push) {
        if (closed || !player.isOnline()) {
            return;
        }
        final UUID id = player.getUniqueId();
        final MenuViewImpl<?> old = registry.get(id);
        final MenuViewImpl<C> view = new MenuViewImpl<>(this, type, player, (C) context, page);
        view.start();
        registry.put(id, view);
        // The close event of the menu that is replaced arrives during this call: it hands that menu's input items back
        // and does nothing else, because that menu is not the current one any more.
        final InventoryView opened = player.openInventory(view.inventory());
        if (opened == null) {
            view.dispose();
            if (old != null) {
                registry.put(id, old);
            } else {
                registry.remove(id, view);
            }
            return;
        }
        if (old != null) {
            if (push) {
                registry.push(id, old.backEntry());
            }
            old.dispose();
            giveBack(old); // normally done by its close event already; nothing is left then
        }
    }

    /** {@code <prefix>:<id>}: asks the module linked to the prefix to open its screen; the menu closes by itself. */
    void openLinked(final MenuViewImpl<?> from, final ClickAction.Linked link) {
        final ScreenOpener opener = links.get(link.prefix());
        if (opener == null) {
            warnOnce("[menus/" + from.type().id() + ".yml] " + link.prefix() + ":" + link.id() + " but nothing is linked to '"
                    + link.prefix() + "'");
            return;
        }
        if (!opener.open(from.player(), link.id())) {
            warnOnce("[menus/" + from.type().id() + ".yml] " + link.prefix() + ":" + link.id() + " but there is no screen '"
                    + link.id() + "'");
        }
    }

    /** {@code open:<menu>}: opens another menu of this service without a context; the previous one stays reachable. */
    void openMenu(final MenuViewImpl<?> from, final String menuId) {
        final MenuType<?> target = from.type().sibling(menuId);
        if (target == null) {
            warnOnce("[menus/" + from.type().id() + ".yml] open:" + menuId + " but there is no menu '" + menuId + "'");
            return;
        }
        openAs(target, from.player(), null, 0, true);
    }

    /** Opens the menu of the back stack, or closes if it is empty. */
    void backNow(final MenuViewImpl<?> view) {
        final BackEntry entry = registry.pop(view.player().getUniqueId());
        if (entry == null) {
            closeNow(view);
            return;
        }
        openAs(entry.type(), view.player(), entry.context(), entry.page(), false);
    }

    /** Opens a new inventory of a view (a changed title or shape); {@code false} if a plugin refused it. */
    boolean openInventory(final Player player, final Inventory inventory) {
        return player.openInventory(inventory) != null;
    }

    void closeNow(final MenuViewImpl<?> view) {
        final Player player = view.player();
        final UUID id = player.getUniqueId();
        if (registry.remove(id, view)) {
            registry.clearBack(id);
        }
        view.dispose();
        giveBack(view);
        if (player.getOpenInventory().getTopInventory() == view.inventory()) {
            player.closeInventory();
        }
    }

    /** Ends the view of a player that is gone. */
    void forget(final Player player) {
        dropViewOf(player, false);
    }

    private void dropViewOf(final Player player, final boolean closeInventory) {
        final UUID id = player.getUniqueId();
        final MenuViewImpl<?> view = registry.remove(id);
        registry.clearBack(id);
        if (view == null) {
            return;
        }
        view.dispose();
        giveBack(view);
        if (closeInventory && player.getOpenInventory().getTopInventory() == view.inventory()) {
            player.closeInventory();
        }
    }

    /** A viewer who dies: the input items drop with the rest of their loot, or stay in a kept inventory. */
    private void onDeath(final PlayerDeathEvent death) {
        final Player player = death.getEntity();
        final UUID id = player.getUniqueId();
        final MenuViewImpl<?> view = registry.remove(id);
        registry.clearBack(id);
        if (view == null) {
            return;
        }
        view.dispose();
        final List<ItemStack> items = view.drainInputs();
        if (death.getKeepInventory()) {
            handBack(player, items);
        } else {
            death.getDrops().addAll(items);
        }
        if (player.getOpenInventory().getTopInventory() == view.inventory()) {
            player.closeInventory();
        }
    }

    // ------------------------------------------------------------------------------------------ input items

    /** Hands the input items of a view that ends back to its viewer; once: the next call finds the slots empty. */
    private void giveBack(final MenuViewImpl<?> view) {
        final List<ItemStack> items = view.drainInputs();
        if (!items.isEmpty()) {
            handBack(view.player(), items);
        }
    }

    /** Puts the items into the player's inventory and drops what does not fit at the player. */
    void handBack(final Player player, final List<ItemStack> items) {
        Inputs.handBack(stacks, items, stack -> {
            final Map<Integer, ItemStack> rest = player.getInventory().addItem(stack);
            return rest.isEmpty() ? null : rest.values().iterator().next();
        }, stack -> player.getWorld().dropItem(player.getLocation(), stack));
    }

    // ------------------------------------------------------------------------------------------ events

    /** @return the view of an inventory of ours that is the one its viewer sees now, else {@code null} */
    private @Nullable MenuViewImpl<?> viewOf(final Inventory top, final org.bukkit.entity.HumanEntity who) {
        if (!(top.getHolder(false) instanceof ViewHolder holder)) {
            return null;
        }
        final MenuViewImpl<?> view = holder.view();
        if (view == null || view.inventory() != top || !(who instanceof Player player) || !player.equals(view.player())) {
            return null;
        }
        return view;
    }

    private void onClick(final InventoryClickEvent event) {
        final Inventory top = event.getView().getTopInventory();
        if (!isMenu(top)) {
            return;
        }
        final MenuViewImpl<?> view = viewOf(top, event.getWhoClicked());
        if (view == null) {
            event.setCancelled(true); // a preview, a replaced menu or a foreign holder: nothing may move
            return;
        }
        final boolean cancelledBefore = event.isCancelled();
        final ClickPolicy.Click<ItemStack> facts = facts(event, top, view.player());
        final ClickPolicy.Verdict verdict = facts == null ? ClickPolicy.Verdict.DENY
                : ClickPolicy.decide(view.slots(), stacks, facts);
        switch (verdict) {
            case DENY -> event.setCancelled(true);
            case REDO_SHIFT -> {
                event.setCancelled(true);
                final Inventory source = event.getClickedInventory();
                if (!cancelledBefore && source != null) {
                    view.shiftIn(source, event.getSlot());
                }
            }
            case ALLOW -> {
                if (!cancelledBefore && facts != null) {
                    noteChange(view, facts);
                }
            }
        }
        if (!cancelledBefore && facts != null && facts.region() == ClickPolicy.Region.TOP
                && event.getClickedInventory() == top) {
            view.click(event.getRawSlot(), event.getClick());
        }
    }

    /** Tells the view which input slots an allowed click may have changed. */
    private static void noteChange(final MenuViewImpl<?> view, final ClickPolicy.Click<ItemStack> facts) {
        if (facts.click() == ClickType.DOUBLE_CLICK) {
            view.touchedAll();
        } else if (facts.region() == ClickPolicy.Region.TOP && view.slots().isInput(facts.slot())) {
            view.touched(List.of(facts.slot()));
        }
    }

    /** @return the facts of a click, {@code null} if the event cannot be understood (then it is denied) */
    private ClickPolicy.@Nullable Click<ItemStack> facts(final InventoryClickEvent event, final Inventory top,
                                                         final Player player) {
        final int raw = event.getRawSlot();
        final ClickPolicy.Region region;
        if (event.getSlotType() == InventoryType.SlotType.OUTSIDE || raw < 0 || event.getClickedInventory() == null) {
            region = ClickPolicy.Region.OUTSIDE;
        } else {
            region = raw < top.getSize() ? ClickPolicy.Region.TOP : ClickPolicy.Region.BOTTOM;
        }
        final ClickType click = event.getClick();
        ItemStack hotbar = null;
        if (click == ClickType.NUMBER_KEY) {
            final int button = event.getHotbarButton();
            if (button < 0 || button > 8) {
                return null;
            }
            hotbar = player.getInventory().getItem(button);
        } else if (click == ClickType.SWAP_OFFHAND) {
            hotbar = player.getInventory().getItemInOffHand();
        }
        return new ClickPolicy.Click<>(click, event.getAction(), region, raw, nonEmpty(event.getCursor()),
                nonEmpty(event.getCurrentItem()), nonEmpty(hotbar));
    }

    private @Nullable ItemStack nonEmpty(final @Nullable ItemStack stack) {
        return stacks.isEmpty(stack) ? null : stack;
    }

    private void onDrag(final InventoryDragEvent event) {
        final Inventory top = event.getView().getTopInventory();
        if (!isMenu(top)) {
            return;
        }
        final MenuViewImpl<?> view = viewOf(top, event.getWhoClicked());
        if (view == null) {
            event.setCancelled(true);
            return;
        }
        final Map<Integer, ItemStack> inTop = new HashMap<>();
        boolean inBottom = false;
        for (final Map.Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
            if (entry.getKey() < top.getSize()) {
                inTop.put(entry.getKey(), entry.getValue());
            } else {
                inBottom = true;
            }
        }
        if (ClickPolicy.decideDrag(view.slots(), stacks, inTop, inBottom) == ClickPolicy.Verdict.DENY) {
            event.setCancelled(true);
        } else if (!event.isCancelled() && !inTop.isEmpty()) {
            view.touched(inTop.keySet());
        }
    }

    private void onClose(final InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof ViewHolder holder)) {
            return;
        }
        final MenuViewImpl<?> view = holder.view();
        if (view == null || view.inventory() != event.getInventory()) {
            return; // an inventory the view has replaced
        }
        final UUID id = event.getPlayer().getUniqueId();
        if (registry.remove(id, view)) {
            registry.clearBack(id);
        }
        view.dispose();
        giveBack(view);
    }

    // ------------------------------------------------------------------------------------------ rendering

    <C> Renderer<C> renderer(final MenuType<C> type, final MenuLoader.Resolved resolved, final MenuView<C> view,
                             final Player player, final AsyncLoads loads) {
        return new Renderer<>(lib.text(), lib.items(), type.id(), resolved, type.registrations(), view,
                player.getName(), this::warnOnce, player, () -> messages, loads);
    }

    /** Sets where {@code <lang:key>} reads from; menus rendered after this see it. */
    void messages(final GuiMessages messages) {
        this.messages = messages;
    }

    /** Runs {@code task} on an async thread. */
    void async(final Runnable task) {
        lib.scheduler().async().run(task);
    }

    /** @return the async lists of {@code view}: source off the server thread, answers back on the viewer's thread */
    <C> AsyncLoads loads(final MenuViewImpl<C> view, final MenuType<C> type, final Runnable arrived) {
        return new AsyncLoads(this::async, task -> onPlayerThread(view.player(), task), view::isDisposed, this::warnOnce,
                id -> type.registrations().lists().get(id).source().apply(view), arrived);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Component previewTitle(final MenuType<?> type, final String playerName,
                                  final @Nullable Object context, final int page) {
        return previewRenderer((MenuType<Object>) type, playerName, context, page).frame(page, null, null).title();
    }

    @Override
    public Inventory preview(final MenuType<?> type, final String playerName, final @Nullable Object context,
                             final int page) {
        return previewOf(type, playerName, context, page);
    }

    private <C> Inventory previewOf(final MenuType<C> type, final String playerName, final @Nullable Object context,
                                    final int page) {
        final Renderer<C> renderer = previewRenderer(type, playerName, context, page);
        final Renderer.Frame frame = renderer.frame(page, null, null);
        final Inventory inventory = renderer.create(new ViewHolder(null), frame);
        renderer.fill(inventory, frame, new Renderer.Shown(inventory.getSize()), null);
        return inventory;
    }

    /** A renderer for nobody: default language, async lists answered right away. */
    @SuppressWarnings("unchecked")
    private <C> Renderer<C> previewRenderer(final MenuType<C> type, final String playerName,
                                            final @Nullable Object context, final int page) {
        return new Renderer<>(lib.text(), lib.items(), type.id(), type.resolved(), type.registrations(),
                new DetachedView<>((C) context, page), playerName, this::warnOnce, null, () -> messages, null);
    }

    @Override
    public Inventory attach(final MenuType<?> type, final Player player, final @Nullable Object context) {
        return attachOf(type, player, context);
    }

    @SuppressWarnings("unchecked")
    private <C> Inventory attachOf(final MenuType<C> type, final Player player, final @Nullable Object context) {
        final MenuViewImpl<C> view = new MenuViewImpl<>(this, type, player, (C) context, 0);
        view.start();
        registry.put(player.getUniqueId(), view);
        return view.inventory();
    }

    // ------------------------------------------------------------------------------------------ threads

    /** Runs {@code task} now if this thread owns the player, else on the player's entity scheduler. */
    void onPlayerThread(final Player player, final Runnable task) {
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

    /** Runs {@code task} on the player's thread after the current event is over. */
    void later(final Player player, final Runnable task) {
        try {
            lib.scheduler().entity(player).run(task);
        } catch (final IllegalStateException e) {
            // the library closed meanwhile
        }
    }

    /** Runs {@code task} on the player's thread on the next tick; nothing happens if the player is gone or the library closed. */
    void hop(final Player player, final Runnable task) {
        try {
            lib.scheduler().entity(player).run(task, () -> { });
        } catch (final IllegalStateException e) {
            // the library closed meanwhile
        }
    }

    /** @return cancels the task, which runs on the player's thread after {@code ticks}; a no-op if it cannot be scheduled */
    Runnable laterOn(final Player player, final long ticks, final Runnable task) {
        try {
            return lib.scheduler().entity(player).runLater(ticks, task, () -> { })::cancel;
        } catch (final IllegalStateException e) {
            return () -> { };
        }
    }

    /**
     * Logs the failure of an asynchronous action and tells the player, if the language file has {@code action-failed}.
     *
     * @param player who to tell; {@code null} if the view is gone and nobody is
     */
    void actionFailed(final MenuType<?> type, final @Nullable Player player, final String what, final Throwable cause) {
        lib.plugin().getLogger().log(Level.SEVERE, "[menus/" + type.id() + ".yml] " + what + " failed", cause);
        final GuiMessages source = messages;
        if (source == null || player == null) {
            return;
        }
        try {
            final Component text = source.get(player, "action-failed", TagResolver.empty());
            if (text != null) {
                player.sendMessage(text);
            }
        } catch (final RuntimeException e) {
            warnOnce("[menus/" + type.id() + ".yml] could not send gui.action-failed: " + e);
        }
    }

    SynthetixTask timer(final Player player, final int ticks, final Runnable body, final Runnable retired) {
        return lib.scheduler().entity(player).runTimer(ticks, ticks, task -> body.run(), retired);
    }

    /** Runs a command as the console on the global region; a menu file is written by the server owner. */
    void runAsConsole(final String command) {
        lib.scheduler().global().run(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    /** Runs code a menu file refers to; its failure is logged and does not reach the event. */
    void guarded(final MenuType<?> type, final String what, final Runnable task) {
        try {
            task.run();
        } catch (final RuntimeException e) {
            lib.plugin().getLogger().log(Level.SEVERE, "[menus/" + type.id() + ".yml] " + what + " threw", e);
        }
    }

    void warnOnce(final String message) {
        if (warned.size() < MAX_WARNINGS && warned.add(message)) {
            lib.plugin().getLogger().warning(message);
        }
    }

    // ------------------------------------------------------------------------------------------ lifecycle

    /**
     * Closes every open view, then stops listening. The order matters: while a menu is open on a thread that cannot close it
     * right now, the listener must still cancel the clicks on it. Idempotent.
     */
    void close() {
        if (closed) {
            return;
        }
        closed = true;
        Shutdown.run(registry.clearAll(), this::closeFor, (view, e) -> {
            lib.plugin().getLogger().log(Level.WARNING, "[menus/" + view.type().id() + ".yml] could not close the menu of "
                    + view.player().getName() + " cleanly, its items were removed instead: " + e);
            view.wipe();
        }, () -> HandlerList.unregisterAll(listener));
    }

    /**
     * Closes one view for good. On the viewer's thread: hand the input items back and close the inventory. Otherwise the
     * close is scheduled, and the menu's own items are removed from the inventory right away, so that nothing of the menu
     * can be taken if the task never runs (the library is closing, or the player is gone); the input items stay, they are the
     * player's.
     */
    private void closeFor(final MenuViewImpl<?> view) {
        final Player player = view.player();
        view.dispose();
        final Runnable shut = () -> {
            giveBack(view);
            if (player.getOpenInventory().getTopInventory() == view.inventory()) {
                player.closeInventory();
            }
        };
        try {
            if (lib.scheduler().owns(player)) {
                shut.run();
                return;
            }
            view.wipe();
            lib.scheduler().entity(player).run(shut, view::wipe);
        } catch (final IllegalStateException e) {
            view.wipe(); // the scheduler is closed: the close cannot be scheduled
        }
    }

    /** @return how many viewers have a menu open */
    int openViews() {
        return registry.size();
    }
}
