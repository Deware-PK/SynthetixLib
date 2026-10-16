package com.github.dewarepk.synthetixlib.config.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.config.codec.Problem;
import com.github.dewarepk.synthetixlib.config.migration.Migration;
import com.github.dewarepk.synthetixlib.config.yaml.FileFixtures.AntiSnipe;
import com.github.dewarepk.synthetixlib.config.yaml.FileFixtures.AuctionConfig;
import com.github.dewarepk.synthetixlib.config.yaml.FileFixtures.ListConfig;
import com.github.dewarepk.synthetixlib.config.yaml.FileFixtures.Node;
import com.github.dewarepk.synthetixlib.config.yaml.FileFixtures.Zone;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileConfigTest {

    @TempDir
    Path dir;

    private final Codecs codecs = Codecs.create();

    private Path file() {
        return dir.resolve("config.yml");
    }

    private FileConfig<AuctionConfig> config(final int version, final Migration... migrations) {
        return config(AuctionConfig::defaults, version, migrations);
    }

    private FileConfig<AuctionConfig> config(final Supplier<AuctionConfig> defaults, final int version,
                                             final Migration... migrations) {
        return new FileConfig<>(file(), AuctionConfig.class, defaults, codecs, version, List.of(migrations));
    }

    private String read() throws IOException {
        return Files.readString(file(), StandardCharsets.UTF_8);
    }

    private void write(final String text) throws IOException {
        Files.writeString(file(), text, StandardCharsets.UTF_8);
    }

    private static List<String> texts(final List<Problem> problems) {
        return problems.stream().map(Problem::toString).toList();
    }

    /** Writes the default file and returns its text, for tests that edit it. */
    private String fresh() throws IOException {
        config(1).load();
        return read();
    }

    // --- first start -------------------------------------------------------------------------------------------

    @Test
    void aMissingFileIsWrittenWithHeaderVersionAndComments() throws IOException {
        final FileConfig.Result<AuctionConfig> result = config(1).load();

        assertEquals(AuctionConfig.defaults(), result.value());
        assertEquals(List.of(), result.errors());
        assertEquals(List.of(), result.warnings());
        final String text = read();
        assertTrue(text.startsWith("# Test config\n# second header line\n"), text);
        assertTrue(text.indexOf("config-version: 1") < text.indexOf("min-bid: 100"), text);
        assertTrue(text.contains("# Minimum starting bid\nmin-bid: 100\n"), text);
        assertTrue(text.contains("# How long bidding lasts\n# e.g. 30s, 2m\nbid-time: 1m\n"), text);
        assertTrue(text.contains("anti-snipe:\n  # Extend the auction on late bids\n  enabled: true\n"), text);
        assertTrue(text.contains("zones:\n  spawn:\n    world: world\n    radius: 8\n"), text);
    }

    @Test
    void parentDirectoriesAreCreatedAndNoTempFileIsLeftBehind() throws IOException {
        final Path nested = dir.resolve("sub/dir/config.yml");
        new FileConfig<>(nested, AuctionConfig.class, AuctionConfig::defaults, codecs, 1, List.of()).load();

        assertTrue(Files.isRegularFile(nested));
        try (Stream<Path> files = Files.list(nested.getParent())) {
            assertEquals(List.of("config.yml"), files.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void theWrittenDefaultsLoadBackToTheSameValueWithoutProblems() throws IOException {
        fresh();
        final FileConfig.Result<AuctionConfig> again = config(1).load();
        assertEquals(AuctionConfig.defaults(), again.value());
        assertEquals(List.of(), again.errors());
        assertEquals(List.of(), again.warnings());
    }

    // --- missing keys ------------------------------------------------------------------------------------------

    @Test
    void aMissingKeyIsAddedWithItsCommentAndTheRestOfTheFileIsKept() throws IOException {
        write("config-version: 1\nmin-bid: 250\nbid-time: 5m\nblacklist:\n- DIRT\nzones:\n  a:\n    world: w\n"
                + "    radius: 3\nanti-snipe:\n  extend-seconds: 20\n");

        final FileConfig.Result<AuctionConfig> result = config(1).load();

        assertEquals(new AuctionConfig(250, Duration.ofMinutes(5), new AntiSnipe(true, 20), List.of("DIRT"),
                Map.of("a", new Zone("w", 3))), result.value());
        assertEquals(List.of(), result.errors());
        final String text = read();
        assertTrue(text.contains("anti-snipe:\n  extend-seconds: 20\n  # Extend the auction on late bids\n  enabled: true\n"),
                text);
        assertTrue(text.contains("min-bid: 250"), text);
        assertFalse(text.contains("spawn"), "map entries the user removed are not added back: " + text);
    }

    @Test
    void aWholeMissingSectionIsAddedWithTheCommentsOfItsKeys() throws IOException {
        write("config-version: 1\nmin-bid: 100\nbid-time: 1m\nblacklist: []\nzones: {}\n");

        config(1).load();

        final String text = read();
        assertTrue(text.contains("anti-snipe:\n  # Extend the auction on late bids\n  enabled: true\n  extend-seconds: 10\n"),
                text);
    }

    @Test
    void aFileWithoutAVersionGetsItAsTheFirstKey() throws IOException {
        write("# my own comment\nmin-bid: 100\nbid-time: 1m\nanti-snipe:\n  enabled: true\n  extend-seconds: 10\n"
                + "blacklist:\n- BEDROCK\nzones: {}\n");

        final FileConfig.Result<AuctionConfig> result = config(1).load();

        assertEquals(List.of(), result.errors());
        final String text = read();
        assertTrue(text.startsWith("config-version: 1\n"), text);
        assertTrue(text.contains("# my own comment\nmin-bid: 100"), text);
    }

    // --- invalid and unknown -----------------------------------------------------------------------------------

    @Test
    void anInvalidValueUsesTheDefaultIsReportedAndStaysInTheFile() throws IOException {
        final String edited = fresh().replace("min-bid: 100", "min-bid: 0").replace("radius: 8", "radius: -1")
                .replace("bid-time: 1m", "bid-time: soon");
        write(edited);

        final FileConfig.Result<AuctionConfig> result = config(1).load();

        assertEquals(AuctionConfig.defaults(), result.value());
        final List<String> errors = texts(result.errors());
        assertEquals(3, errors.size(), errors.toString());
        assertEquals("min-bid: must be >= 1 (was 0) -> using 100", errors.get(0));
        assertTrue(errors.get(1).startsWith("bid-time: invalid duration 'soon'") && errors.get(1).endsWith("-> using 1m"),
                errors.get(1));
        assertEquals("zones.spawn.radius: must be >= 1 (was -1) -> using 8", errors.get(2));
        assertEquals(edited, read(), "the file must not be rewritten: SnapPlug bug #6");
    }

    @Test
    void oneInvalidFieldDoesNotCostTheOthers() throws IOException {
        write(fresh().replace("min-bid: 100", "min-bid: 0").replace("extend-seconds: 10", "extend-seconds: 99")
                .replace("- BEDROCK", "- DIRT"));

        final AuctionConfig value = config(1).load().value();

        assertEquals(new AuctionConfig(100, Duration.ofSeconds(60), new AntiSnipe(true, 99), List.of("DIRT"),
                Map.of("spawn", new Zone("world", 8))), value);
    }

    @Test
    void aSectionWhereAValueIsExpectedFallsBackForThatFieldOnly() throws IOException {
        write(fresh().replace("min-bid: 100", "min-bid:\n  nested: 1"));

        final FileConfig.Result<AuctionConfig> result = config(1).load();

        assertEquals(AuctionConfig.defaults(), result.value());
        assertEquals(List.of("min-bid: expected an integer, got a section -> using 100"), texts(result.errors()));
    }

    @Test
    void aValueWhereASectionIsExpectedFallsBackToTheWholeSection() throws IOException {
        final String edited = fresh().replace("anti-snipe:\n  # Extend the auction on late bids\n  enabled: true\n"
                + "  extend-seconds: 10\n", "anti-snipe: nope\n");
        write(edited);

        final FileConfig.Result<AuctionConfig> result = config(1).load();

        assertEquals(AuctionConfig.defaults(), result.value());
        assertEquals(1, result.errors().size(), texts(result.errors()).toString());
        assertTrue(result.errors().get(0).toString().startsWith("anti-snipe: expected a section"), result.errors().toString());
        assertEquals(edited, read());
    }

    @Test
    void anUnknownKeyIsKeptAndReported() throws IOException {
        final String edited = fresh() + "extra: 1\n";
        write(edited);

        final FileConfig.Result<AuctionConfig> result = config(1).load();

        assertEquals(List.of("extra: unknown key"), texts(result.warnings()));
        assertEquals(List.of(), result.errors());
        assertEquals(edited, read());
    }

    @Test
    void aMapEntryWithoutADefaultThatIsInvalidIsLeftOutButStaysInTheFile() throws IOException {
        final String edited = fresh().replace("zones:\n", "zones:\n  lobby:\n    world: w\n    radius: 0\n");
        write(edited);

        final FileConfig.Result<AuctionConfig> result = config(1).load();

        assertEquals(Map.of("spawn", new Zone("world", 8)), result.value().zones());
        assertEquals(List.of("zones.lobby.radius: must be >= 1 (was 0) -> entry ignored"), texts(result.errors()));
        assertEquals(edited, read());
    }

    @Test
    void aMapEntryWithADefaultFallsBackToItsDefaultField() throws IOException {
        write(fresh().replace("radius: 8", "radius: 0"));

        final FileConfig.Result<AuctionConfig> result = config(1).load();

        assertEquals(Map.of("spawn", new Zone("world", 8)), result.value().zones());
        assertEquals(List.of("zones.spawn.radius: must be >= 1 (was 0) -> using 8"), texts(result.errors()));
    }

    @Test
    void mapKeysWithDotsStayOneKey() throws IOException {
        final Supplier<AuctionConfig> dotted = () -> new AuctionConfig(100, Duration.ofSeconds(60),
                new AntiSnipe(true, 10), List.of(), Map.of("vip.gold", new Zone("world", 8)));
        config(dotted, 1).load();

        assertTrue(read().contains("  vip.gold:\n"), read());
        final FileConfig.Result<AuctionConfig> again = config(dotted, 1).load();
        assertEquals(dotted.get(), again.value());
        assertEquals(List.of(), again.errors());
        assertEquals(List.of(), again.warnings());
    }

    // --- no re-save, comments ----------------------------------------------------------------------------------

    @Test
    void anUnchangedReloadDoesNotTouchTheFile() throws IOException {
        fresh();
        final FileTime old = FileTime.fromMillis(946_684_800_000L);
        Files.setLastModifiedTime(file(), old);
        final String before = read();

        for (int i = 0; i < 3; i++) {
            config(1).load();
        }

        assertEquals(old, Files.getLastModifiedTime(file()));
        assertEquals(before, read());
    }

    @Test
    void userCommentsSurviveAWriteThatAddsKeys() throws IOException {
        write("# top note\nconfig-version: 1\n# my note on bids\nmin-bid: 100 # inline note\nbid-time: 1m\n"
                + "# note on the list\nblacklist:\n- BEDROCK\nzones: {}\n");

        config(1).load();

        final String text = read();
        assertTrue(text.contains("# top note"), text);
        assertTrue(text.contains("# my note on bids\nmin-bid: 100 # inline note"), text);
        assertTrue(text.contains("# note on the list\nblacklist:"), text);
        assertTrue(text.contains("enabled: true"), "the write happened: " + text);
    }

    // --- migrations --------------------------------------------------------------------------------------------

    @Test
    void aMigrationRenamesAKeyAndWritesABackup() throws IOException {
        final String v1 = "config-version: 1\nmin-price: 50\n# kept\nbid-time: 2m\nanti-snipe:\n  enabled: false\n"
                + "  extend-seconds: 5\nblacklist: []\nzones: {}\n";
        write(v1);

        final FileConfig.Result<AuctionConfig> result = config(2,
                new Migration(1, 2, tree -> tree.rename("min-price", "min-bid"))).load();

        assertEquals(List.of(), result.errors());
        assertEquals(new AuctionConfig(50, Duration.ofMinutes(2), new AntiSnipe(false, 5), List.of(), Map.of()),
                result.value());
        assertEquals(v1, Files.readString(dir.resolve("config.yml.v1.bak")));
        final String text = read();
        assertTrue(text.startsWith("config-version: 2\n"), text);
        assertTrue(text.contains("min-bid: 50"), text);
        assertFalse(text.contains("min-price"), text);
        assertTrue(text.contains("# kept\nbid-time: 2m"), text);
    }

    @Test
    void migrationsChainFromTheVersionOfTheFile() throws IOException {
        write("config-version: 1\nmin-price: 50\nbid-time: 90\nanti-snipe:\n  enabled: false\n"
                + "  extend-seconds: 5\nblacklist: []\nzones: {}\n");
        final Migration first = new Migration(1, 2, tree -> tree.rename("min-price", "min-bid"));
        final Migration second = new Migration(2, 3, tree -> tree.transform("bid-time", old -> old + "s"));

        final FileConfig.Result<AuctionConfig> result = config(3, second, first).load();

        assertEquals(List.of(), result.errors());
        assertEquals(50, result.value().minBid());
        assertEquals(Duration.ofSeconds(90), result.value().bidTime());
        assertTrue(read().startsWith("config-version: 3\n"), read());
        assertTrue(Files.exists(dir.resolve("config.yml.v1.bak")));
        assertFalse(Files.exists(dir.resolve("config.yml.v2.bak")));
    }

    @Test
    void aFileAlreadyPartwayOnlyRunsTheRemainingSteps() throws IOException {
        write(fresh().replace("config-version: 1", "config-version: 2"));
        final int[] ran = new int[2];
        final Migration first = new Migration(1, 2, tree -> ran[0]++);
        final Migration second = new Migration(2, 3, tree -> ran[1]++);

        config(3, first, second).load();

        assertEquals(0, ran[0]);
        assertEquals(1, ran[1]);
        assertTrue(Files.exists(dir.resolve("config.yml.v2.bak")));
    }

    @Test
    void aVersionBumpWithoutAStepJustUpdatesTheVersion() throws IOException {
        write(fresh());

        config(2).load();

        assertTrue(read().startsWith("# Test config\n# second header line\n\nconfig-version: 2\n"), read());
        assertTrue(Files.exists(dir.resolve("config.yml.v1.bak")));
    }

    @Test
    void aFailingMigrationLeavesTheFileAndStillLoadsFromTheOriginal() throws IOException {
        final String v1 = fresh();
        write(v1);

        final FileConfig.Result<AuctionConfig> result = config(2, new Migration(1, 2, tree -> {
            tree.set("min-bid", 999);
            throw new IllegalStateException("boom");
        })).load();

        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).message().contains("boom"), result.errors().toString());
        assertEquals(100, result.value().minBid(), "half-applied changes are discarded");
        assertEquals(v1, read());
    }

    @Test
    void importFromReadsAnotherFileDuringAMigration() throws IOException {
        Files.writeString(dir.resolve("old.yml"), "min-bid: 77\nzones:\n  spawn:\n    radius: 4\n");
        write(fresh());

        final FileConfig.Result<AuctionConfig> result = config(2,
                new Migration(1, 2, tree -> assertTrue(tree.importFrom(dir.resolve("old.yml"))))).load();

        assertEquals(77, result.value().minBid());
        assertEquals(new Zone("world", 4), result.value().zones().get("spawn"));
    }

    @Test
    void aFileFromANewerVersionIsNeverChanged() throws IOException {
        final String newer = "config-version: 9\nmin-bid: 5\n";
        write(newer);

        final FileConfig.Result<AuctionConfig> result = config(2).load();

        assertEquals(5, result.value().minBid());
        assertEquals(List.of("config-version: 9 is newer than the 2 this plugin knows; the file will not be changed"),
                texts(result.warnings()));
        assertEquals(newer, read());
    }

    @Test
    void invalidMigrationSetupsAreRejected() {
        final Migration far = new Migration(1, 5, tree -> { });
        assertThrows(IllegalArgumentException.class, () -> config(2, far));
        final Migration a = new Migration(1, 2, tree -> { });
        final Migration b = new Migration(1, 3, tree -> { });
        assertThrows(IllegalArgumentException.class, () -> config(3, a, b));
        assertThrows(IllegalArgumentException.class, () -> config(0));
        assertThrows(IllegalArgumentException.class, () -> new Migration(2, 2, tree -> { }));
    }

    // --- unusable files ----------------------------------------------------------------------------------------

    @Test
    void invalidYamlYieldsNoValueAndLeavesTheFileAlone() throws IOException {
        write("min-bid: [unclosed\n  : :\n");

        final FileConfig.Result<AuctionConfig> result = config(1).load();

        assertNull(result.value());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).message().startsWith("invalid YAML"), result.errors().toString());
        assertEquals("min-bid: [unclosed\n  : :\n", read());
    }

    @Test
    void anEmptyFileGetsEverythingFromTheDefaults() throws IOException {
        write("");

        final FileConfig.Result<AuctionConfig> result = config(1).load();

        assertEquals(AuctionConfig.defaults(), result.value());
        assertTrue(read().startsWith("config-version: 1\n"), read());
        assertEquals(AuctionConfig.defaults(), config(1).load().value());
    }

    // --- lists -------------------------------------------------------------------------------------------------

    private FileConfig.Result<ListConfig> loadLists(final String text) throws IOException {
        write(text);
        return new FileConfig<>(file(), ListConfig.class, ListConfig::defaults, codecs, 1, List.of()).load();
    }

    @Test
    void anInvalidListElementIsDroppedAndTheOthersAreKept() throws IOException {
        final String text = "config-version: 1\nnumbers: [1, two, 3, [4]]\ntags: [5]\nrequired: [9]\nzone-list: []\n";

        final FileConfig.Result<ListConfig> result = loadLists(text);

        assertEquals(List.of(1, 3), result.value().numbers());
        assertEquals(List.of("numbers[1]: expected an integer, got string \"two\" -> element ignored",
                "numbers[3]: expected an integer, got a list -> element ignored"), texts(result.errors()));
        assertEquals(text, read(), "the text of the user stays");
    }

    @Test
    void anInvalidSetElementIsDroppedToo() throws IOException {
        final FileConfig.Result<ListConfig> result = loadLists(
                "config-version: 1\nnumbers: []\ntags: [1, x, 1, 2]\nrequired: [9]\nzone-list: []\n");

        assertEquals(new LinkedHashSet<>(List.of(1, 2)), result.value().tags());
        assertEquals(1, result.errors().size(), texts(result.errors()).toString());
        assertTrue(texts(result.errors()).get(0).startsWith("tags[1]: "), texts(result.errors()).toString());
    }

    @Test
    void aNotEmptyListWithoutAnyValidElementFallsBackToTheDefault() throws IOException {
        final FileConfig.Result<ListConfig> result = loadLists(
                "config-version: 1\nnumbers: [1]\ntags: [5]\nrequired: [a, b]\nzone-list: []\n");

        assertEquals(List.of(9), result.value().required());
        assertEquals(3, result.errors().size(), texts(result.errors()).toString());
        assertEquals("required: must not be empty -> using [9]", texts(result.errors()).get(2));
    }

    @Test
    void aListOfRecordsDropsTheInvalidRecord() throws IOException {
        final FileConfig.Result<ListConfig> result = loadLists("config-version: 1\nnumbers: []\ntags: [5]\n"
                + "required: [9]\nzone-list:\n- {world: a, radius: 2}\n- {world: b, radius: 0}\n- {world: c, radius: 3}\n");

        assertEquals(List.of(new Zone("a", 2), new Zone("c", 3)), result.value().zoneList());
        assertEquals(List.of("zone-list[1].radius: must be >= 1 (was 0) -> element ignored"), texts(result.errors()));
    }

    @Test
    void aValueThatIsNotAListFallsBackToTheDefault() throws IOException {
        final FileConfig.Result<ListConfig> result = loadLists(
                "config-version: 1\nnumbers: nope\ntags: [5]\nrequired: [9]\nzone-list: []\n");

        assertEquals(List.of(1, 2, 3), result.value().numbers());
        assertEquals(List.of("numbers: expected a list, got string \"nope\" -> using default"), texts(result.errors()));
    }

    // --- self-referencing records ------------------------------------------------------------------------------

    @Test
    void aRecordThatContainsItselfLoadsAndGetsItsCommentsOnce() throws IOException {
        final FileConfig<Node> config = new FileConfig<>(file(), Node.class, () -> new Node("root", null), codecs, 1,
                List.of());

        assertEquals(new Node("root", null), config.load().value());
        assertTrue(read().startsWith("# A node\n"), read());
        assertTrue(read().contains("# Name of the node\nname: root"), read());
        write("config-version: 1\nname: a\nnext:\n  name: b\n");
        assertEquals(new Node("a", new Node("b", null)), config.load().value());
    }

    @Test
    void aTypeThatCannotBeMappedIsAProgrammingError() {
        assertThrows(IllegalArgumentException.class,
                () -> new FileConfig<>(file(), String.class, () -> "x", codecs, 1, List.of()).load());
    }
}
