package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.config.codec.Codec;
import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.config.codec.Problem;
import com.github.dewarepk.synthetixlib.core.item.Items;
import com.github.dewarepk.synthetixlib.core.text.TextService;
import com.github.dewarepk.synthetixlib.dialog.file.ClickActions;
import com.github.dewarepk.synthetixlib.dialog.file.DialogBody;
import com.github.dewarepk.synthetixlib.dialog.file.DialogFile;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.tags.DialogTagKeys;
import io.papermc.paper.registry.tag.TagKey;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

/**
 * Dialogs that Minecraft itself knows: registered in the dialog registry while the server starts, so they can be added to
 * the pause menu ("quick actions", "pause screen additions") and shown with {@code /dialog show}. Made in a
 * {@code PluginBootstrap}, where the library is not running yet; the object is kept by the plugin and handed to
 * {@link DialogService#bootstrapped} in {@code onEnable}, so there is no static state.
 *
 * <pre>{@code
 * public final class AuctionBootstrap implements PluginBootstrap {
 *     private SynthetixDialogs registry;                                // kept for createPlugin
 *
 *     public void bootstrap(final BootstrapContext context) {
 *         registry = SynthetixDialogs.bootstrap(context)                // dialogs/help.yml, copied from the jar first
 *                 .register("help", "auction:help")
 *                 .quickActions("help");
 *     }
 *
 *     public JavaPlugin createPlugin(final PluginProviderContext context) {
 *         return new AuctionPlugin(registry);
 *     }
 * }
 *
 * // AuctionPlugin#onEnable:
 * dialogs.bootstrapped(registry).registered("auction:help")
 *         .action("open-auction", click -> auction.open(click.player()))
 *         .build();
 * }</pre>
 *
 * <p>A registry dialog is <b>static</b>: it is the same for every player and fixed until the server restarts. Its file may
 * not use a placeholder (including {@code <player>}) or an item that is the player's ({@code slot:}) or comes from code;
 * those are reported when the file is read and shown as text / a barrier. Its buttons send a custom click key
 * {@code <namespace>:<path>/<button>}, which the {@link DialogService} routes to the handlers of
 * {@link DialogService#registered}. Text is MiniMessage with the standard tags only (the library is not running yet).
 */
public final class SynthetixDialogs {

    /** A registered file: the key it has in the registry and what was read. */
    record Entry(String fileId, Key key, DialogFile file) {
    }

    private static final Pattern BARE_TAG = Pattern.compile("<([A-Za-z][A-Za-z0-9_-]*)>");
    private static final Pattern FILE_ID = Pattern.compile("[a-z0-9_-]{1,64}");

    private final BootstrapContext context;
    private final DialogParser parser;
    private final DialogStore store;
    private final Map<String, Entry> byKey = new LinkedHashMap<>();
    private final Map<String, Entry> byFile = new HashMap<>();
    private final Set<String> quickActions = new LinkedHashSet<>();
    private final Set<String> pauseScreen = new LinkedHashSet<>();

    private SynthetixDialogs(final BootstrapContext context) {
        this.context = context;
        final Codecs codecs = Codecs.create();
        codecs.register(ClickActions.class, new ClickActionsCodec());
        // the Bukkit codecs need a running server; the registries of the file are not needed for the name of a material
        codecs.register(Material.class, Codec.string(text -> {
            final Material material = Material.matchMaterial(text);
            if (material == null) {
                throw new IllegalArgumentException("unknown material '" + text + "'");
            }
            return material;
        }, material -> material.name().toLowerCase(Locale.ROOT)));
        this.parser = new DialogParser(codecs);
        this.store = new DialogStore(context.getDataDirectory().resolve("dialogs"), this::bundled);
    }

