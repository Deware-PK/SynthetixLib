package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.config.Configs;
import com.github.dewarepk.synthetixlib.config.FilesReloadResult;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.core.ScreenOpener;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.dialog.file.ClickAction;
import com.github.dewarepk.synthetixlib.dialog.file.ClickActions;
import com.github.dewarepk.synthetixlib.dialog.file.DialogFile;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import io.papermc.paper.dialog.DialogResponseView;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * Dialogs for one plugin; one instance per {@link SynthetixLib}, closed with it. Every dialog is a YAML file in
 * {@code plugins/<plugin>/dialogs/}, copied from the plugin jar the first time and never overwritten; code registers
 * the actions, forms and placeholders that the file refers to by name.
 *
 * <pre>{@code
 * DialogService dialogs = DialogService.create(lib, configs);
 * DialogType<Void> help = dialogs.dialog("help")
 *         .action("open-auction", click -> auction.open(click.player()))
 *         .build();
 *
 * FilesReloadResult result = dialogs.reload();            // e.g. from /auction reload
 * result.errors().forEach(sender::sendMessage);
 * }</pre>
 *
 * <p>Registers a codec for the {@code click:} key of buttons and one for {@code BigDecimal} (a decimal
 * {@code number-field}) in {@code configs.codecs()}, and listens to players quitting.
 * Dialog files are read on the thread that calls {@link DialogBuilder#build()} or {@link #reload()}; dialogs that are
 * open keep the file they were shown with.
 *
 * <p>The Paper dialog API is experimental; so is this module.
 */
public final class DialogService implements AutoCloseable {

    private static final Pattern ID = Pattern.compile(ClickAction.NAME_PATTERN);

    private final SynthetixLib lib;
    private final Configs configs;
    private final DialogRegistry registry;
    private final ViewManager views;
    private final Map<String, DialogType<?>> registered = new ConcurrentHashMap<>();
    private volatile @Nullable SynthetixDialogs bootstrap;

    private DialogService(final SynthetixLib lib, final Configs configs) {
        this.lib = lib;
        this.configs = configs;
        configs.codecs().register(ClickActions.class, new ClickActionsCodec());
        configs.codecs().register(BigDecimal.class, new BigDecimalCodec());
        final Path folder = lib.plugin().getDataFolder().toPath().resolve("dialogs");
        final DialogLoader loader = new DialogLoader(new DialogStore(folder, lib.plugin()::getResource),
                new DialogParser(configs.codecs()), lib.text(), configs.codecs());
        this.views = new ViewManager(lib, configs.codecs());
        this.registry = new DialogRegistry(loader, lib.plugin().getLogger(), views);
        views.siblings(registry::find);
        views.registryDialogs(registered::get);
    }

    /**
     * Creates the service and attaches it to {@code lib}, so {@code lib.close()} closes it.
     *
     * @param lib     the owning library instance
     * @param configs the config service whose codecs read the dialog files
     * @return a new service bound to {@code lib}
     * @throws IllegalStateException if {@code lib} is already closed
     */
    public static DialogService create(final SynthetixLib lib, final Configs configs) {
        Objects.requireNonNull(lib, "lib");
        Objects.requireNonNull(configs, "configs");
        final DialogService service = new DialogService(lib, configs);
        lib.attach(service);
        return service;
    }

    /**
     * Turns on the {@code <lang:key>} tag in every text of every dialog file (title, labels, body, {@code message:}). The
     * key is looked up in the language file of the viewer's client locale (the {@code perPlayer} setting of {@code lang});
     * a key only the default language has is shown in that language (logged once); a key that no file has is shown as
     * MiniMessage's own {@code <lang>}, a translatable, so vanilla keys work. The message may use the placeholders of
     * the dialog and can be mixed with inline text.
     *
     * <pre>{@code
     * // lang/en_US.yml:  dialog: { sell-title: "<gold>Sell" }       (a Map<String, String> in Messages)
     * dialogs.messages(lang, Messages::dialog);
     * // dialogs/sell.yml:  title: '<lang:sell-title> <item-name>'
     * }</pre>
     *
     * @param lang the language files
     * @param keys picks the map of keys of one language from its record, e.g. {@code Messages::dialog}
     * @param <T>  the record of messages
     * @return this
     * @throws IllegalStateException if the service is closed
     */
    public <T> DialogService messages(final LocaleHandle<T> lang, final Function<T, Map<String, String>> keys) {
        Objects.requireNonNull(lang, "lang");
        Objects.requireNonNull(keys, "keys");
        ensureOpen();
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

    /**
     * Sets how long the buttons of a shown dialog work (default 5 minutes). Applies to dialogs shown afterwards.
     *
     * @param settings the settings, usually read from the plugin's config
     * @return this
     * @throws IllegalStateException if the service is closed
     */
    public DialogService settings(final DialogSettings settings) {
        ensureOpen();
        views.settings(Objects.requireNonNull(settings, "settings"));
        return this;
    }

    /**
     * Sends viewers whose client cannot show a dialog (an old client behind ViaVersion, Bedrock) to {@code handler}
     * instead. The library cannot tell that itself, so the plugin supplies the predicate. The view is not shown and cannot
     * be clicked; the handler may open a menu or send a message.
     *
     * <pre>{@code
     * dialogs.fallback(player -> viaVersion.version(player) < 771, (player, view) -> auctionMenu.open(player));
     * }</pre>
     *
     * @param cannotShow {@code true} for a viewer who cannot see dialogs
     * @param handler    called on the viewer's thread instead of showing the dialog
     * @return this
     * @throws IllegalStateException if the service is closed
     */
    public DialogService fallback(final Predicate<Player> cannotShow,
                                  final BiConsumer<Player, DialogView<?>> handler) {
        ensureOpen();
        views.fallback(new ViewManager.Fallback(Objects.requireNonNull(cannotShow, "cannotShow"),
                Objects.requireNonNull(handler, "handler")));
        return this;
    }

    /**
     * Replaces how the service reaches a player's thread. For self-tests with a player that is not a real entity (the
     * server cannot ask a test double which thread owns it); a plugin never needs this.
     *
     * @param executor runs the task the way the player's thread would
     * @return this
     * @throws IllegalStateException if the service is closed
     */
    @ApiStatus.Internal
    public DialogService executor(final BiConsumer<Player, Runnable> executor) {
        ensureOpen();
        views.executor(Objects.requireNonNull(executor, "executor"));
        return this;
    }

    /**
     * Connects the dialogs that the plugin registered in its bootstrap ({@link SynthetixDialogs}): their buttons are now
     * routed to the handlers of {@link #registered}, and {@link #opener()} finds them by registry key. Call once, in
     * {@code onEnable}, before {@link #registered}.
     *
     * @param registry the object made in the bootstrap
     * @return this
     * @throws IllegalStateException if the service is closed or has a registry already
     */
    public DialogService bootstrapped(final SynthetixDialogs registry) {
        Objects.requireNonNull(registry, "registry");
        ensureOpen();
        synchronized (registered) {
            if (bootstrap != null) {
                throw new IllegalStateException("bootstrapped() was called already");
            }
            bootstrap = registry;
            for (final SynthetixDialogs.Entry entry : registry.entries()) {
                final String key = entry.key().asString();
                registered.put(key, new DialogType<Void>(key, Registrations.none(),
                        new DialogLoader.Resolved(entry.file(), java.util.Set.of()), views));
            }
        }
        return this;
    }

    /**
     * Starts registering the code of a registry dialog, which {@link SynthetixDialogs#register} registered: the same
     * {@code action}, {@code submit} and {@code onClose} as for any dialog, answered when a player presses its buttons
     * (a custom click event, routed by the key). Placeholders do not exist: the dialog is static.
     *
     * <p>Nothing stops a client from sending the click of a registry dialog's button without having the dialog open, so
     * a handler must check its own state (does the player have the item, the money) exactly as it would for a command.
     * Validation of the inputs of a {@code submit} is the same as for other dialogs.
     *
     * <pre>{@code
     * dialogs.registered("auction:help").action("open-auction", click -> auction.open(click.player())).build();
     * }</pre>
     *
     * @param key the registry key given to {@link SynthetixDialogs#register}
     * @return a builder for the dialog without a context
     * @throws IllegalArgumentException if no dialog was registered under the key
     * @throws IllegalStateException    if the service is closed or {@link #bootstrapped} was not called
     */
    public DialogBuilder<Void> registered(final String key) {
        Objects.requireNonNull(key, "key");
        ensureOpen();
        synchronized (registered) {
            if (bootstrap == null) {
                throw new IllegalStateException("call bootstrapped(...) before registered(...)");
            }
            if (!registered.containsKey(key)) {
                throw new IllegalArgumentException("no dialog was registered under the key '" + key + "'");
            }
        }
        return new DialogBuilder<>(new DialogBuilder.Registrar() {
            @Override
            public <C> DialogType<C> register(final String id, final Registrations<C> registrations) {
                return registerRegistry(id, registrations);
            }
        }, key);
    }

    private <C> DialogType<C> registerRegistry(final String key, final Registrations<C> registrations) {
        synchronized (registered) {
            ensureOpen();
            final DialogFile file = registered.get(key).file();
            final DialogChecker.Checked checked = DialogChecker.check(file, registrations, lib.text(), configs.codecs());
            final String name = key;
            checked.warnings().forEach(problem -> lib.plugin().getLogger().warning("[dialogs/" + name + "] " + problem));
            final DialogType<C> type = new DialogType<>(key, registrations,
                    new DialogLoader.Resolved(file, checked.inert()), views);
            registered.put(key, type);
            return type;
        }
    }

    /**
     * Lets dialog files open screens of another module with the prefix {@code menu}: {@code click: ['menu:shop']}. Link
     * before the dialogs that use it are clicked.
     *
     * <pre>{@code
     * dialogs.link("menu", gui.opener());
     * }</pre>
     *
     * @param prefix must be {@code menu}, the only prefix dialog files have for another module
     * @param opener opens the menu with the id after the colon for a player
     * @return this
     * @throws IllegalArgumentException if the prefix is not {@code menu}
     * @throws IllegalStateException    if the service is closed
     */
    public DialogService link(final String prefix, final ScreenOpener opener) {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(opener, "opener");
        ensureOpen();
        if (!prefix.equals("menu")) {
            throw new IllegalArgumentException("dialog files can only link the prefix 'menu', not '" + prefix + "'");
        }
        views.menuOpener(opener);
        return this;
    }

    /**
     * @return opens the dialog with the given id (a dialog built by this service, or the registry key of one
     *         registered in the bootstrap) for a player; {@code false} if there is none. Hand it to another module's
     *         {@code link}.
     */
    public ScreenOpener opener() {
        return (player, id) -> {
            DialogType<?> found = registry.find(id);
            if (found == null) {
                found = registered.get(id);
            }
            if (found == null) {
                return false;
            }
            found.open(player);
            return true;
        };
    }

    /**
     * Sends the click of a registry dialog's button through the same routing as the client's custom click event, so a
     * self-test with no client can press a button.
     *
     * @param key      {@code namespace:path/button}
     * @param player   who clicked
     * @param response the answers, {@code null} if none
     */
    @ApiStatus.Internal
    public void click(final String key, final Player player, final DialogResponseView response) {
        views.routeClick(Objects.requireNonNull(key, "key"), Objects.requireNonNull(player, "player"), response);
    }

    private void ensureOpen() {
        if (registry.isClosed()) {
            throw new IllegalStateException("DialogService is closed");
        }
    }

    /** @return the library instance this service belongs to */
    public SynthetixLib lib() {
        return lib;
    }

    /** @return the config service whose codecs read the dialog files */
    public Configs configs() {
        return configs;
    }

    /**
     * Starts describing the dialog in {@code dialogs/<id>.yml}; nothing is read until {@link DialogBuilder#build()}.
     *
     * <pre>{@code
     * DialogType<Void> help = dialogs.dialog("help").action("open-auction", click -> auction.open(click.player())).build();
     * }</pre>
     *
     * @param id lower case letters, digits, {@code -} and {@code _}, at most 64; the file name without {@code .yml}
     * @return a builder for a dialog without a context
     * @throws IllegalArgumentException if the id is not valid
     * @throws IllegalStateException    if the service is closed
     */
    public DialogBuilder<Void> dialog(final String id) {
        Objects.requireNonNull(id, "id");
        if (registry.isClosed()) {
            throw new IllegalStateException("DialogService is closed");
        }
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("'" + id + "' is not a valid dialog id (lower case letters, digits, - and"
                    + " _, at most 64)");
        }
        return new DialogBuilder<>(registry, id);
    }

    /**
     * Reads every dialog file again on the calling thread. A missing file with a bundled default is created again; a
     * file that cannot be parsed as YAML keeps the dialog as it was; every other mistake is reported and the dialog
     * still loads.
     *
     * @return one result per dialog, by id
     * @throws IllegalStateException if the service is closed
     */
    public FilesReloadResult reload() {
        return registry.reload();
    }

    /** @return {@code true} after {@link #close()} */
    public boolean isClosed() {
        return registry.isClosed();
    }

    /** Closes every open dialog and stops listening. Idempotent; called by {@code lib.close()}. */
    @Override
    public void close() {
        registry.close();
        views.close();
    }
}
