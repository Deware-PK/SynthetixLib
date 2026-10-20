package com.github.dewarepk.synthetixexample.selftest;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * A test double for a player with a menu open, so the self-tests can feed real Bukkit events to the library's listener
 * on a server nobody has joined. The player, its inventory and its window are {@link Proxy}s that answer only what the
 * listener asks (anything else throws, so a new dependency of the library on the player shows up as a failing test).
 * The window is the menu on top of a 36-slot stand-in for the player's inventory.
 */
final class PolicyBot {

    final UUID id = UUID.randomUUID();
    /** The player's own inventory as the window shows it: raw slot {@code top.getSize() + i} is slot {@code i}. */
    final Inventory bottom = Bukkit.createInventory(null, 36);
    final Player player;
    ItemStack cursor = ItemStack.empty();
    ItemStack offHand = ItemStack.empty();
    /** What {@code player.locale()} answers: the client language. */
    java.util.Locale locale = java.util.Locale.US;
    private InventoryView window;
    private Inventory top;

    PolicyBot() {
        final PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(
                PolicyBot.class.getClassLoader(), new Class<?>[] {PlayerInventory.class}, (proxy, method, args) ->
                        switch (method.getName()) {
                            case "getItem" -> bottom.getItem((Integer) args[0]);
                            case "setItem" -> {
                                bottom.setItem((Integer) args[0], (ItemStack) args[1]);
                                yield null;
                            }
                            case "addItem" -> bottom.addItem((ItemStack[]) args[0]);
                            case "getItemInOffHand" -> offHand;
                            case "getSize" -> 36;
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> throw new UnsupportedOperationException("PlayerInventory." + method.getName());
                        });
        // The entity scheduler: every task is "retired" at once, as Paper does for an entity that is gone.
        final Object scheduler = Proxy.newProxyInstance(PolicyBot.class.getClassLoader(),
                new Class<?>[] {io.papermc.paper.threadedregions.scheduler.EntityScheduler.class},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? (Object) false : null);
        this.player = (Player) Proxy.newProxyInstance(PolicyBot.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> "PolicyBot";
                    case "locale" -> locale;
                    case "isOnline" -> true;
                    case "getInventory" -> inventory;
                    case "getItemOnCursor" -> cursor;
                    case "setItemOnCursor" -> {
                        cursor = args[0] == null ? ItemStack.empty() : (ItemStack) args[0];
                        yield null;
                    }
                    case "getScheduler" -> scheduler;
                    case "updateInventory", "playSound" -> null;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "PolicyBot";
                    default -> throw new UnsupportedOperationException("Player." + method.getName());
                });
    }

    /** Puts the bot in front of {@code menu}: events built by the other methods click in it. */
    void show(final Inventory menu) {
        this.top = menu;
        this.window = (InventoryView) Proxy.newProxyInstance(PolicyBot.class.getClassLoader(),
                new Class<?>[] {InventoryView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getTopInventory" -> menu;
                    case "getBottomInventory" -> bottom;
                    case "getPlayer" -> (HumanEntity) player;
                    case "getType" -> InventoryType.CHEST;
                    case "getCursor" -> cursor;
                    case "setCursor" -> {
                        cursor = args[0] == null ? ItemStack.empty() : (ItemStack) args[0];
                        yield null;
                    }
                    case "getItem" -> itemAt((Integer) args[0]);
                    case "getInventory" -> inventoryAt((Integer) args[0]);
                    case "convertSlot" -> (Integer) args[0] < menu.getSize() ? args[0] : (Integer) args[0] - menu.getSize();
                    case "countSlots" -> menu.getSize() + bottom.getSize();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException("InventoryView." + method.getName());
                });
    }

    private Inventory inventoryAt(final int raw) {
        return raw < 0 ? null : raw < top.getSize() ? top : bottom;
    }

    private ItemStack itemAt(final int raw) {
        final Inventory inventory = inventoryAt(raw);
        return inventory == null ? null : inventory.getItem(raw < top.getSize() ? raw : raw - top.getSize());
    }

    /** @return the raw slot of slot {@code slot} of the player's own inventory */
    int bottomRaw(final int slot) {
        return top.getSize() + slot;
    }

    /** Fires a click through the server's event system; returns the event so the test can read whether it was cancelled. */
    InventoryClickEvent click(final int rawSlot, final ClickType click, final InventoryAction action) {
        return fire(new InventoryClickEvent(window, slotType(rawSlot), rawSlot, click, action));
    }

    /** Fires a click with a number key ({@code button} 0 to 8) or the off-hand key (40). */
    InventoryClickEvent clickWithKey(final int rawSlot, final ClickType click, final InventoryAction action,
                                     final int button) {
        return fire(new InventoryClickEvent(window, slotType(rawSlot), rawSlot, click, action, button));
    }

    InventoryDragEvent drag(final ItemStack oldCursor, final Map<Integer, ItemStack> newItems) {
        final Map<Integer, ItemStack> copy = new HashMap<>(newItems);
        final InventoryDragEvent event = new InventoryDragEvent(window, oldCursor, oldCursor, false, copy);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    void close() {
        Bukkit.getPluginManager().callEvent(new InventoryCloseEvent(window));
    }

    private InventoryClickEvent fire(final InventoryClickEvent event) {
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    private static InventoryType.SlotType slotType(final int rawSlot) {
        return rawSlot < 0 ? InventoryType.SlotType.OUTSIDE : InventoryType.SlotType.CONTAINER;
    }

    /** @return how many items of {@code material} are in {@code inventory} */
    static int count(final Inventory inventory, final Material material) {
        int sum = 0;
        for (final ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.getType() == material) {
                sum += stack.getAmount();
            }
        }
        return sum;
    }
}
