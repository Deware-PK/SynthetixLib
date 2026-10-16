package com.github.dewarepk.synthetixlib.config.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.config.yaml.YamlTrees;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigTreeTest {

    @TempDir
    Path dir;

    private YamlConfiguration yaml;
    private ConfigTree tree;

    private void load(final String text) throws InvalidConfigurationException {
        yaml = YamlTrees.parse(text);
        tree = ConfigTree.of(yaml);
    }

    private String text() {
        return yaml.saveToString();
    }

    @Test
    void getReadsNestedValuesAndReturnsSectionsAsCopies() throws InvalidConfigurationException {
        load("a:\n  b:\n    c: 5\nlist: [1, 2]\n");

        assertEquals(5, tree.get("a.b.c"));
        assertEquals(Map.of("c", 5), tree.get("a.b"));
        assertEquals(List.of(1, 2), tree.get("list"));
        assertNull(tree.get("a.x.c"));
        assertNull(tree.get("a.b.c.d"));
        assertTrue(tree.has("a.b"));
        assertFalse(tree.has("nope"));
        @SuppressWarnings("unchecked")
        final Map<String, Object> copy = (Map<String, Object>) tree.get("a");
        copy.put("changed", true);
        assertNull(tree.get("a.changed"));
    }

    @Test
    void setCreatesSectionsAndReplacesValuesKeepingComments() throws InvalidConfigurationException {
        load("# about a\na: 1\n");

        tree.set("a", 2).set("x.y.z", "deep").set("m", Map.of("k", 1));

        assertEquals(2, tree.get("a"));
        assertEquals("deep", tree.get("x.y.z"));
        assertEquals(1, tree.get("m.k"));
        assertTrue(text().contains("# about a\na: 2"), text());
    }

    @Test
    void setNullRemoves() throws InvalidConfigurationException {
        load("a: 1\nb: 2\n");

        tree.set("a", null);

        assertFalse(tree.has("a"));
        assertTrue(tree.has("b"));
    }

    @Test
    void removeIgnoresMissingPaths() throws InvalidConfigurationException {
        load("a:\n  b: 1\n");

        tree.remove("a.b").remove("x.y");

        assertFalse(tree.has("a.b"));
    }

    @Test
    void renameKeepsTheSectionAndTheComment() throws InvalidConfigurationException {
        load("zones:\n  # the spawn\n  old: 1\n");

        tree.rename("zones.old", "spawn");

        assertEquals(1, tree.get("zones.spawn"));
        assertFalse(tree.has("zones.old"));
        assertTrue(text().contains("# the spawn\n  spawn: 1"), text());
    }

    @Test
    void renameRejectsAPath() throws InvalidConfigurationException {
        load("a: 1\n");
        assertThrows(IllegalArgumentException.class, () -> tree.rename("a", "b.c"));
        assertThrows(IllegalArgumentException.class, () -> tree.rename("a", ""));
    }

    @Test
    void moveCarriesWholeSectionsAndOverwritesTheTarget() throws InvalidConfigurationException {
        load("# note\nsnipe:\n  enabled: true\n  secs: 3\nanti:\n  old: 1\n");

        tree.move("snipe", "anti");

        assertEquals(Map.of("enabled", true, "secs", 3), tree.get("anti"));
        assertFalse(tree.has("snipe"));
        assertTrue(text().contains("# note\nanti:"), text());
    }

    @Test
    void moveOfAMissingPathDoesNothing() throws InvalidConfigurationException {
        load("a: 1\n");

        tree.move("b", "c");

        assertEquals("a: 1\n", text());
    }

    @Test
    void transformChangesTheValueOnlyIfThereIsOne() throws InvalidConfigurationException {
        load("secs: 90\n");

        tree.transform("secs", old -> old + "s").transform("absent", old -> {
            throw new AssertionError("must not be called");
        });

        assertEquals("90s", tree.get("secs"));
        assertFalse(tree.has("absent"));
    }

    @Test
    void transformToNullRemoves() throws InvalidConfigurationException {
        load("a: 1\n");
        tree.transform("a", old -> null);
        assertFalse(tree.has("a"));
    }

    @Test
    void importFromMergesSectionsAndTheFileWins() throws IOException, InvalidConfigurationException {
        final Path other = dir.resolve("old.yml");
        Files.writeString(other, "a:\n  x: 9\n  z: 3\nb: new\n");
        load("a:\n  x: 1\n  y: 2\nb: old\nkeep: true\n");

        assertTrue(tree.importFrom(other));

        assertEquals(Map.of("x", 9, "y", 2, "z", 3), tree.get("a"));
        assertEquals("new", tree.get("b"));
        assertEquals(true, tree.get("keep"));
    }

    @Test
    void importFromReportsAMissingFileAndRejectsBrokenOnes() throws IOException, InvalidConfigurationException {
        load("a: 1\n");
        assertFalse(tree.importFrom(dir.resolve("none.yml")));

        final Path broken = dir.resolve("broken.yml");
        Files.writeString(broken, "a: [unclosed\n");
        assertThrows(IllegalStateException.class, () -> tree.importFrom(broken));
    }

    @Test
    void emptyPathSegmentsAreRejected() throws InvalidConfigurationException {
        load("a: 1\n");
        assertThrows(IllegalArgumentException.class, () -> tree.get("a..b"));
        assertThrows(IllegalArgumentException.class, () -> tree.set("", 1));
        assertThrows(IllegalArgumentException.class, () -> tree.remove(".a"));
    }
}
