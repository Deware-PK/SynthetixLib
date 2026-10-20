package com.github.dewarepk.synthetixlib.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Finding and checking the migration files, without a database. */
class MigrationsLoadingTest {

    @TempDir
    Path dir;

    private int folders;

    private Migrations folder(final String... namesAndTexts) throws IOException {
        final Path root = dir.resolve("loose" + folders++);
        final Path folder = Files.createDirectories(root.resolve("db"));
        for (int i = 0; i < namesAndTexts.length; i += 2) {
            Files.writeString(folder.resolve(namesAndTexts[i]), namesAndTexts[i + 1]);
        }
        return Migrations.fromResources(new URLClassLoader(new URL[] {root.toUri().toURL()}, null), "db");
    }

    private static List<String> described(final Migrations migrations) {
        return migrations.steps().stream().map(step -> step.version() + ":" + step.name()).toList();
    }

    @Test
    void theClassOfTheCallerFindsItsOwnResources() {
        final Migrations migrations = Migrations.fromResources("db/sample");   // src/test/resources, README.txt is ignored

        assertEquals(List.of("1:first", "2:second"), described(migrations));
    }

    @Test
    void aJarIsListedWithoutScanningAndOnlyTheFolderItselfCounts() throws IOException {
        final Path jar = dir.resolve("plugin.jar");
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream zip = new JarOutputStream(out)) {
            for (final String entry : new String[] {"db/", "db/migrations/", "db/migrations/sub/", "db/other/"}) {
                zip.putNextEntry(new JarEntry(entry));
                zip.closeEntry();
            }
            for (final String entry : new String[] {"db/migrations/V2__b.sql", "db/migrations/V1__a.sql",
                    "db/migrations/V1__a.mysql.sql", "db/migrations/readme.txt", "db/migrations/sub/V9__x.sql",
                    "db/other/V5__y.sql"}) {
                zip.putNextEntry(new JarEntry(entry));
                zip.write("SELECT 1;\n".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }

        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            final Migrations migrations = Migrations.fromResources(loader, "/db/migrations/");
            assertEquals(List.of("1:a", "2:b"), described(migrations));
            assertNotNull(migrations.steps().get(0).mysql());
            assertNotNull(migrations.steps().get(0).base());
        }
    }

    @Test
    void aMissingFolderIsReported() {
        final IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> Migrations.fromResources("db/nothing-here"));
        assertTrue(error.getMessage().contains("db/nothing-here"), error.getMessage());
    }

    @Test
    void aVersionUsedTwiceFailsAtLoad() {
        final MigrationException error = assertThrows(MigrationException.class,
                () -> folder("V1__init.sql", "SELECT 1;", "V2__a.sql", "SELECT 1;", "V2__b.sql", "SELECT 1;"));
        assertTrue(error.getMessage().contains("Version 2 is used twice"), error.getMessage());
    }

    @Test
    void anOverrideMustHaveTheNameOfThePlainFile() {
        final MigrationException error = assertThrows(MigrationException.class,
                () -> folder("V1__init.sql", "SELECT 1;", "V1__other.mysql.sql", "SELECT 1;"));
        assertTrue(error.getMessage().contains("used twice"), error.getMessage());
    }

    @Test
    void codeCannotTakeAVersionAFileUses() throws IOException {
        final Migrations files = folder("V1__init.sql", "SELECT 1;");

        final MigrationException error = assertThrows(MigrationException.class,
                () -> files.withCode(1, "again", tx -> { }));
        assertTrue(error.getMessage().contains("Version 1 is used twice"), error.getMessage());
        assertThrows(MigrationException.class, () -> files.withCode(0, "zero", tx -> { }));
        assertThrows(MigrationException.class, () -> files.withCode(2, " ", tx -> { }));
        files.withCode(2, "two", tx -> { });
        assertEquals(1, files.steps().size(), "withCode returns a new Migrations and leaves this one alone");
    }

    @Test
    void aGapIsFoundByStepsAndFilledByCode() throws IOException {
        final Migrations files = folder("V1__a.sql", "SELECT 1;", "V3__c.sql", "SELECT 3;");

        final MigrationException gap = assertThrows(MigrationException.class, files::steps);
        assertTrue(gap.getMessage().contains("Migration 2 is missing"), gap.getMessage());
        assertEquals(List.of("1:a", "2:b", "3:c"), described(files.withCode(2, "b", tx -> { })));
        final Migrations late = folder("V2__b.sql", "SELECT 1;");
        assertThrows(MigrationException.class, late::steps, "versions must start at 1");
    }

    @Test
    void sqlFilesWithABadNameAreRejected() {
        for (final String bad : new String[] {"V1_init.sql", "v1__init.sql", "V0__zero.sql", "V1__.sql", "init.sql",
                "V99999999999__big.sql"}) {
            assertThrows(MigrationException.class, () -> folder(bad, "SELECT 1;"), bad);
        }
    }

    @Test
    void otherFilesAreIgnored() throws IOException {
        assertEquals(List.of("1:ok"), described(folder("V1__ok.sql", "SELECT 1;", "notes.md", "text", "V2__x.txt", "text")));
    }
}
