package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.config.yaml.AtomicFiles;
import com.github.dewarepk.synthetixlib.config.yaml.YamlTrees;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import org.bukkit.configuration.InvalidConfigurationException;
import org.jspecify.annotations.Nullable;

/**
 * Where dialog files live: {@code <data folder>/dialogs/<id>.yml}, created from {@code dialogs/<id>.yml} in the plugin
 * jar when missing. A file that exists is never touched, so a buyer's edits and comments survive updates.
 */
final class DialogStore {

    private final Path folder;
    private final Function<String, @Nullable InputStream> resources;

    /**
     * @param folder    the folder of the dialog files
     * @param resources opens a resource of the plugin jar by path, or answers {@code null}; the caller closes it
     */
    DialogStore(final Path folder, final Function<String, @Nullable InputStream> resources) {
        this.folder = folder;
        this.resources = resources;
    }

    Path file(final String id) {
        return folder.resolve(id + ".yml");
    }

    static String resourcePath(final String id) {
        return "dialogs/" + id + ".yml";
    }

    /**
     * Reads the file, copying the bundled one first if it does not exist.
     *
     * @throws IllegalStateException if there is no file and no bundled one
     * @throws IOException           if the file cannot be read or the copy cannot be written
     */
    String read(final String id) throws IOException {
        final Path file = file(id);
        if (!Files.exists(file)) {
            final byte[] bundled = bundled(id);
            if (bundled == null) {
                throw new IllegalStateException("Dialog '" + id + "' has no file " + file + " and no bundled "
                        + resourcePath(id) + " in the plugin jar");
            }
            AtomicFiles.write(file, bundled);
            return new String(bundled, StandardCharsets.UTF_8);
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /**
     * Tells a file that fell behind: the plugin jar ships a higher {@code config-version} than {@code text}, the file
     * in the data folder. Nothing is overwritten, a buyer's edits stay.
     *
     * @param text the content of the file in the data folder
     * @return the warning, or {@code null} if the file is current, there is no bundled one or either cannot be read
     */
    @Nullable String outdated(final String id, final String text) {
        final byte[] bundled;
        try {
            bundled = bundled(id);
        } catch (final IOException e) {
            return null;
        }
        if (bundled == null) {
            return null;
        }
        final int shipped = version(new String(bundled, StandardCharsets.UTF_8));
        final int present = version(text);
        if (shipped <= present) {
            return null;
        }
        return "the plugin ships config-version " + shipped + ", this file is " + present
                + ": delete it to get the new default or update it by hand (old copy kept)";
    }

    /** @return the {@code config-version} of a file, 1 if it has none, 0 if it is not YAML (never newer) */
    private static int version(final String text) {
        try {
            return YamlTrees.parse(text).get("config-version") instanceof final Number number ? number.intValue() : 1;
        } catch (final InvalidConfigurationException e) {
            return 0;
        }
    }

    private byte @Nullable [] bundled(final String id) throws IOException {
        try (InputStream in = resources.apply(resourcePath(id))) {
            return in == null ? null : in.readAllBytes();
        }
    }
}
