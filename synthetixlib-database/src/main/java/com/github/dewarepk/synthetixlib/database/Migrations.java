package com.github.dewarepk.synthetixlib.database;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The versioned changes of a plugin's tables, applied by {@link Database#migrate}. Immutable.
 *
 * <pre>{@code
 * // src/main/resources/db/migrations/  V1__init.sql  V2__mail.sql  V2__mail.mysql.sql
 * db.migrate(Migrations.fromResources("db/migrations")
 *         .withCode(3, "import-v1", tx -> tx.update("INSERT INTO {prefix}lots SELECT ...")))
 *         .join();   // in onEnable
 * }</pre>
 *
 * <p><b>Files.</b> {@code V<n>__<name>.sql}, versions {@code 1, 2, 3...} without gaps. {@code V2__mail.mysql.sql} or
 * {@code V2__mail.sqlite.sql} is used instead of {@code V2__mail.sql} on that database; write those only where the
 * dialects differ (types to share: {@code BIGINT, INTEGER, VARCHAR(n), TEXT, BLOB, CHAR(36)}). {@code {prefix}} is
 * replaced by the table prefix. Statements end with a {@code ;} at the end of a line; {@code --} starts a comment. A
 * {@code ;} inside a statement at the end of a line (trigger bodies) is not supported. Do not edit a file after it
 * shipped: its checksum is stored and a change makes {@code migrate} fail.
 *
 * <p><b>Finding the files.</b> The folder is read from the plugin's class loader: the entries of the jar (or the folder
 * on disk in a development run) are listed, nothing is scanned on the class path and no class is loaded. The jar must
 * contain the folder entry, which Gradle's {@code jar} and {@code shadowJar} do.
 *
 * <p><b>MySQL.</b> DDL ({@code CREATE}, {@code ALTER}, ...) commits at once on MySQL, so a migration that fails half way
 * can leave its earlier DDL statements in place and the version is then not recorded. Write small migrations, one change
 * each. SQLite runs DDL inside the transaction and undoes all of it.
 */
public final class Migrations {

    private static final Pattern FILE = Pattern.compile("^V(\\d+)__(.+?)(?:\\.(mysql|sqlite))?\\.sql$");

    /** One version: the scripts per dialect, or code. */
    record Step(int version, String name, @Nullable String base, @Nullable String mysql, @Nullable String sqlite,
                @Nullable CodeMigration code) {

        /** @return the script for {@code dialect}: its own file when there is one, else the plain one; {@code null} for code */
        @Nullable String script(final Dialect dialect) {
            final String own = dialect == Dialect.MYSQL ? mysql : sqlite;
            return own != null ? own : base;
        }

        /** @return the checksum of what runs on {@code dialect} (line endings normalised), stored in the history */
        String checksum(final Dialect dialect) {
            final String script = script(dialect);
            return SqlScript.sha256(script != null ? SqlScript.normalize(script) : "code:" + version + ":" + name);
        }
    }

    private final List<Step> steps;

    private Migrations(final List<Step> steps) {
        this.steps = steps;
    }

    /**
     * Reads {@code V<n>__<name>.sql} files from {@code path} in the jar of the class that calls this (your plugin).
     *
     * @param path folder inside the jar, e.g. {@code "db/migrations"}
     * @throws MigrationException       if a file name is malformed or a version is used twice
     * @throws IllegalArgumentException if the folder does not exist
     */
    public static Migrations fromResources(final String path) {
        final Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        final ClassLoader loader = caller.getClassLoader();
        if (loader == null) {
            throw new IllegalStateException("Cannot find the class loader of " + caller.getName());
        }
        return fromResources(loader, path);
    }

    /** Like {@link #fromResources(String)} with an explicit class loader, e.g. {@code plugin.getClass().getClassLoader()}. */
    public static Migrations fromResources(final ClassLoader loader, final String path) {
        Objects.requireNonNull(loader, "loader");
        final String folder = trimSlashes(Objects.requireNonNull(path, "path"));
        final TreeMap<Integer, Builder> byVersion = new TreeMap<>();
        for (final String file : list(loader, folder)) {
            if (!file.endsWith(".sql")) {
                continue;
            }
            final Matcher match = FILE.matcher(file);
            if (!match.matches()) {
                throw new MigrationException("Migration file '" + file + "' in " + folder
                        + " must be named V<number>__<name>.sql (or .mysql.sql / .sqlite.sql)");
            }
            final int version = parseVersion(match.group(1), file);
            byVersion.computeIfAbsent(version, Builder::new).add(match.group(2), match.group(3),
                    read(loader, folder + "/" + file), file);
        }
        final List<Step> steps = new ArrayList<>();
        byVersion.values().forEach(builder -> steps.add(builder.build()));
        return new Migrations(Collections.unmodifiableList(steps));
    }

