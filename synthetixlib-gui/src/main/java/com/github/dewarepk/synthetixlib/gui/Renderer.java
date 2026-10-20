package com.github.dewarepk.synthetixlib.gui;

import com.github.dewarepk.synthetixlib.core.item.ItemBuilder;
import com.github.dewarepk.synthetixlib.core.item.Items;
import com.github.dewarepk.synthetixlib.core.text.TextService;
import com.github.dewarepk.synthetixlib.gui.file.HideWhen;
import com.github.dewarepk.synthetixlib.gui.file.MenuFile;
import com.github.dewarepk.synthetixlib.gui.file.MenuItem;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Turns one menu file into an inventory for one viewer: texts with the placeholders of that view, items, lists and
 * pages. Holds no data of the viewer besides the view it calls the registered code with, so a view makes a new one
 * whenever the file changes.
 *
 * <p>Placeholder values are components that are inserted as they are; they are never parsed again (no MiniMessage
 * injection from item names or player input). A tag that nothing registered is left as text by MiniMessage.
 */
final class Renderer<C> {

    /**
     * Everything that changes with the page and the data of the lists.
     *
     * @param page  the page shown, from 0, already clamped
     * @param pages number of pages, at least 1
     * @param lists the entries of every list used, by list id
     * @param title the title for this page
     * @param loading the ids of the async lists whose first entries have not arrived (their list is empty here)
     */
    record Frame(int page, int pages, Map<String, List<?>> lists, Component title, Set<String> loading) {
    }

    /** What each slot of an inventory shows, so a click is answered by what the viewer saw, not by what is true now. */
    static final class Shown {

        private final @Nullable MenuItem[] items;
        private final @Nullable Object[] entries;

        Shown(final int size) {
            this.items = new MenuItem[size];
            this.entries = new Object[size];
        }

        int size() {
            return items.length;
        }

        /** @return the item the slot shows (the chosen state of a state item); {@code null} if nothing clickable */
        @Nullable MenuItem item(final int slot) {
            return items[slot];
        }

        /** @return the list entry the slot shows, {@code null} if it is not a list slot or has no entry */
        @Nullable Object entry(final int slot) {
            return entries[slot];
        }

        private void set(final int slot, final @Nullable MenuItem item, final @Nullable Object entry) {
            items[slot] = item;
            entries[slot] = entry;
        }
    }

    private final Set<String> warnedKeys = ConcurrentHashMap.newKeySet();
    private final TextService text;
    private final Items items;
    private final String menuId;
    private final MenuLoader.Resolved resolved;
    private final Registrations<C> registered;
    private final MenuView<C> view;
    private final String playerName;
    private final Consumer<String> warn;
    private final Map<Character, int[]> slotsOf = new LinkedHashMap<>();
    private final Map<Character, String> listOf = new LinkedHashMap<>();
    private final Set<String> listIds = new LinkedHashSet<>();
    private final Set<Character> dynamic = new LinkedHashSet<>();
    private final Set<Character> inputChars;
    private final boolean[] inputSlot;
    private final @Nullable Player viewer;
    private final Supplier<@Nullable GuiMessages> messages;
    private final @Nullable AsyncLoads loads;

    /** A renderer without a viewer, language files or async lists (previews, tests). */
    Renderer(final TextService text, final Items items, final String menuId, final MenuLoader.Resolved resolved,
             final Registrations<C> registered, final MenuView<C> view, final String playerName,
             final Consumer<String> warn) {
        this(text, items, menuId, resolved, registered, view, playerName, warn, null, () -> null, null);
    }

    /**
     * @param view       what registered code is called with
     * @param playerName the value of {@code <player>}
     * @param warn       gets a message for things wrong in a file that only show while rendering
     * @param viewer     whose language {@code <lang:key>} uses; {@code null} for the default language
     * @param messages   the language files, if the service has any
     * @param loads      the async lists of the view; {@code null} runs their source right away
     */
    Renderer(final TextService text, final Items items, final String menuId, final MenuLoader.Resolved resolved,
             final Registrations<C> registered, final MenuView<C> view, final String playerName,
             final Consumer<String> warn, final @Nullable Player viewer,
             final Supplier<@Nullable GuiMessages> messages, final @Nullable AsyncLoads loads) {
        this.viewer = viewer;
        this.messages = messages;
        this.loads = loads;
        this.text = text;
        this.items = items;
        this.menuId = menuId;
        this.resolved = resolved;
        this.registered = registered;
        this.view = view;
        this.playerName = playerName;
        this.warn = warn;
        final MenuFile file = resolved.file();
        this.inputChars = SlotMap.inputChars(file, resolved.inert());
        this.inputSlot = new boolean[file.size()];
        for (final String line : file.layout()) {
            for (final char c : line.toCharArray()) {
                if (c != ' ' && !slotsOf.containsKey(c)) {
                    slotsOf.put(c, file.slots(c));
                }
            }
        }
        for (final char c : inputChars) {
            for (final int slot : slotsOf.getOrDefault(c, new int[0])) {
                inputSlot[slot] = true;
            }
        }
        for (final char c : slotsOf.keySet()) {
            final MenuItem item = file.item(c);
            if (item == null || inputChars.contains(c)) {
                continue;
            }
            if (isDynamic(item)) {
                dynamic.add(c);
            }
            if (item.list().isPresent() && !resolved.inert().contains(c)) {
                listOf.put(c, item.list().get());
                listIds.add(item.list().get());
            }
        }
    }

