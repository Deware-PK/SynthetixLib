package com.github.dewarepk.synthetixlib.config.yaml;

import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.config.codec.Problem;
import com.github.dewarepk.synthetixlib.config.mapping.LenientResult;
import com.github.dewarepk.synthetixlib.config.mapping.Schema;
import com.github.dewarepk.synthetixlib.config.migration.ConfigTree;
import com.github.dewarepk.synthetixlib.config.migration.Migration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * One YAML file mapped to a record, and the algorithm that loads it (docs/design/config.md section 2):
 *
 * <ol>
 *   <li>A missing file is written from the defaults, with comments.</li>
 *   <li>Otherwise it is read with comments; {@code config-version} (missing = 1) selects the migrations.</li>
 *   <li>An older version is backed up as {@code <file>.v<old>.bak}, then migrated step by step.</li>
 *   <li>The data is mapped to the record field by field: a missing key takes the default and is added to the file
 *       with its comment, an invalid value takes the default and is reported but stays in the file, an unknown
 *       key stays and is reported.</li>
 *   <li>The file is written only if it was created, migrated or got keys added. A plain reload never writes it
 *       (SnapPlug bug #6), and comments written by the user survive a write.</li>
 * </ol>
 *
 * Stateless apart from its configuration; {@link #load()} can be called again for every reload. Not thread-safe:
 * callers serialise {@code load()}.
 *
 * @param <T> the config record
 * @apiNote Internal: use {@code Configs#file}
 */
@org.jetbrains.annotations.ApiStatus.Internal
public final class FileConfig<T> {

    /** Key of the version number; always the first key of a file. */
    public static final String VERSION_KEY = "config-version";

    /**
     * What one {@link #load()} found.
     *
     * @param value    the config, or {@code null} if the file could not be read or is not valid YAML (a reload then
     *                 keeps what it has); a missing or damaged field is replaced by its default, not {@code null}
     * @param errors   values that were invalid or things that went wrong (read, write, migration)
     * @param warnings things worth knowing that changed nothing, e.g. unknown keys
     * @param <T>      the config record
     */
    public record Result<T>(@Nullable T value, List<Problem> errors, List<Problem> warnings) {
    }

    /**
     * What {@link #compute()} found and what it would write.
     *
     * @param result what {@link #load()} would return, before any write error is added
     * @param text   the complete new content of the file, or {@code null} if the file stays as it is
     * @param <T>    the config record
     */
    public record Computed<T>(Result<T> result, @Nullable String text) {
    }

    private final Path file;
    private final Class<T> type;
    private final Supplier<T> defaults;
    private final Codecs codecs;
    private final int version;
    private final List<Migration> migrations;
    private final @Nullable Map<String, Object> mapSupplement;

    /**
     * @param file       the YAML file, need not exist
     * @param type       a public record type
     * @param defaults   the defaults, called on every load
     * @param version    the current version of the file format, at least 1
     * @param migrations steps to the current version, in any order
     * @throws IllegalArgumentException if {@code version} is below 1, a migration starts at the same version as
     *                                  another or ends above {@code version}
     */
    public FileConfig(final Path file, final Class<T> type, final Supplier<T> defaults, final Codecs codecs,
                      final int version, final List<Migration> migrations) {
        this(file, type, defaults, codecs, version, migrations, null);
    }

    private FileConfig(final Path file, final Class<T> type, final Supplier<T> defaults, final Codecs codecs,
                       final int version, final List<Migration> migrations,
                       final @Nullable Map<String, Object> mapSupplement) {
        if (version < 1) {
            throw new IllegalArgumentException("version must be at least 1, got " + version);
        }
        for (final Migration migration : migrations) {
            if (migration.to() > version) {
                throw new IllegalArgumentException("Migration " + migration.from() + " -> " + migration.to()
                        + " goes beyond the current version " + version);
            }
            if (migrations.stream().anyMatch(other -> other != migration && other.from() == migration.from())) {
                throw new IllegalArgumentException("Two migrations start at version " + migration.from());
            }
        }
        this.file = Objects.requireNonNull(file, "file");
        this.type = Objects.requireNonNull(type, "type");
        this.defaults = Objects.requireNonNull(defaults, "defaults");
        this.codecs = Objects.requireNonNull(codecs, "codecs");
        this.version = version;
        this.migrations = migrations.stream().sorted(Comparator.comparingInt(Migration::from)).toList();
        this.mapSupplement = mapSupplement;
    }

    /**
     * The same file config for a locale file: every entry a map of the record lacks is added to the file as well (a
     * config file leaves a removed map entry alone). The added text is the default's, or the one in
     * {@code supplement} if that has it.
     *
     * @param supplement a data tree shaped like the record, e.g. the bundled file of the same language; may be empty
     */
    public FileConfig<T> fillingMaps(final Map<String, Object> supplement) {
        return new FileConfig<>(file, type, defaults, codecs, version, migrations,
                Objects.requireNonNull(supplement, "supplement"));
    }

    /** @return the file this config is read from and saved to */
    public Path file() {
        return file;
    }

    /**
     * Reads the file and writes it if needed, as described in the class description. Does IO on the calling thread.
     *
     * @throws IllegalArgumentException if {@code type} or the defaults cannot be mapped (a programming error)
     * @throws NullPointerException     if the defaults supplier returns {@code null}
     */
    public Result<T> load() {
        final Computed<T> computed = compute();
        final Result<T> result = computed.result();
        if (computed.text() == null) {
            return result;
        }
        final List<Problem> errors = new ArrayList<>(result.errors());
        try {
            AtomicFiles.write(file, computed.text().getBytes(StandardCharsets.UTF_8));
        } catch (final IOException e) {
            errors.add(new Problem("", "cannot save " + file.getFileName() + ": " + e));
        }
        return new Result<>(result.value(), errors, result.warnings());
    }

    /**
     * Does everything {@link #load()} does except writing the file: returns the content it would write instead. The
     * only side effect is the {@code .bak} copy before a migration. Lets a caller that owns the write order (a
     * collection) decide when and where the text goes.
     *
     * @throws IllegalArgumentException if {@code type} or the defaults cannot be mapped (a programming error)
     * @throws NullPointerException     if the defaults supplier returns {@code null}
     */
    public Computed<T> compute() {
        final T defaultValue = Objects.requireNonNull(defaults.get(), "defaults must not return null");
        final Map<String, List<String>> comments = Schema.comments(type);
        final List<Problem> errors = new ArrayList<>();
        final List<Problem> warnings = new ArrayList<>();
        if (!Files.exists(file)) {
            final String created = createDefaults(defaultValue, comments);
            return new Computed<>(new Result<>(defaultValue, errors, warnings), created);
        }

        final String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            errors.add(new Problem("", "cannot read " + file.getFileName() + ": " + e));
            return new Computed<>(new Result<>(null, errors, warnings), null);
        }
        return computeText(text, defaultValue, comments, false);
    }

    /**
     * Like {@link #compute()} for text that is not on disk yet, e.g. a file shipped in the plugin jar and about to be
     * copied: besides the missing keys, every key without a comment gets the comment of the record. The returned
     * text is the complete content to write once.
     *
     * @param text the YAML text to start from
     */
    public Computed<T> computeBundled(final String text) {
        final T defaultValue = Objects.requireNonNull(defaults.get(), "defaults must not return null");
        return computeText(text, defaultValue, Schema.comments(type), true);
    }

    private Computed<T> computeText(final String text, final T defaultValue,
                                    final Map<String, List<String>> comments, final boolean fillComments) {
        final List<Problem> errors = new ArrayList<>();
        final List<Problem> warnings = new ArrayList<>();
        YamlConfiguration yaml;
        try {
            yaml = YamlTrees.parse(text);
        } catch (final InvalidConfigurationException e) {
            errors.add(new Problem("", "invalid YAML, the file is left as it is: " + firstLine(e.getMessage())));
            return new Computed<>(new Result<>(null, errors, warnings), null);
        }

        boolean save = false;
        boolean mayWrite = true;
        if (fillComments) {
            save |= fillComments(yaml, comments);
        }
        final int found = readVersion(yaml, warnings);
        if (found > version) {
            warnings.add(new Problem(VERSION_KEY, found + " is newer than the " + version
                    + " this plugin knows; the file will not be changed"));
            mayWrite = false;
        } else if (found < version) {
            final MigrationOutcome outcome = migrate(yaml, text, found, errors);
            yaml = outcome.yaml();
            mayWrite = outcome.ok();
            save = outcome.ok();
        }
        if (mayWrite && yaml.get(VERSION_KEY) == null) {
            yaml = YamlTrees.withFirst(yaml, VERSION_KEY, version);
            save = true;
        } else if (mayWrite && found < version) {
            yaml.set(VERSION_KEY, version);
        }

        final Map<String, Object> data = YamlTrees.toPlain(yaml);
        data.remove(VERSION_KEY);
        final LenientResult<T> decoded = mapSupplement == null
                ? codecs.decodeLenient(data, type, defaultValue)
                : codecs.decodeLenientFillingMaps(data, type, defaultValue, mapSupplement);
        errors.addAll(decoded.errors());
        warnings.addAll(decoded.warnings());
        for (final LenientResult.Missing missing : decoded.missing()) {
            save |= addMissing(yaml, missing, comments);
        }

        final String out = save && mayWrite ? yaml.saveToString() : null;
        return new Computed<>(new Result<>(decoded.value(), errors, warnings), out);
    }

    /**
     * The content of a new file holding {@code value}: header, version, keys and comments, as written on first
     * start. Reads and writes nothing.
     *
     * @throws IllegalArgumentException if {@code value} cannot be encoded
     */
    public String render(final T value) {
        return createDefaults(Objects.requireNonNull(value, "value"), Schema.comments(type));
    }

    // --- first start --------------------------------------------------------------------------------------------

    private String createDefaults(final T defaultValue, final Map<String, List<String>> comments) {
        final YamlConfiguration yaml = YamlTrees.create();
        final List<String> header = Schema.header(type);
        if (!header.isEmpty()) {
            yaml.options().setHeader(header);
        }
        yaml.set(VERSION_KEY, version);
        final Object tree = codecs.encode(defaultValue, type);
        for (final Map.Entry<?, ?> entry : ((Map<?, ?>) tree).entrySet()) {
            final String key = String.valueOf(entry.getKey());
            YamlTrees.put(yaml, key, entry.getValue(), key, comments);
        }
        return yaml.saveToString();
    }

    // --- migrations ---------------------------------------------------------------------------------------------

    private record MigrationOutcome(YamlConfiguration yaml, boolean ok) {
    }

    /** Backs up the file, then runs the steps from {@code found} up. On failure the original data is returned. */
    private MigrationOutcome migrate(final YamlConfiguration yaml, final String text, final int found,
                                     final List<Problem> errors) {
        final Path backup = file.resolveSibling(file.getFileName() + ".v" + found + ".bak");
        try {
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException e) {
            errors.add(new Problem("", "cannot back up " + file.getFileName() + " to " + backup.getFileName()
                    + ", not migrating: " + e));
            return new MigrationOutcome(yaml, false);
        }
        final ConfigTree tree = ConfigTree.of(yaml);
        int current = found;
        try {
            while (current < version) {
                final int from = current;
                final Migration step = migrations.stream().filter(m -> m.from() >= from).findFirst().orElse(null);
                if (step == null) {
                    break;
                }
                step.action().accept(tree);
                current = step.to();
            }
            return new MigrationOutcome(yaml, true);
        } catch (final RuntimeException e) {
            errors.add(new Problem("", "migration from version " + current + " failed, the file is left as it is: "
                    + e));
            try {
                return new MigrationOutcome(YamlTrees.parse(text), false);
            } catch (final InvalidConfigurationException unlikely) {
                throw new IllegalStateException("File parsed before but not now", unlikely);
            }
        }
    }

    private int readVersion(final YamlConfiguration yaml, final List<Problem> warnings) {
        final Object raw = yaml.get(VERSION_KEY);
        if (raw == null) {
            return 1;
        }
        if (raw instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue())
                && number.intValue() >= 1) {
            return number.intValue();
        }
        warnings.add(new Problem(VERSION_KEY, "must be a whole number from 1, assuming 1 (was " + raw + ")"));
        return 1;
    }

    /** Gives every existing key that has no comment the comment of the record; returns whether any was added. */
    private static boolean fillComments(final YamlConfiguration yaml, final Map<String, List<String>> comments) {
        boolean changed = false;
        for (final Map.Entry<String, List<String>> entry : comments.entrySet()) {
            final String[] path = entry.getKey().split("\\.");
            ConfigurationSection section = yaml;
            for (int i = 0; i < path.length - 1 && section != null; i++) {
                section = section.getConfigurationSection(path[i]);
            }
            final String key = path[path.length - 1];
            if (section != null && section.contains(key, true) && section.getComments(key).isEmpty()) {
                section.setComments(key, entry.getValue());
                changed = true;
            }
        }
        return changed;
    }

    // --- writing ------------------------------------------------------------------------------------------------

    /** Adds a missing key (with the comments of everything below it); returns whether the file changed. */
    private boolean addMissing(final YamlConfiguration yaml, final LenientResult.Missing missing,
                               final Map<String, List<String>> comments) {
        final List<String> path = missing.path();
        ConfigurationSection section = yaml;
        for (int i = 0; i < path.size() - 1; i++) {
            section = section.getConfigurationSection(path.get(i));
            if (section == null) {
                return false;
            }
        }
        YamlTrees.put(section, path.get(path.size() - 1), missing.value(), String.join(".", path), comments);
        return true;
    }

    private static String firstLine(final @Nullable String message) {
        if (message == null) {
            return "unknown error";
        }
        final int end = message.indexOf('\n');
        return end < 0 ? message : message.substring(0, end);
    }
}