    /**
     * Adds a migration written in Java, for a data move between two SQL versions.
     *
     * @param version the version it takes in the sequence (must not be used by a file)
     * @param name    what it does, e.g. {@code "import-v1"}; part of its checksum, so do not rename it later
     * @param code    runs in the migration's transaction
     * @return new migrations with the code added
     * @throws MigrationException if the version is already used
     */
    public Migrations withCode(final int version, final String name, final CodeMigration code) {
        Objects.requireNonNull(code, "code");
        if (version < 1) {
            throw new MigrationException("Migration versions start at 1, got " + version);
        }
        if (Objects.requireNonNull(name, "name").isBlank()) {
            throw new MigrationException("Code migration " + version + " needs a name");
        }
        for (final Step step : steps) {
            if (step.version() == version) {
                throw new MigrationException("Version " + version + " is used twice: '" + step.name() + "' and '"
                        + name + "'");
            }
        }
        final List<Step> all = new ArrayList<>(steps);
        all.add(new Step(version, name, null, null, null, code));
        all.sort((a, b) -> Integer.compare(a.version(), b.version()));
        return new Migrations(Collections.unmodifiableList(all));
    }

    /**
     * @return the steps in order, once the sequence is complete
     * @throws MigrationException if a version is missing: files and code together must be 1, 2, 3...
     */
    List<Step> steps() {
        int expected = 1;
        for (final Step step : steps) {
            if (step.version() != expected) {
                throw new MigrationException("Migration " + expected + " is missing: versions must be 1, 2, 3... without "
                        + "gaps (found " + step.version() + " '" + step.name() + "' next)");
            }
            expected++;
        }
        return steps;
    }

    // --- loading ----------------------------------------------------------------------------------------------------

    /** The files of one version while they are read. */
    private static final class Builder {

        private final int version;
        private @Nullable String name;
        private @Nullable String base;
        private @Nullable String mysql;
        private @Nullable String sqlite;

        Builder(final int version) {
            this.version = version;
        }

        void add(final String fileName, final @Nullable String dialect, final String script, final String file) {
            if (name != null && !name.equals(fileName)) {
                throw new MigrationException("Version " + version + " is used twice: '" + name + "' and '" + fileName
                        + "' (" + file + ")");
            }
            name = fileName;
            if (dialect == null) {
                base = script;
            } else if (dialect.equals("mysql")) {
                mysql = script;
            } else {
                sqlite = script;
            }
        }

        Step build() {
            return new Step(version, Objects.requireNonNull(name), base, mysql, sqlite, null);
        }
    }

    private static int parseVersion(final String digits, final String file) {
        try {
            final int version = Integer.parseInt(digits);
            if (version >= 1) {
                return version;
            }
        } catch (final NumberFormatException ignored) {
            // reported below
        }
        throw new MigrationException("Migration file '" + file + "' has no valid version: versions start at 1");
    }

    private static String trimSlashes(final String path) {
        int start = 0;
        int end = path.length();
        while (start < end && path.charAt(start) == '/') {
            start++;
        }
        while (end > start && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(start, end);
    }

    private static String read(final ClassLoader loader, final String resource) {
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new MigrationException("Cannot read " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new MigrationException("Cannot read " + resource + ": " + e.getMessage(), e);
        }
    }

    /** @return the file names (not paths) directly inside {@code folder}, from a jar or a directory */
    private static List<String> list(final ClassLoader loader, final String folder) {
        URL url = loader.getResource(folder);
        if (url == null) {
            url = loader.getResource(folder + "/");
        }
        if (url == null) {
            throw new IllegalArgumentException("Migration folder '" + folder + "' not found in the plugin jar; it must "
                    + "contain the folder entry (Gradle's jar task does)");
        }
        try {
            return switch (url.getProtocol()) {
                case "jar" -> listJar(url, folder);
                case "file" -> listDirectory(Path.of(url.toURI()));
                default -> throw new MigrationException("Cannot list " + url + ": only jar and file locations are supported");
            };
        } catch (final IOException | URISyntaxException e) {
            throw new MigrationException("Cannot list the migration folder '" + folder + "': " + e.getMessage(), e);
        }
    }

    private static List<String> listJar(final URL url, final String folder) throws IOException {
        final JarURLConnection connection = (JarURLConnection) url.openConnection();
        connection.setUseCaches(false);   // our own JarFile: closing it must not close one the class loader uses
        final String prefix = folder + "/";
        final TreeSet<String> names = new TreeSet<>();
        try (JarFile jar = connection.getJarFile()) {
            jar.stream().forEach(entry -> {
                final String name = entry.getName();
                if (!entry.isDirectory() && name.startsWith(prefix) && name.indexOf('/', prefix.length()) < 0) {
                    names.add(name.substring(prefix.length()));
                }
            });
        }
        return new ArrayList<>(names);
    }

    private static List<String> listDirectory(final Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).map(file -> file.getFileName().toString()).sorted().toList();
        }
    }
}