    /** An item that must be rendered again on every update: data, state, a page rule or a placeholder in its text. */
    private boolean isDynamic(final MenuItem item) {
        if (item.list().isPresent() || item.state().isPresent() || item.hideWhen().isPresent()) {
            return true;
        }
        if (item.name().isPresent() && !MenuChecker.placeholderNames(item.name().get(), text).isEmpty()) {
            return true;
        }
        return item.lore().stream().anyMatch(line -> !MenuChecker.placeholderNames(line, text).isEmpty());
    }

    /** @return the layout characters worth rendering on every update (the others only on open and {@code refresh()}) */
    Set<Character> dynamicChars() {
        return dynamic;
    }

    /** @return the id of the list that {@code c} shows, {@code null} if it is not a usable list */
    @Nullable String listIdOf(final char c) {
        return listOf.get(c);
    }

    /**
     * Calls the sources of the lists and works out the pages.
     *
     * @param requestedPage the page wanted; it is clamped to the pages that exist
     * @param previous      the frame before, to keep the lists that are not reloaded; may be {@code null}
     * @param reload        which lists to ask their source again: {@code null} for all, else only those
     */
    Frame frame(final int requestedPage, final @Nullable Frame previous, final @Nullable Set<String> reload) {
        final Map<String, List<?>> lists = new LinkedHashMap<>();
        final Set<String> loading = new LinkedHashSet<>();
        for (final String id : listIds) {
            final boolean again = previous == null || reload == null || reload.contains(id);
            if (loads != null && registered.lists().get(id).async()) {
                final List<?> got = loads.poll(id, again);
                lists.put(id, got == null ? List.of() : got);
                if (got == null) {
                    loading.add(id);
                }
                continue;
            }
            final List<?> kept = again ? null : previous.lists().get(id);
            lists.put(id, kept != null ? kept : load(id));
        }
        int pages = 1;
        for (final Map.Entry<Character, String> entry : listOf.entrySet()) {
            pages = Math.max(pages, Pagination.pages(lists.get(entry.getValue()).size(),
                    slotsOf.get(entry.getKey()).length));
        }
        final int page = Pagination.clamp(requestedPage, pages);
        return new Frame(page, pages, lists, new Pass(page, pages).parse(resolved.file().title()), loading);
    }

    private List<?> load(final String id) {
        final Registrations.ListBinding<C> binding = registered.lists().get(id);
        try {
            final List<?> entries = binding.source().apply(view);
            return entries == null ? List.of() : new ArrayList<>(entries);
        } catch (final RuntimeException e) {
            warn.accept("[menus/" + menuId + ".yml] the source of list '" + id + "' threw " + e);
            return List.of();
        }
    }

    /** Creates the inventory of this menu with the frame's title; {@code holder} is bound to it. */
    Inventory create(final ViewHolder holder, final Frame frame) {
        final MenuFile file = resolved.file();
        final Inventory inventory = switch (file.type()) {
            case CHEST -> Bukkit.createInventory(holder, file.size(), frame.title());
            case HOPPER -> Bukkit.createInventory(holder, InventoryType.HOPPER, frame.title());
            case DISPENSER -> Bukkit.createInventory(holder, InventoryType.DISPENSER, frame.title());
        };
        holder.bind(inventory);
        return inventory;
    }

    /** @return {@code true} if the slot is an input slot: it holds the player's items, so it is never drawn or cleared */
    boolean isInputSlot(final int slot) {
        return slot >= 0 && slot < inputSlot.length && inputSlot[slot];
    }

