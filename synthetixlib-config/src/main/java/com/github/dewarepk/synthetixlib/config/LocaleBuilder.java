package com.github.dewarepk.synthetixlib.config;

import com.github.dewarepk.synthetixlib.config.migration.ConfigTree;
import com.github.dewarepk.synthetixlib.config.migration.Migration;
import com.github.dewarepk.synthetixlib.core.text.TextService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Describes a {@link LocaleHandle} before it is loaded; obtained from {@link Configs#locale}. Not thread-safe.
 *
 * <pre>{@code
 * LocaleHandle<Messages> lang = configs.locale("lang", Messages.class, Messages::defaults)
 *         .defaultLocale(Locale.US)
 *         .bundled("th_TH")
 *         .perPlayer(true)
 *         .text(lib.text().with(Placeholder.parsed("prefix", "<gold>[Auction]</gold> ")))
 *         .load();
 * }</pre>
 *
 * @param <T> the record of messages
 */
public final class LocaleBuilder<T> {

    private final Configs configs;
    private final Path dir;
    private final String name;
    private final Class<T> type;
    private final Supplier<T> defaults;
    private final List<Migration> migrations = new ArrayList<>();
    private final List<String> bundled = new ArrayList<>();
    private Locale defaultLocale = Locale.US;
    private boolean perPlayer;
    private int version = 1;
    private TextService text;

    LocaleBuilder(final Configs configs, final Path dir, final String name, final Class<T> type,
                  final Supplier<T> defaults) {
        this.configs = configs;
        this.dir = dir;
        this.name = name;
        this.type = type;
        this.defaults = defaults;
        this.text = configs.lib().text();
    }

    /**
     * The language of the defaults, whose file is created from them and used when nothing better matches.
     * Default {@link Locale#US} ({@code en_US.yml}).
     *
     * @return this
     */
    public LocaleBuilder<T> defaultLocale(final Locale locale) {
        this.defaultLocale = Objects.requireNonNull(locale, "locale");
        return this;
    }

    /**
     * Languages shipped inside the plugin jar as {@code <dir>/<tag>.yml}; each is copied to the data folder if
     * missing, then lacking keys are filled from the defaults.
     *
     * @param tags locale tags such as {@code th_TH}
     * @return this
     */
    public LocaleBuilder<T> bundled(final String... tags) {
        bundled.addAll(List.of(tags));
        return this;
    }

    /**
     * Whether messages go out in the language of the receiver (its locale, falling back to the default). Default
     * {@code false}: everybody gets the default locale.
     *
     * @return this
     */
    public LocaleBuilder<T> perPlayer(final boolean perPlayer) {
        this.perPlayer = perPlayer;
        return this;
    }

    /**
     * The text service that renders the messages, e.g. {@code lib.text().with(prefixTag)} so a {@code <prefix>} tag
     * works in every message. Default {@code lib.text()}.
     *
     * @return this
     */
    public LocaleBuilder<T> text(final TextService text) {
        this.text = Objects.requireNonNull(text, "text");
        return this;
    }

    /**
     * The version of the file format, written as {@code config-version} in every file. Default {@code 1}.
     *
     * @param version at least 1
     * @return this
     */
    public LocaleBuilder<T> version(final int version) {
        if (version < 1) {
            throw new IllegalArgumentException("version must be at least 1, got " + version);
        }
        this.version = version;
        return this;
    }

    /**
     * Adds a step that turns a file of version {@code from} into version {@code to}; see
     * {@link ConfigFileBuilder#migrate}. It runs on every language file.
     *
     * @return this
     */
    public LocaleBuilder<T> migrate(final int from, final int to, final Consumer<ConfigTree> action) {
        migrations.add(new Migration(from, to, action));
        return this;
    }

    /**
     * Creates and copies what is missing and reads every language file. Synchronous; call it in {@code onEnable}.
     * Problems with the content of a file are logged and never thrown: a missing or invalid message uses the default.
     *
     * @return the loaded messages
     * @throws IllegalArgumentException if the record, the version, the migrations or a tag are not valid
     * @throws IllegalStateException    if the {@link Configs} service is closed
     */
    public LocaleHandle<T> load() {
        if (configs.isClosed()) {
            throw new IllegalStateException("Configs is closed");
        }
        return LocaleHandle.open(new LocaleHandle<>(configs.host(), text, dir, name, type, defaults,
                configs.codecs(), version, migrations, defaultLocale, bundled, perPlayer));
    }
}