    /**
     * Starts registering dialogs for a plugin. Call it once, in {@code bootstrap}; the registrations may be added until
     * the registry is built, which is after every plugin's {@code bootstrap} has returned.
     *
     * @param context the bootstrap context of the plugin
     * @return the object to keep and to register on
     */
    public static SynthetixDialogs bootstrap(final BootstrapContext context) {
        Objects.requireNonNull(context, "context");
        final SynthetixDialogs dialogs = new SynthetixDialogs(context);
        context.getLifecycleManager().registerEventHandler(RegistryEvents.DIALOG.compose().newHandler(event -> {
            for (final Entry entry : dialogs.entries()) {
                try {
                    final DialogRenderer.Parts parts = dialogs.render(entry);
                    event.registry().register(TypedKey.create(RegistryKey.DIALOG, entry.key()),
                            builder -> builder.base(parts.base()).type(parts.kind()));
                } catch (final RuntimeException e) {
                    context.getLogger().error("[dialogs/{}.yml] cannot be registered as {}", entry.fileId(),
                            entry.key().asString(), e);
                }
            }
        }));
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.TAGS.postFlatten(RegistryKey.DIALOG)
                .newHandler(event -> {
                    dialogs.addToTag(event.registrar(), DialogTagKeys.QUICK_ACTIONS, dialogs.quickActionIds());
                    dialogs.addToTag(event.registrar(), DialogTagKeys.PAUSE_SCREEN_ADDITIONS, dialogs.pauseScreenIds());
                }));
        return dialogs;
    }

    /**
     * Reads {@code dialogs/<fileId>.yml} (copying the one in the jar first if it is missing) and registers it under
     * {@code key}. Mistakes in the file are logged, as for any dialog; a file that is not YAML is not registered.
     *
     * @param fileId the file name without {@code .yml}: lower case letters, digits, {@code -} and {@code _}
     * @param key    the registry key, e.g. {@code auction:help}
     * @return this
     * @throws IllegalArgumentException if an id or the key is not valid, or the file id or key is used already
     * @throws IllegalStateException    if the file is missing and the jar has none
     */
    public synchronized SynthetixDialogs register(final String fileId, final String key) {
        Objects.requireNonNull(fileId, "fileId");
        Objects.requireNonNull(key, "key");
        if (!FILE_ID.matcher(fileId).matches()) {
            throw new IllegalArgumentException("'" + fileId + "' is not a dialog file id (lower case letters, digits, - and"
                    + " _, at most 64)");
        }
        final Key parsed = parseKey(key);
        if (byKey.containsKey(parsed.asString())) {
            throw new IllegalArgumentException("the key '" + key + "' is registered already");
        }
        if (byFile.containsKey(fileId)) {
            throw new IllegalArgumentException("the file '" + fileId + "' is registered already");
        }
        final String text;
        try {
            text = store.read(fileId);
        } catch (final IOException e) {
            context.getLogger().error("[dialogs/{}.yml] cannot be read, not registered", fileId, e);
            return this;
        }
        final DialogParser.Parsed result = parser.parse(text);
        result.errors().forEach(problem -> context.getLogger().warn("[dialogs/{}.yml] {}", fileId, problem));
        result.warnings().forEach(problem -> context.getLogger().warn("[dialogs/{}.yml] {}", fileId, problem));
        final DialogFile file = result.file();
        if (file == null) {
            context.getLogger().error("[dialogs/{}.yml] is not usable, not registered", fileId);
            return this;
        }
        staticProblems(file).forEach(problem -> context.getLogger().error("[dialogs/{}.yml] {}", fileId, problem));
        final Entry entry = new Entry(fileId, parsed, file);
        byKey.put(parsed.asString(), entry);
        byFile.put(fileId, entry);
        return this;
    }

    /**
     * Adds registered dialogs to the "quick actions" dialog of the pause menu (the key bind menu in vanilla 1.21.6+).
     *
     * @param fileIds file ids passed to {@link #register}
     * @return this
     * @throws IllegalArgumentException if an id is not a registered file
     */
    public synchronized SynthetixDialogs quickActions(final String... fileIds) {
        addAll(quickActions, fileIds);
        return this;
    }

    /**
     * Adds registered dialogs to the buttons of the pause screen.
     *
     * @param fileIds file ids passed to {@link #register}
     * @return this
     * @throws IllegalArgumentException if an id is not a registered file
     */
    public synchronized SynthetixDialogs pauseScreen(final String... fileIds) {
        addAll(pauseScreen, fileIds);
        return this;
    }

    // --- used by the service and by the events ------------------------------------------------------------------

    synchronized List<Entry> entries() {
        return List.copyOf(byKey.values());
    }

    private synchronized List<String> quickActionIds() {
        return List.copyOf(quickActions);
    }

    private synchronized List<String> pauseScreenIds() {
        return List.copyOf(pauseScreen);
    }

    private void addAll(final Set<String> into, final String[] fileIds) {
        for (final String id : fileIds) {
            if (!byFile.containsKey(id)) {
                throw new IllegalArgumentException("'" + id + "' is not a registered dialog file (register it first)");
            }
            into.add(id);
        }
    }

    private void addToTag(final io.papermc.paper.tag.PostFlattenTagRegistrar<Dialog> registrar,
                          final TagKey<Dialog> tag, final List<String> fileIds) {
        final List<TypedKey<Dialog>> keys = new ArrayList<>();
        synchronized (this) {
            for (final String id : fileIds) {
                final Entry entry = byFile.get(id);
                if (entry != null) {
                    keys.add(TypedKey.create(RegistryKey.DIALOG, entry.key()));
                }
            }
        }
        if (!keys.isEmpty()) {
            registrar.addToTag(tag, keys);
        }
    }

    private @Nullable TypedKey<Dialog> keyOfFile(final String fileId) {
        final Entry entry;
        synchronized (this) {
            entry = byFile.get(fileId);
        }
        return entry == null ? null : TypedKey.create(RegistryKey.DIALOG, entry.key());
    }

    /** The Paper dialog of a file: static, rendered for nobody. */
    private DialogRenderer.Parts render(final Entry entry) {
        final TextService text = TextService.create();
        final DialogLoader.Resolved resolved = new DialogLoader.Resolved(entry.file(), Set.of());
        final DialogType<Void> type = new DialogType<>(entry.key().asString(), Registrations.none(), resolved, null);
        // no manager: nothing is clicked on this view, it only gives the renderer a name and empty answers
        final DialogViewImpl<Void> view = new DialogViewImpl<>(null, type, null, "<player>", null, null);
        final Set<String> warned = new HashSet<>();
        final DialogRenderer.Env env = new DialogRenderer.Env(text, Items.create(text), () -> null,
                DialogSettings::defaults,
                message -> context.getLogger().warn(message), warned, id -> null);
        final DialogRenderer.StaticKeys keys = new DialogRenderer.StaticKeys(entry.key().namespace(),
                entry.key().value(), this::keyOfFile);
        return new DialogRenderer<>(env, type, view, 0, Set.of(), keys).parts();
    }

    /** What a static dialog cannot do, as messages with the path in the file. */
    static List<Problem> staticProblems(final DialogFile file) {
        final List<Problem> out = new ArrayList<>();
        DialogChecker.texts(file).forEach((path, text) -> {
            final Matcher matcher = BARE_TAG.matcher(text);
            final Set<String> seen = new LinkedHashSet<>();
            while (matcher.find()) {
                final String name = matcher.group(1).toLowerCase(Locale.ROOT);
                if (!TagResolver.standard().has(name) && seen.add(name)) {
                    out.add(new Problem(path, "placeholder <" + name + "> cannot be used in a registry dialog (it is the"
                            + " same for every player), it is shown as text"));
                }
            }
        });
        for (int i = 0; i < file.body().size(); i++) {
            if (file.body().get(i) instanceof DialogBody.Item item
                    && (item.item().slot().isPresent() || item.item().placeholder().isPresent())) {
                out.add(new Problem("body[" + i + "].item", "an item from a player's hand or from code cannot be used in a"
                        + " registry dialog, a barrier is shown"));
            }
        }
        return out;
    }

    private static Key parseKey(final String key) {
        if (key.indexOf(':') < 0) {
            throw new IllegalArgumentException("the key '" + key + "' needs a namespace, e.g. myplugin:" + key);
        }
        try {
            return Key.key(key);
        } catch (final InvalidKeyException e) {
            throw new IllegalArgumentException("'" + key + "' is not a valid key: " + e.getMessage(), e);
        }
    }

    /** A file of the plugin jar (or folder, when run from a build directory); read at once, so nothing stays open. */
    private @Nullable InputStream bundled(final String path) {
        final Path source = context.getPluginSource();
        try {
            if (Files.isDirectory(source)) {
                final Path file = source.resolve(path);
                return Files.exists(file) ? new ByteArrayInputStream(Files.readAllBytes(file)) : null;
            }
            try (ZipFile zip = new ZipFile(source.toFile())) {
                final ZipEntry entry = zip.getEntry(path);
                if (entry == null) {
                    return null;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    return new ByteArrayInputStream(in.readAllBytes());
                }
            }
        } catch (final IOException e) {
            context.getLogger().error("cannot read {} from {}", path, source, e);
            return null;
        }
    }
}