    /**
     * Sets the slots of the given layout characters. Input slots are never touched: what is in them belongs to the player.
     *
     * @param only the characters to render; {@code null} clears every slot but the input slots and renders everything
     */
    void fill(final Inventory inventory, final Frame frame, final Shown shown, final @Nullable Set<Character> only) {
        if (only == null) {
            for (int slot = 0; slot < shown.size(); slot++) {
                if (!isInputSlot(slot)) {
                    inventory.setItem(slot, null);
                }
                shown.set(slot, null, null);
            }
        }
        final Pass pass = new Pass(frame.page(), frame.pages());
        for (final char c : only == null ? slotsOf.keySet() : only) {
            final int[] slots = slotsOf.get(c);
            if (slots == null || inputChars.contains(c)) {
                continue;
            }
            final MenuItem item = resolved.file().item(c);
            if (item == null || resolved.inert().contains(c) && (item.list().isPresent() || item.state().isPresent())) {
                put(inventory, shown, slots, null, null, null);
            } else if (listOf.containsKey(c)) {
                fillList(inventory, frame, shown, pass, c, item);
            } else {
                final MenuItem shownItem = effective(item, frame.page(), frame.pages(), () -> stateOf(item));
                put(inventory, shown, slots, shownItem == null ? null : build(shownItem, pass), shownItem, null);
            }
        }
    }

    private void fillList(final Inventory inventory, final Frame frame, final Shown shown, final Pass pass,
                          final char c, final MenuItem item) {
        final int[] slots = slotsOf.get(c);
        final String listId = listOf.get(c);
        final List<?> entries = frame.lists().get(listId);
        final Registrations.ListBinding<C> binding = registered.lists().get(listId);
        if (frame.loading().contains(listId)) {
            final ItemStack loadingStack = item.loading().map(wait -> build(wait, pass)).orElse(null);
            for (final int slot : slots) {
                inventory.setItem(slot, loadingStack);
                shown.set(slot, null, null);
            }
            return;
        }
        final ItemStack emptyStack = item.empty().map(empty -> build(empty, pass)).orElse(null);
        for (int i = 0; i < slots.length; i++) {
            final int index = frame.page() * slots.length + i;
            final Object entry = index < entries.size() ? entries.get(index) : null;
            if (entry == null) {
                inventory.setItem(slots[i], emptyStack);
                shown.set(slots[i], null, null);
            } else {
                inventory.setItem(slots[i], entryStack(binding, listId, entry));
                shown.set(slots[i], item, entry);
            }
        }
    }

    private @Nullable ItemStack entryStack(final Registrations.ListBinding<C> binding, final String listId,
                                           final Object entry) {
        try {
            return binding.render().apply(entry, view);
        } catch (final RuntimeException e) {
            warn.accept("[menus/" + menuId + ".yml] rendering an entry of list '" + listId + "' threw " + e);
            return null;
        }
    }

    private static void put(final Inventory inventory, final Shown shown, final int[] slots,
                            final @Nullable ItemStack stack, final @Nullable MenuItem item,
                            final @Nullable Object entry) {
        for (final int slot : slots) {
            inventory.setItem(slot, stack);
            shown.set(slot, item, entry);
        }
    }

    private @Nullable String stateOf(final MenuItem item) {
        final String id = item.state().orElseThrow();
        try {
            return registered.states().get(id).apply(view);
        } catch (final RuntimeException e) {
            warn.accept("[menus/" + menuId + ".yml] the source of state '" + id + "' threw " + e);
            return null;
        }
    }

    /**
     * What an item shows right now.
     *
     * @param page  the page, from 0
     * @param pages number of pages
     * @param state the name of the state to show; only called for a state item
     * @return {@code null} if the item is hidden by {@code hide-when} or its state is unknown, else the item itself or
     *         the item of its state
     */
    static @Nullable MenuItem effective(final MenuItem item, final int page, final int pages,
                                        final Supplier<@Nullable String> state) {
        final HideWhen hideWhen = item.hideWhen().orElse(null);
        if (hideWhen == HideWhen.FIRST_PAGE && page <= 0 || hideWhen == HideWhen.LAST_PAGE && page >= pages - 1) {
            return null;
        }
        if (item.state().isEmpty()) {
            return item;
        }
        final String name = state.get();
        return name == null ? null : item.stateItem(name);
    }

