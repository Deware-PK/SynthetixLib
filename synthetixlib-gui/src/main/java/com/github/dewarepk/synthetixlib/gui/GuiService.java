package com.github.dewarepk.synthetixlib.gui;

import com.github.dewarepk.synthetixlib.config.Configs;
import com.github.dewarepk.synthetixlib.config.FilesReloadResult;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.core.ScreenOpener;
import com.github.dewarepk.synthetixlib.core.ScreenOpener;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.gui.file.ClickAction;
import com.github.dewarepk.synthetixlib.gui.file.ClickBindings;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;

/**
 * Menus for one plugin; one instance per {@link SynthetixLib}, closed with it. Every menu is a YAML file in
 * {@code plugins/<plugin>/menus/}, copied from the plugin jar the first time and never overwritten; code registers
 * the actions, lists, states and placeholders that the file refers to by name.
 *
 * <pre>{@code
 * GuiService gui = GuiService.create(lib, configs);
 * MenuType<Void> auction = gui.menu("auction")
 *         .placeholder("balance", view -> Component.text(economy.balance(view.player())))
 *         .action("toggle-notify", click -> prefs.toggle(click.player()))
 *         .build();
 *
 * FilesReloadResult result = gui.reload();            // e.g. from /auction reload
 * result.errors().forEach(sender::sendMessage);
 * }</pre>
 *
 * <p>Registers a codec for the {@code click:} key of items in {@code configs.codecs()}, and listens to the inventory
 * events of the menus it opened. Menu files are read on the thread that calls {@link MenuBuilder#build()} or
 * {@link #reload()}; open views pick up a reloaded file on their next refresh.
 */
public final class GuiService implements AutoCloseable {
    private static final Pattern ID = Pattern.compile(ClickAction.NAME_PATTERN);


    private final SynthetixLib lib;
    private final Configs configs;
    private final MenuRegistry registry;
    private final ViewManager views;
    private final Map<String, ScreenOpener> links = new ConcurrentHashMap<>();

    private GuiService(final SynthetixLib lib, final Configs configs) {
        this.lib = lib;
        this.configs = configs;
        configs.codecs().register(ClickBindings.class, new ClickBindingsCodec());
        final Path folder = lib.plugin().getDataFolder().toPath().resolve("menus");
        final MenuLoader loader = new MenuLoader(new MenuStore(folder, lib.plugin()::getResource),
                new MenuParser(configs.codecs()), lib.text(), links::containsKey);
        this.views = new ViewManager(lib, links);
        this.registry = new MenuRegistry(loader, lib.plugin().getLogger(), views);
    }

    /**
     * Creates the service and attaches it to {@code lib}, so {@code lib.close()} closes it.
     *
     * @param lib     the owning library instance
     * @param configs the config service whose codecs read the menu files
     * @return a new service bound to {@code lib}
     * @throws IllegalStateException if {@code lib} is already closed
     */
    public static GuiService create(final SynthetixLib lib, final Configs configs) {
        Objects.requireNonNull(lib, "lib");
        Objects.requireNonNull(configs, "configs");
        final GuiService service = new GuiService(lib, configs);
        lib.attach(service);
        return service;
    }

    /**
     * Turns on the {@code <lang:key>} tag in every text of every menu file (title, names, lore, {@code message:}). The key
     * is looked up in the language file of the viewer's client locale (the {@code perPlayer} setting of {@code lang}); a
     * key that no file has is logged once and shown as the key; a key only the default language has is shown in the default
     * language (logged once). The message may use the placeholders of the menu
     * ({@code <player>}, {@code <page>}, registered ones) and can be mixed with inline text.
     *
     * <pre>{@code
     * // lang/en_US.yml:  gui: { auction-title: "<gold>Auction" }       (a Map<String, String> in Messages)
     * gui.messages(lang, Messages::gui);
     * // menus/auction.yml:  title: '<lang:auction-title> <gray>(<page>/<pages>)'
     * }</pre>
     *
     * @param lang the language files
     * @param keys picks the map of keys of one language from its record, e.g. {@code Messages::gui}
     * @param <T>  the record of messages
     * @return this
     * @throws IllegalStateException if the service is closed
     */
    public <T> GuiService messages(final LocaleHandle<T> lang, final Function<T, Map<String, String>> keys) {
        Objects.requireNonNull(lang, "lang");
        Objects.requireNonNull(keys, "keys");
        if (registry.isClosed()) {
            throw new IllegalStateException("GuiService is closed");
        }
        views.messages((viewer, key, nested) -> {
            final boolean[] found = {false};
            final Function<T, String> pick = messages -> {
                final Map<String, String> map = keys.apply(messages);
                final String raw = map == null ? null : map.get(key);
                found[0] = raw != null;
                return raw;
            };
            Component message = viewer == null ? lang.get((Locale) null, pick, nested)
                    : lang.get(viewer, pick, nested);
            if (!found[0] && viewer != null) {
                // the viewer's file lacks the key (a removed entry): the default language still has its text
                message = lang.get((Locale) null, pick, nested);
                if (found[0]) {
                    views.warnOnce("[lang] the key '" + key + "' is missing in the language file of " + viewer.locale()
                            + ", using the default language's text");
                }
            }
            return found[0] ? message : null;
        });
        return this;
    }

