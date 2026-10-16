package com.github.dewarepk.synthetixlib.config.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFilesTest {

    @TempDir
    Path dir;

    private List<String> names() throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void replacesTheFileAndLeavesNoTempFile() throws IOException {
        final Path target = dir.resolve("a.yml");
        AtomicFiles.write(target, "one".getBytes(StandardCharsets.UTF_8));
        AtomicFiles.write(target, "two".getBytes(StandardCharsets.UTF_8));

        assertEquals("two", Files.readString(target));
        assertEquals(List.of("a.yml"), names());
    }

    @Test
    void createsMissingParentDirectories() throws IOException {
        final Path target = dir.resolve("x/y/a.yml");
        AtomicFiles.write(target, new byte[] {1, 2, 3});

        assertEquals(3, Files.size(target));
    }

    @Test
    void aFailedWriteKeepsTheOldFileAndLeavesNoTempFile() throws IOException {
        final Path target = dir.resolve("folder");
        Files.createDirectory(target);
        Files.writeString(target.resolve("inner.txt"), "x");

        assertThrows(IOException.class, () -> AtomicFiles.write(target, new byte[] {1}));

        assertEquals(List.of("folder"), names());
        assertEquals("x", Files.readString(target.resolve("inner.txt")));
    }
}
