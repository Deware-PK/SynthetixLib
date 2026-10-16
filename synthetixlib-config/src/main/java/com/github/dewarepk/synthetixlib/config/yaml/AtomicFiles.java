package com.github.dewarepk.synthetixlib.config.yaml;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes a file so that a reader (or a crash) never sees half of it: the bytes go to a temp file in the same
 * directory, which then replaces the target in one move.
 *
 * @apiNote Internal
 */
@org.jetbrains.annotations.ApiStatus.Internal
public final class AtomicFiles {

    private AtomicFiles() {
    }

    /**
     * Replaces {@code target} with {@code content}, creating missing parent directories. No temp file is left
     * behind, also when the write fails.
     *
     * @throws IOException if the file cannot be written; the previous content of {@code target} is then intact
     */
    public static void write(final Path target, final byte[] content) throws IOException {
        final Path directory = target.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        final Path temp = Files.createTempFile(directory, "." + target.getFileName() + ".", ".tmp");
        try {
            Files.write(temp, content);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (final AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
