package com.github.dewarepk.synthetixlib.gui;

import com.github.dewarepk.synthetixlib.core.scheduler.SynthetixTask;
import com.github.dewarepk.synthetixlib.gui.file.ClickAction;
import com.github.dewarepk.synthetixlib.gui.file.ClickKind;
import com.github.dewarepk.synthetixlib.gui.file.MenuFile;
import com.github.dewarepk.synthetixlib.gui.file.MenuItem;
import com.github.dewarepk.synthetixlib.gui.file.MenuKind;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * One viewer's open copy of a menu (design section 3): own inventory, page, context, lists and update task.
 * Everything that is per viewer lives here or in the service's {@link ViewRegistry}; the {@link MenuType} is shared
 * and immutable.
 *
 * <p>The public methods may be called from any thread and hop onto the viewer's thread, except the ones that return
 * items ({@link #inputs}, {@link #takeInputs}), which must be called on it; the package-private ones run on it already.
 */
final class MenuViewImpl<C> implements MenuView<C> {

    private record Click<C>(Player player, MenuView<C> view) implements MenuClick<C> {
    }

    private final ViewManager manager;
    private final MenuType<C> type;
    private final Player player;
    private final @Nullable C context;
    private final ClickGate gate = new ClickGate();
    private final AsyncLoads loads;
    private final Object inputLock = new Object();
    private volatile int page;
    private volatile boolean disposed;
    private volatile Inventory inventory;
    private volatile SlotMap<ItemStack> slots = new SlotMap<>(List.of());

    private MenuLoader.Resolved resolved;
    private Renderer<C> renderer;
    private Renderer.Frame frame;
    private Renderer.Shown shown = new Renderer.Shown(0);
    private Component title;
    private MenuKind builtKind;
    private int builtSize;
    private @Nullable SynthetixTask timer;
    private int timerTicks;

    // input change notifications; only touched on the viewer's thread
    private final Set<Character> changed = new LinkedHashSet<>();
    private final Map<Character, List<ItemStack>> notified = new LinkedHashMap<>();
    private boolean notifyScheduled;

    MenuViewImpl(final ViewManager manager, final MenuType<C> type, final Player player, final @Nullable C context,
                 final int page) {
        this.manager = manager;
        this.type = type;
        this.player = player;
        this.context = context;
        this.page = page;
        this.loads = manager.loads(this, type, () -> render(false, null, true));
    }

    @Override
    public Player player() {
        return player;
    }

    @Override
    public @Nullable C context() {
        return context;
    }

    @Override
    public int page() {
        return page;
    }

    @Override
    public void page(final int page) {
        manager.onPlayerThread(player, () -> {
            this.page = page;
            render(false, null);
        });
    }

    @Override
    public void refresh() {
        manager.onPlayerThread(player, () -> render(true, null));
    }

    @Override
    public void refresh(final char slotChar) {
        manager.onPlayerThread(player, () -> render(false, slotChar));
    }

    @Override
    public void close() {
        manager.onPlayerThread(player, () -> manager.closeNow(this));
    }

    @Override
    public void back() {
        manager.onPlayerThread(player, () -> manager.backNow(this));
    }

    /** @return the inventory shown now; a new one after the title or the shape of the menu changed */
    Inventory inventory() {
        return inventory;
    }

    MenuType<C> type() {
        return type;
    }

    /** @return which slots are input slots and what they accept; changes when the menu file does */
    SlotMap<ItemStack> slots() {
        return slots;
    }

    ViewManager.BackEntry backEntry() {
        return new ViewManager.BackEntry(type, context, page);
    }

    /** Builds the inventory; the viewer's thread. */
    void start() {
        render(true, null);
    }

    boolean isDisposed() {
        return disposed;
    }

    /** Stops the update task and ignores everything from now on. */
    void dispose() {
        disposed = true;
        final SynthetixTask task = timer;
        timer = null;
        if (task != null) {
            task.cancel();
        }
    }

    // ------------------------------------------------------------------------------------------ input slots

    @Override
    public List<ItemStack> inputs(final char slotChar) {
        synchronized (inputLock) {
            return Inputs.read(manager.stacks(), contents(inventory), slots.slotsOf(slotChar));
        }
    }

    @Override
    public List<ItemStack> takeInputs(final char slotChar) {
        return take(slots.slotsOf(slotChar));
    }

    @Override
    public List<ItemStack> takeInputs() {
        return take(slots.inputSlots());
    }

    /**
     * Empties the input slots and returns what was in them, once: the lock makes the read and the clearing one step, so a
     * second call (another click, the menu closing) finds nothing.
     */
    private List<ItemStack> take(final int[] inputSlots) {
        synchronized (inputLock) {
            final Inventory current = inventory;
            final List<ItemStack> taken = Inputs.drain(manager.stacks(), contents(current), inputSlots);
            for (final int slot : inputSlots) {
                current.setItem(slot, null);
            }
            return taken;
        }
    }

    /** Takes everything out of the input slots, for the caller to give to the player. */
    List<ItemStack> drainInputs() {
        return take(slots.inputSlots());
    }

    /** Clears the slots that are not input slots, so nothing of the menu is left to take; the input items stay. */
    void wipe() {
        final Inventory current = inventory;
        final SlotMap<ItemStack> rules = slots;
        for (int slot = 0; slot < current.getSize(); slot++) {
            if (!rules.isInput(slot)) {
                current.setItem(slot, null);
            }
        }
    }

    private static List<@Nullable ItemStack> contents(final Inventory inventory) {
        final List<@Nullable ItemStack> list = new ArrayList<>(inventory.getSize());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            list.add(inventory.getItem(slot));
        }
        return list;
    }

    /**
     * The server would put a shift-clicked stack in the first free slot of the menu, wherever that is; this puts it into
     * input slots only. The event is cancelled already.
     *
     * @param source     the player's inventory the stack is in
     * @param sourceSlot the slot of {@code source}
     */
    void shiftIn(final Inventory source, final int sourceSlot) {
        final StackOps<ItemStack> ops = manager.stacks();
        final ItemStack moving = source.getItem(sourceSlot);
        if (ops.isEmpty(moving)) {
            return;
        }
        synchronized (inputLock) {
            final Inventory top = inventory;
            final List<@Nullable ItemStack> contents = contents(top);
            final Inputs.Distribution<ItemStack> result = Inputs.distribute(slots, ops, contents, moving);
            if (result.changed().isEmpty()) {
                return;
            }
            for (final int slot : result.changed()) {
                top.setItem(slot, contents.get(slot));
            }
            source.setItem(sourceSlot, result.remaining());
            touched(result.changed());
        }
        manager.later(player, player::updateInventory);
    }

    /** The viewer changed these slots of the menu (a click or drag the server carried out); tells the input handlers. */
    void touched(final Collection<Integer> topSlots) {
        for (final int slot : topSlots) {
            final SlotMap.Input<ItemStack> input = slots.input(slot);
            if (input != null) {
                changed.add(input.id());
            }
        }
        scheduleNotify();
    }

    /** Like {@link #touched} for every input slot (a double-click can gather from all of them). */
    void touchedAll() {
        changed.addAll(slots.chars());
        scheduleNotify();
    }

    private void scheduleNotify() {
        if (changed.isEmpty() || notifyScheduled || type.registrations().inputs().isEmpty()) {
            return;
        }
        notifyScheduled = true;
        manager.later(player, this::notifyChanges);
    }

    /** Runs after the server finished the click, so the handler sees the items as they are now. */
    private void notifyChanges() {
        notifyScheduled = false;
        final List<Character> chars = new ArrayList<>(changed);
        changed.clear();
        if (disposed) {
            return;
        }
        for (final char c : chars) {
            final var handler = type.registrations().inputs().get(c);
            if (handler == null) {
                continue;
            }
            final List<ItemStack> items = inputs(c);
            if (items.equals(notified.getOrDefault(c, List.of()))) {
                continue; // nothing is different from what the handler saw last
            }
            notified.put(c, items);
            manager.guarded(type, "input change handler for '" + c + "'", () -> handler.accept(this, items));
        }
    }

    // ------------------------------------------------------------------------------------------ rendering

    /**
     * Renders what changed and, if the file or the title needs another inventory, re-opens with one (Paper 1.21.11
     * can only set the title of an open inventory from a legacy string). The items of the input slots and the item on
     * the cursor go along.
     *
     * @param all  render every item, static ones too
     * @param only render the slots of this character only (and reload its list)
     */
    private void render(final boolean all, final @Nullable Character only) {
        render(all, only, false);
    }

    /** @param keepLists do not ask any list source again: an async answer just arrived and the lists are up to date */
    private void render(final boolean all, final @Nullable Character only, final boolean keepLists) {
        if (disposed) {
            return;
        }
        final MenuLoader.Resolved now = type.resolved();
        final boolean fileChanged = renderer == null || now != resolved;
        boolean full = all;
        Character single = only;
        if (fileChanged) {
            resolved = now;
            renderer = manager.renderer(type, now, this, player, loads);
            full = true;
            single = null;
        }
        Set<String> reload = null;
        if (!full && single != null) {
            final String listId = renderer.listIdOf(single);
            reload = listId == null ? Set.of() : Set.of(listId);
        }
        if (keepLists && !fileChanged) {
            reload = Set.of();
        }
        frame = renderer.frame(page, fileChanged ? null : frame, reload);
        page = frame.page();

        final MenuFile file = now.file();
        final Inventory current = inventory;
        final boolean create = current == null;
        final boolean reshaped = !create && (file.type() != builtKind || file.size() != builtSize);
        final SlotMap<ItemStack> nextSlots = fileChanged || create
                ? manager.stacks().slotMap(file, now.inert()) : slots;
        // a slot that became an input slot (or stopped being one) must not keep what is in it: re-open
        final boolean otherInputs = fileChanged && !create && !samePattern(slots, nextSlots);
        if (create || reshaped || otherInputs || !frame.title().equals(title)) {
            final Inventory next = renderer.create(new ViewHolder(this), frame);
            final Renderer.Shown nextShown = new Renderer.Shown(next.getSize());
            renderer.fill(next, frame, nextShown, null);
            if (!create && !reopen(current, next, nextSlots)) {
                return; // another plugin refused the new inventory: the old one stays, the next render tries again
            }
            inventory = next;
            slots = nextSlots;
            shown = nextShown;
            title = frame.title();
            builtKind = file.type();
            builtSize = file.size();
        } else if (full) {
            slots = nextSlots;
            renderer.fill(current, frame, shown, null);
        } else {
            renderer.fill(current, frame, shown, single != null ? Set.of(single) : renderer.dynamicChars());
        }
        syncTimer(file.updateTicks());
    }

    /** @return {@code true} if both menus have input slots in the same places with the same layout characters */
    private static boolean samePattern(final SlotMap<ItemStack> a, final SlotMap<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int slot = 0; slot < a.size(); slot++) {
            final SlotMap.Input<ItemStack> left = a.input(slot);
            final SlotMap.Input<ItemStack> right = b.input(slot);
            if (left == null != (right == null) || left != null && left.id() != right.id()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Opens {@code next} in place of {@code old}: the input items move to the same slots of {@code next}, what has no place
     * there is handed back, and the cursor item stays with the player. {@code old}'s close event must be ignored, so the
     * view points at {@code next} before it opens.
     *
     * @return {@code false} if {@code next} could not be opened; nothing has changed then
     */
    private boolean reopen(final Inventory old, final Inventory next, final SlotMap<ItemStack> nextSlots) {
        final StackOps<ItemStack> ops = manager.stacks();
        synchronized (inputLock) {
            final List<@Nullable ItemStack> from = contents(old);
            final List<@Nullable ItemStack> to = contents(next);
            inventory = next;
            final Handoff.Result<ItemStack> result = Handoff.reopen(ops, slots, from, nextSlots, to, () -> {
                final ItemStack held = player.getItemOnCursor();
                player.setItemOnCursor(null);
                return ops.isEmpty(held) ? null : held;
            }, held -> player.setItemOnCursor(held), () -> {
                for (final int slot : nextSlots.inputSlots()) {
                    next.setItem(slot, to.get(slot));
                }
                return manager.openInventory(player, next);
            });
            if (!result.opened()) {
                inventory = old;
                return false;
            }
            manager.handBack(player, result.overflow());
            return true;
        }
    }

    private void syncTimer(final int ticks) {
        if (ticks == timerTicks && (ticks == 0 || timer != null)) {
            return;
        }
        final SynthetixTask old = timer;
        timer = null;
        if (old != null) {
            old.cancel();
        }
        timerTicks = ticks;
        if (ticks > 0 && !disposed) {
            timer = manager.timer(player, ticks, () -> render(false, null), () -> manager.forget(player));
        }
    }

    // ------------------------------------------------------------------------------------------ clicks

    /**
     * A click in the top inventory at {@code slot}: plays the item's sound and runs its actions, one handler at a time (a click
     * that arrives while another handler of this view still runs is ignored). Which clicks may move items is decided before
     * this by {@link ClickPolicy}; this only runs what the file says.
     */
    void click(final int slot, final ClickType clickType) {
        if (disposed || slot < 0 || slot >= shown.size()) {
            return;
        }
        final MenuItem item = shown.item(slot);
        if (item == null || resolved.inert().contains(resolved.file().charAt(slot))) {
            return;
        }
        if (!gate.tryEnter()) {
            return;
        }
        try {
            item.sound().ifPresent(this::playSound);
            final ClickKind kind = kindOf(clickType);
            if (kind == null) {
                return;
            }
            final MenuClick<C> click = new Click<>(player, this);
            final Object entry = shown.entry(slot);
            if (entry != null) {
                final var binding = type.registrations().lists().get(item.list().orElseThrow());
                final var handler = binding == null ? null : binding.onClick();
                if (handler != null) {
                    manager.guarded(type, "list click handler", () -> handler.accept(entry, click));
                }
                return;
            }
            for (final ClickAction action : item.clicks().actions(kind)) {
                manager.guarded(type, action.format(), () -> run(action, click));
            }
        } finally {
            gate.leave();
        }
    }

    static @Nullable ClickKind kindOf(final ClickType type) {
        return switch (type) {
            case LEFT -> ClickKind.LEFT;
            case RIGHT -> ClickKind.RIGHT;
            case SHIFT_LEFT -> ClickKind.SHIFT_LEFT;
            case SHIFT_RIGHT -> ClickKind.SHIFT_RIGHT;
            case MIDDLE -> ClickKind.MIDDLE;
            case DROP, CONTROL_DROP -> ClickKind.DROP;
            default -> null;
        };
    }

    private void run(final ClickAction action, final MenuClick<C> click) {
        switch (action) {
            case ClickAction.Close ignored -> manager.closeNow(this);
            case ClickAction.Back ignored -> manager.backNow(this);
            case ClickAction.Refresh ignored -> render(true, null);
            case ClickAction.PageNext ignored -> {
                page = page + 1;
                render(false, null);
            }
            case ClickAction.PagePrevious ignored -> {
                page = page - 1;
                render(false, null);
            }
            case ClickAction.Open open -> manager.openMenu(this, open.menu());
            case ClickAction.Linked link -> manager.openLinked(this, link);
            case ClickAction.Command command -> player.performCommand(command.command());
            case ClickAction.Console console -> manager.runAsConsole(console.command());
            case ClickAction.Message message -> player.sendMessage(
                    renderer.pass(frame.page(), frame.pages()).parse(message.text()));
            case ClickAction.PlaySound sound -> playSound(sound.sound());
            case ClickAction.Run run -> {
                final var handler = type.registrations().actions().get(run.name());
                if (handler == null) {
                    manager.warnOnce("[menus/" + type.id() + ".yml] action '" + run.name() + "' is not registered");
                } else {
                    handler.accept(click);
                }
            }
        }
    }

    private void playSound(final NamespacedKey key) {
        player.playSound(Sound.sound(key, Sound.Source.UI, 1f, 1f));
    }
}