    /** Builds the stack of one item with the placeholders of {@code pass}; {@code null} for air or an unusable item. */
    @Nullable ItemStack build(final MenuItem item, final Pass pass) {
        try {
            final ItemBuilder builder;
            final int maxStack;
            if (item.base64().isPresent()) {
                final ItemStack stack = ItemStack.deserializeBytes(Base64.getDecoder().decode(item.base64().get()));
                if (stack.isEmpty()) {
                    return null;
                }
                builder = items.from(stack);
                maxStack = stack.getMaxStackSize();
            } else {
                final Material material = item.material().orElse(null);
                if (material == null || material.isAir() || !material.isItem()) {
                    return null;
                }
                builder = items.of(material);
                maxStack = material.getMaxStackSize();
            }
            item.amount().ifPresent(amount -> builder.amount(Math.min(amount, maxStack)));
            item.name().ifPresent(name -> builder.name(name, pass.resolver()));
            for (final String line : item.lore()) {
                builder.lore(line, pass.resolver());
            }
            item.itemModel().ifPresent(builder::itemModel);
            item.glint().ifPresent(builder::glint);
            item.hideTooltip().ifPresent(builder::hideTooltip);
            item.tooltipStyle().ifPresent(style -> builder.meta(meta -> meta.setTooltipStyle(style)));
            return builder.build();
        } catch (final RuntimeException e) {
            warn.accept("[menus/" + menuId + ".yml] an item could not be built: " + e);
            return null;
        }
    }

    /**
     * The key is in no language file: it is MiniMessage's own {@code <lang>}, a translatable with the rest of the
     * arguments as text, so vanilla keys such as {@code item.minecraft.diamond} work. A key that does not look
     * vanilla is most likely a typo, so it is reported once.
     */
    private Component translatable(final String key, final ArgumentQueue arguments) {
        final List<Component> args = new ArrayList<>();
        while (arguments.hasNext()) {
            args.add(Component.text(arguments.pop().value()));
        }
        if (!isVanillaKey(key) && warnedKeys.add(key)) {
            warn.accept("[menus/" + menuId + ".yml] <lang:" + key + "> is not in the language files");
        }
        return Component.translatable(key, args);
    }

    private static boolean isVanillaKey(final String key) {
        final int dot = key.indexOf('.');
        if (dot < 0) {
            return false;
        }
        return switch (key.substring(0, dot)) {
            case "block", "item", "entity", "effect", "enchantment", "biome", "container", "gui", "key",
                    "options", "attribute", "advancements", "stat" -> true;
            default -> false;
        };
    }

    /** @return a pass for page {@code page} (from 0) of {@code pages} */
    Pass pass(final int page, final int pages) {
        return new Pass(page, pages);
    }

    /**
     * The placeholders of one render: {@code <player>}, {@code <page>} (from 1), {@code <pages>} and the registered
     * ones. A value is asked for once per pass, however often the tag is used.
     */
    final class Pass {

        private final int page;
        private final int pages;
        private final Map<String, Component> values = new HashMap<>();
        /** The placeholders alone: what a language file message may use (no {@code <lang>} inside a message). */
        private final TagResolver placeholders = new TagResolver() {
            @Override
            public @Nullable Tag resolve(final String name, final ArgumentQueue arguments, final Context ctx) {
                final Component value = value(name);
                return value == null ? null : Tag.selfClosingInserting(value);
            }

            @Override
            public boolean has(final String name) {
                return Registrations.isBuiltInPlaceholder(name) || registered.placeholders().containsKey(name);
            }
        };
        /** The placeholders and {@code <lang:key>}, which reads the viewer's language file. */
        private final TagResolver resolver = TagResolver.resolver(placeholders, new TagResolver() {
            @Override
            public @Nullable Tag resolve(final String name, final ArgumentQueue arguments, final Context ctx) {
                final GuiMessages source = messages.get();
                if (source == null || !name.equals("lang") || !arguments.hasNext()) {
                    return null; // no language files: MiniMessage's own <lang> (a translatable) stays
                }
                final String key = arguments.pop().value();
                final Component message = source.get(viewer, key, placeholders);
                if (message == null) {
                    return Tag.selfClosingInserting(translatable(key, arguments));
                }
                return Tag.selfClosingInserting(message);
            }

            @Override
            public boolean has(final String name) {
                return name.equals("lang") && messages.get() != null;
            }
        });

        private Pass(final int page, final int pages) {
            this.page = page;
            this.pages = pages;
        }

        TagResolver resolver() {
            return resolver;
        }

        /** Parses MiniMessage with this pass's placeholders. */
        Component parse(final String markup) {
            return text.parse(markup, resolver);
        }

        private @Nullable Component value(final String name) {
            return switch (name) {
                case "player" -> Component.text(playerName);
                case "page" -> Component.text(page + 1);
                case "pages" -> Component.text(pages);
                default -> {
                    final var function = registered.placeholders().get(name);
                    if (function == null) {
                        yield null;
                    }
                    yield values.computeIfAbsent(name, key -> {
                        try {
                            final Component value = function.apply(view);
                            return value == null ? Component.empty() : value;
                        } catch (final RuntimeException e) {
                            warn.accept("[menus/" + menuId + ".yml] placeholder <" + key + "> threw " + e);
                            return Component.empty();
                        }
                    });
                }
            };
        }
    }
}