    /** @return the library instance this service belongs to */
    public SynthetixLib lib() {
        return lib;
    }

    /** @return the config service whose codecs read the menu files */
    public Configs configs() {
        return configs;
    }

    /**
     * Starts describing the menu in {@code menus/<id>.yml}; nothing is read until {@link MenuBuilder#build()}.
     *
     * <pre>{@code
     * MenuType<Void> shop = gui.menu("shop").action("buy", click -> shop.buy(click.player())).build();
     * }</pre>
     *
     * @param id lower case letters, digits, {@code -} and {@code _}, at most 64; the file name without {@code .yml}
     * @return a builder for a menu without a context
     * @throws IllegalArgumentException if the id is not valid
     * @throws IllegalStateException    if the service is closed
     */
    public MenuBuilder<Void> menu(final String id) {
        Objects.requireNonNull(id, "id");
        if (registry.isClosed()) {
            throw new IllegalStateException("GuiService is closed");
        }
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("'" + id + "' is not a valid menu id (lower case letters, digits, - and _,"
                    + " at most 64)");
        }
        return new MenuBuilder<>(registry, id);
    }

    /**
     * Lets menu files open screens of another module with {@code <prefix>:<id>} in a click list, e.g. {@code dialog:sell}.
     * Link before the menus that use the prefix are built (or call {@link #reload()} after): a menu item that uses a prefix
     * nothing is linked to is reported and inert.
     *
     * <pre>{@code
     * gui.link("dialog", dialogs.opener());      // menus/shop.yml:  click: ['dialog:sell']
     * }</pre>
     *
     * @param prefix lower case letters, digits, {@code -} and {@code _}; not a built-in action name
     * @param opener opens the screen with the id after the colon for a player
     * @return this
     * @throws IllegalArgumentException if the prefix is not valid, is a built-in name or is linked already
     * @throws IllegalStateException    if the service is closed
     */
    public GuiService link(final String prefix, final ScreenOpener opener) {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(opener, "opener");
        if (registry.isClosed()) {
            throw new IllegalStateException("GuiService is closed");
        }
        if (!ID.matcher(prefix).matches() || isBuiltIn(prefix)) {
            throw new IllegalArgumentException("'" + prefix + "' cannot be a link prefix (lower case letters, digits, - and _,"
                    + " not a built-in action name such as close, open or action)");
        }
        if (links.putIfAbsent(prefix, opener) != null) {
            throw new IllegalArgumentException("the prefix '" + prefix + "' is linked already");
        }
        return this;
    }

    /** Names the click action parser reads itself; a link cannot use them as a prefix. */
    private static boolean isBuiltIn(final String name) {
        return switch (name) {
            case "close", "back", "refresh", "page-next", "page-previous", "open", "command", "console", "message",
                    "sound", "action" -> true;
            default -> false;
        };
    }

    /**
     * @return opens the menu with the given id (a menu built by this service) for a player; {@code false} if there is
     *         none. Hand it to another module's {@code link}.
     */
    public ScreenOpener opener() {
        return (player, id) -> {
            final MenuType<?> menu = registry.find(id);
            if (menu == null) {
                return false;
            }
            menu.open(player);
            return true;
        };
    }

    /**
     * Reads every menu file again on the calling thread. A missing file with a bundled default is created again; a
     * file that cannot be parsed as YAML keeps the menu as it was; every other mistake is reported and the menu
     * still loads.
     *
     * @return one result per menu, by id
     * @throws IllegalStateException if the service is closed
     */
    public FilesReloadResult reload() {
        return registry.reload();
    }

    /** @return {@code true} after {@link #close()} */
    public boolean isClosed() {
        return registry.isClosed();
    }

    /**
     * Closes every open menu and stops listening. Idempotent; called by {@code lib.close()}.
     */
    @Override
    public void close() {
        registry.close();
        views.close();
    }
}
