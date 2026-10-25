package com.github.dewarepk.synthetixlib.config;

import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.config.codec.Problem;
import com.github.dewarepk.synthetixlib.config.migration.Migration;
import com.github.dewarepk.synthetixlib.config.yaml.AtomicFiles;
import com.github.dewarepk.synthetixlib.config.yaml.FileConfig;
import com.github.dewarepk.synthetixlib.config.yaml.YamlTrees;
import com.github.dewarepk.synthetixlib.core.text.TextService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Messages in several languages, one YAML file per language ({@code lang/en_US.yml}, {@code lang/th_TH.yml}), all
 * mapped to the same record of MiniMessage strings; obtained from {@link Configs#locale}.
 *
 * <pre>{@code
 * LocaleHandle<Messages> lang = configs.locale("lang", Messages.class, Messages::defaults)
 *         .defaultLocale(Locale.US)
 *         .bundled("th_TH")
 *         .perPlayer(true)
 *         .load();
 * lang.send(player, Messages::bidPlaced, Placeholder.unparsed("amount", "100"));
 * lang.sendLines(sender, Messages::help);
 * Component outbid = lang.get(player, Messages::outbid);
 * }</pre>
 *
 * <p>The file of the default locale is created from the defaults; bundled files are copied from the plugin jar if
 * they are missing. Every file then gets the keys it lacks from the defaults (with comments), like a config file,
 * and a plain reload never writes a file. That includes the entries a map of the record lacks (a section such as
 * {@code dialog: {sell-title: ...}}), taken from the bundled file of the same language if that has them, else from
 * the defaults; an entry set to an empty string is kept (it switches the message off). Other {@code <tag>.yml} files that server owners add are loaded as well.
 * A message that is an empty string is disabled: {@code send} does nothing and {@code get} returns an empty
 * component. A list message ({@code List<String>}, via {@code sendLines} / {@code getLines}) is one line per entry.
 * Thread-safe: the loaded messages are an immutable snapshot that {@link #reload()} swaps.
 *
 * <p>Which language a message uses (for an explicit {@link Locale}, or for an audience that has one - a Paper player
 * does - when {@code perPlayer} is on): the file with the exact tag of the locale ({@code th_TH}), else the file of
 * its language ({@code th}), else any file of the same language, else the default locale's file. The names of files
 * are compared ignoring case. {@code perPlayer} only decides whether an {@link Audience} is asked for its locale; an
 * explicit {@code Locale} argument is always honoured.
 *
 * <p>Java cannot overload {@code send} for {@code Function<T, String>} and {@code Function<T, List<String>>} (same
 * erasure), which is why the list variants are called {@code sendLines} and {@code getLines}.
 *
 * @param <T> the record of messages
 */
public final class LocaleHandle<T> {

    private static final Pattern TAG = Pattern.compile("[a-z]{2,3}(_[A-Za-z0-9]{2,8})*");
    private static final String EXTENSION = ".yml";

    private record Snapshot<T>(Map<String, T> byTag, T fallback) {
    }

    private final ConfigHost host;
    private final TextService text;
    private final Path dir;
    private final String name;
    private final Class<T> type;
    private final Supplier<T> defaults;
    private final Codecs codecs;
    private final int version;
    private final List<Migration> migrations;
    private final Locale defaultLocale;
    private final String defaultTag;
    private final List<String> bundled;
    private final boolean perPlayer;
    private final Object lock = new Object();
    private volatile Snapshot<T> snapshot;

    LocaleHandle(final ConfigHost host, final TextService text, final Path dir, final String name,
                 final Class<T> type, final Supplier<T> defaults, final Codecs codecs, final int version,
                 final List<Migration> migrations, final Locale defaultLocale, final List<String> bundled,
                 final boolean perPlayer) {
        this.host = host;
        this.text = text;
        this.dir = dir;
        this.name = name;
        this.type = type;
        this.defaults = defaults;
        this.codecs = codecs;
        this.version = version;
        this.migrations = List.copyOf(migrations);
        this.defaultLocale = defaultLocale;
        this.defaultTag = tagOf(defaultLocale);
        this.bundled = List.copyOf(bundled);
        this.perPlayer = perPlayer;
        if (!TAG.matcher(defaultTag).matches()) {
            throw new IllegalArgumentException("\"" + defaultTag + "\" is not a usable default locale, e.g. en_US");
        }
        for (final String tag : bundled) {
            if (!TAG.matcher(tag).matches()) {
                throw new IllegalArgumentException("\"" + tag + "\" is not a locale tag like th_TH");
            }
        }
        final T fallback = Objects.requireNonNull(defaults.get(), "defaults must not return null");
        this.snapshot = new Snapshot<>(Map.of(), fallback);
    }

    /** First load, called by the builder: creates what is missing and logs what it found. */
    static <T> LocaleHandle<T> open(final LocaleHandle<T> handle) {
        final FilesReloadResult result = handle.reloadInternal();
        result.files().forEach((tag, one) -> {
            one.errors().forEach(m -> handle.host.logger().warning("[" + handle.name + "/" + tag + "] " + m));
            one.warnings().forEach(m -> handle.host.logger().warning("[" + handle.name + "/" + tag + "] " + m));
        });
        return handle;
    }

    // --- reading ------------------------------------------------------------------------------------------------

    /**
     * The locale whose file is used when nothing better matches; also what to use for a receiver that has no locale,
     * e.g. the console.
     *
     * <pre>{@code
     * Locale forConsole = lang.defaultLocale();
     * }</pre>
     *
     * @return the default locale given to the builder
     */
    public Locale defaultLocale() {
        return defaultLocale;
    }

    /**
     * Sends a message in the language of {@code audience}.
     *
     * @param audience  the receiver
     * @param message   picks the message from the record, e.g. {@code Messages::bidPlaced}
     * @param resolvers tags for this message, e.g. {@code Placeholder.unparsed("amount", "100")}
     */
    public void send(final Audience audience, final Function<T, String> message, final TagResolver... resolvers) {
        Objects.requireNonNull(audience, "audience");
        final String raw = message.apply(messages(localeOf(audience)));
        if (raw != null && !raw.isEmpty()) {
            text.send(audience, raw, resolvers);
        }
    }

    /** Sends each line of a list message as its own chat message; an empty list sends nothing. */
    public void sendLines(final Audience audience, final Function<T, List<String>> lines,
                          final TagResolver... resolvers) {
        Objects.requireNonNull(audience, "audience");
        for (final String line : lines.apply(messages(localeOf(audience)))) {
            text.send(audience, line, resolvers);
        }
    }

    /** @return the message in the language of {@code audience}; empty if the message is disabled */
    public Component get(final Audience audience, final Function<T, String> message,
                         final TagResolver... resolvers) {
        return get(localeOf(Objects.requireNonNull(audience, "audience")), message, resolvers);
    }

    /**
     * @param locale the language to look for, {@code null} for the default; always honoured, whether or not
     *               {@code perPlayer} is on
     * @return the message, or an empty component if it is disabled
     */
    public Component get(final @Nullable Locale locale, final Function<T, String> message,
                         final TagResolver... resolvers) {
        final String raw = message.apply(messages(locale));
        return raw == null || raw.isEmpty() ? Component.empty() : text.parse(raw, resolvers);
    }

    /** @return the lines of a list message joined with a newline in the language of {@code audience} */
    public Component getLines(final Audience audience, final Function<T, List<String>> lines,
                              final TagResolver... resolvers) {
        return getLines(localeOf(Objects.requireNonNull(audience, "audience")), lines, resolvers);
    }

    /** @return the lines of a list message joined with a newline; empty if the list is */
    public Component getLines(final @Nullable Locale locale, final Function<T, List<String>> lines,
                              final TagResolver... resolvers) {
        final List<Component> parsed = lines.apply(messages(locale)).stream()
                .map(line -> text.parse(line, resolvers)).toList();
        return Component.join(JoinConfiguration.newlines(), parsed);
    }

    private @Nullable Locale localeOf(final Audience audience) {
        if (!perPlayer) {
            return null;
        }
        if (audience instanceof Player player) {
            return player.locale();
        }
        return audience.get(Identity.LOCALE).orElse(null);
    }

    /** The messages to use for {@code locale}; see the class description for the order. */
    private T messages(final @Nullable Locale locale) {
        final Snapshot<T> current = snapshot;
        if (locale != null) {
            final String exact = tagOf(locale).toLowerCase(Locale.ROOT);
            final String language = locale.getLanguage().toLowerCase(Locale.ROOT);
            T found = lookup(current.byTag(), exact);
            if (found == null && !language.isEmpty()) {
                found = lookup(current.byTag(), language);
                if (found == null && defaultTag.toLowerCase(Locale.ROOT).startsWith(language + "_")) {
                    found = current.byTag().get(defaultTag);
                }
                if (found == null) {
                    for (final Map.Entry<String, T> entry : current.byTag().entrySet()) {
                        if (entry.getKey().toLowerCase(Locale.ROOT).startsWith(language + "_")) {
                            found = entry.getValue();
                            break;
                        }
                    }
                }
            }
            if (found != null) {
                return found;
            }
        }
        final T inDefaultFile = current.byTag().get(defaultTag);
        return inDefaultFile != null ? inDefaultFile : current.fallback();
    }

    private static <V> @Nullable V lookup(final Map<String, V> byTag, final String lowerTag) {
        for (final Map.Entry<String, V> entry : byTag.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(lowerTag)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String tagOf(final Locale locale) {
        return locale.toString();
    }

    // --- reload -------------------------------------------------------------------------------------------------

    /**
     * Reads every language file again on the calling thread and swaps the messages. A missing file of the default
     * or of a bundled locale is created again; a file that cannot be parsed keeps its current messages; the others
     * still load.
     *
     * @return one result per file, by locale tag
     * @throws IllegalStateException if the {@link Configs} service is closed
     */
    public FilesReloadResult reload() {
        if (host.isClosed()) {
            throw new IllegalStateException("Configs is closed");
        }
        return reloadInternal();
    }

    private FilesReloadResult reloadInternal() {
        synchronized (lock) {
            final Set<String> tags = new LinkedHashSet<>();
            tags.add(defaultTag);
            tags.addAll(bundled);
            final Map<String, ReloadResult> results = new LinkedHashMap<>();
            for (final String tag : bundled) {
                copyBundled(tag, results);
            }
            try {
                tags.addAll(scan());
            } catch (final IOException e) {
                results.put("", new ReloadResult(List.of("cannot list " + dir + ": " + e), List.of(), false));
            }

            final Snapshot<T> before = snapshot;
            final Map<String, T> byTag = new TreeMap<>();
            for (final String tag : tags) {
                final Path file = file(tag);
                if (!Files.exists(file) && !tag.equals(defaultTag)) {
                    continue;   // a bundled file that could not be copied
                }
                final FileConfig.Result<T> result = load(tag);
                T value = result.value();
                if (value == null) {
                    value = before.byTag().get(tag);
                }
                final boolean changed = result.value() != null && !result.value().equals(before.byTag().get(tag));
                final ReloadResult previous = results.get(tag);
                final List<String> errors = new ArrayList<>(previous != null ? previous.errors() : List.of());
                errors.addAll(result.errors().stream().map(Problem::toString).toList());
                results.put(tag, new ReloadResult(errors, result.warnings().stream().map(Problem::toString).toList(),
                        changed));
                if (value != null) {
                    byTag.put(tag, value);
                }
            }
            final List<String> removed = before.byTag().keySet().stream()
                    .filter(tag -> !byTag.containsKey(tag)).toList();
            final T defaultValue = byTag.get(defaultTag);
            snapshot = new Snapshot<>(Collections.unmodifiableMap(byTag),
                    defaultValue != null ? defaultValue : before.fallback());
            return new FilesReloadResult(results, removed);
        }
    }

    /** Copies {@code <dir>/<tag>.yml} from the plugin jar if the file is missing. */
    private void copyBundled(final String tag, final Map<String, ReloadResult> results) {
        final Path file = file(tag);
        if (Files.exists(file)) {
            return;
        }
        final String resource = name.replace('\\', '/') + "/" + tag + EXTENSION;
        try (InputStream in = host.resource(resource)) {
            if (in == null) {
                results.put(tag, new ReloadResult(List.of("bundled file " + resource + " is not in the plugin jar"),
                        List.of(), false));
                return;
            }
            final String jarText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            final String commented = new FileConfig<>(file, type, defaults, codecs, version, migrations)
                    .computeBundled(jarText).text();
            AtomicFiles.write(file, (commented != null ? commented : jarText).getBytes(StandardCharsets.UTF_8));
        } catch (final IOException e) {
            results.put(tag, new ReloadResult(List.of("cannot copy bundled " + resource + ": " + e), List.of(),
                    false));
        }
    }

    private FileConfig.Result<T> load(final String tag) {
        try {
            return new FileConfig<>(file(tag), type, defaults, codecs, version, migrations)
                    .fillingMaps(bundledData(tag)).load();
        } catch (final RuntimeException e) {
            return new FileConfig.Result<>(null, List.of(new Problem("", "cannot load: " + e)), List.of());
        }
    }

    /** The data of {@code lang/<tag>.yml} in the plugin jar, to fill map entries a file lacks in that language. */
    private Map<String, Object> bundledData(final String tag) {
        final String resource = name.replace('\\', '/') + "/" + tag + EXTENSION;
        try (InputStream in = host.resource(resource)) {
            if (in == null) {
                return Map.of();
            }
            final Map<String, Object> data = YamlTrees.toPlain(
                    YamlTrees.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            data.remove(FileConfig.VERSION_KEY);
            return data;
        } catch (final IOException | InvalidConfigurationException e) {
            return Map.of();
        }
    }

    /** Tags of the files in the folder whose name looks like a locale. */
    private Set<String> scan() throws IOException {
        final Set<String> tags = new LinkedHashSet<>();
        if (!Files.isDirectory(dir)) {
            return tags;
        }
        try (Stream<Path> list = Files.list(dir)) {
            list.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
                    .filter(fileName -> fileName.endsWith(EXTENSION))
                    .map(fileName -> fileName.substring(0, fileName.length() - EXTENSION.length()))
                    .filter(tag -> TAG.matcher(tag).matches())
                    .sorted()
                    .forEach(tags::add);
        }
        return tags;
    }

    private Path file(final String tag) {
        return dir.resolve(tag + EXTENSION);
    }
}
