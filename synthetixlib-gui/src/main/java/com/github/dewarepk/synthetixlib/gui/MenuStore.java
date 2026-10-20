package com.github.dewarepk.synthetixlib.gui;

import com.github.dewarepk.synthetixlib.config.yaml.AtomicFiles;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Where menu files live: {@code <data folder>/menus/<id>.yml}, created from {@code menus/<id>.yml} in the plugin
 * jar when missing. A file that exists is never touched, so a buyer's edits and comments survive updates.
 */
final class MenuStore {

    private final Path folder;
    private final Function<String, @Nullable InputStream> resources;

    /**
     * @param folder    the folder of the menu files
     * @param resources opens a resource of the plugin jar by path, or answers {@code null}; the caller closes it
     */
    MenuStore(final Path folder, final Function<String, @Nullable InputStream> resources) {
        this.folder = folder;
        this.resources = resources;
    }

    Path file(final String id) {
        return folder.resolve(id + ".yml");
    }

    static String resourcePath(final String id) {
        return "menus/" + id + ".yml";
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
                throw new IllegalStateException("Menu '" + id + "' has no file " + file + " and no bundled "
                        + resourcePath(id) + " in the plugin jar");
            }
            AtomicFiles.write(file, bundled);
            return new String(bundled, StandardCharsets.UTF_8);
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private byte @Nullable [] bundled(final String id) throws IOException {
        try (InputStream in = resources.apply(resourcePath(id))) {
            return in == null ? null : in.readAllBytes();
        }
    }
}
