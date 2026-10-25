import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SnippetsTest {

    private val src = File("A.java")
    private val md = File("doc.md")

    private fun extract(vararg lines: String): Pair<Map<String, Snippets.Snippet>, List<Snippets.Problem>> {
        val map = LinkedHashMap<String, Snippets.Snippet>()
        val problems = ArrayList<Snippets.Problem>()
        Snippets.extract(src, lines.toList(), map, problems)
        return map to problems
    }

    @Test
    fun deindentsToShallowestLine() {
        val (map, problems) = extract("class A {", "    // @snippet:one", "        int a;", "      if (x) {", "", "      }", "    // @end", "}")
        assertTrue(problems.isEmpty())
        assertEquals("  int a;\nif (x) {\n\n}", map.getValue("one").text)
        assertEquals(2, map.getValue("one").line)
    }

    @Test
    fun yamlMarkers() {
        val (map, problems) = extract("# @snippet:y-1", "a: 1", "# @end")
        assertTrue(problems.isEmpty())
        assertEquals("a: 1", map.getValue("y-1").text)
    }

    @Test
    fun nestedRefused() {
        val (_, problems) = extract("// @snippet:a", "// @snippet:b", "x", "// @end")
        assertTrue(problems.any { "nesting" in it.message && it.where.endsWith(":2") })
    }

    @Test
    fun unclosedReported() {
        val (_, problems) = extract("// @snippet:a", "x")
        assertTrue(problems.any { "never closed" in it.message && it.where.endsWith(":1") })
    }

    @Test
    fun strayEndAndBadName() {
        val (_, problems) = extract("// @end", "// @snippet:Bad_Name")
        assertEquals(2, problems.size)
    }

    @Test
    fun duplicateReported() {
        val (map, problems) = extract("// @snippet:a", "x", "// @end", "// @snippet:a", "y", "// @end")
        assertEquals("x", map.getValue("a").text)
        assertTrue(problems.any { "duplicate" in it.message && it.where.endsWith(":4") })
    }

    private fun snippets() = mapOf("a" to Snippets.Snippet("a", "int a;\nint b;", src, 3))

    @Test
    fun rewriteFixesStaleBlockAndReportsIt() {
        val problems = ArrayList<Snippets.Problem>()
        val stale = ArrayList<Snippets.Problem>()
        val text = "intro\n```java snippet=a\nold\n```\noutro"
        val out = Snippets.rewrite(md, text, snippets(), problems, stale)
        assertEquals("intro\n```java snippet=a\nint a;\nint b;\n```\noutro", out)
        assertEquals(1, stale.size)
        assertTrue(stale[0].where.endsWith("doc.md:2"))
        assertTrue(problems.isEmpty())
    }

    @Test
    fun rewriteIsIdempotentAndQuietWhenInSync() {
        val problems = ArrayList<Snippets.Problem>()
        val stale = ArrayList<Snippets.Problem>()
        val text = "```java snippet=a\nint a;\nint b;\n```\n"
        assertEquals(text, Snippets.rewrite(md, text, snippets(), problems, stale))
        assertTrue(stale.isEmpty() && problems.isEmpty())
    }

    @Test
    fun unknownNameAndUnclosedFence() {
        val problems = ArrayList<Snippets.Problem>()
        val stale = ArrayList<Snippets.Problem>()
        val text = "```java snippet=nope\nkeep\n```\n```yaml snippet=a\nx"
        val out = Snippets.rewrite(md, text, snippets(), problems, stale)
        assertEquals(text, out)
        assertTrue(problems.any { "unknown snippet 'nope'" in it.message && it.where.endsWith(":1") })
        assertTrue(problems.any { "never closed" in it.message && it.where.endsWith(":4") })
    }

    @Test
    fun kotlinFenceIsRewritten() {
        val problems = ArrayList<Snippets.Problem>()
        val stale = ArrayList<Snippets.Problem>()
        val out = Snippets.rewrite(md, "```kotlin snippet=a\nold\n```", snippets(), problems, stale)
        assertEquals("```kotlin snippet=a\nint a;\nint b;\n```", out)
        assertEquals(1, stale.size)
        assertTrue(problems.isEmpty())
    }

    @Test
    fun buildOutputIsNotScanned() {
        val root = kotlin.io.path.createTempDirectory("snippets").toFile()
        try {
            File(root, "src").mkdirs()
            File(root, "build/resources").mkdirs()
            File(root, "src/a.yml").writeText("# @snippet:a\nx: 1\n# @end\n")
            File(root, "build/resources/a.yml").writeText("# @snippet:a\nx: 1\n# @end\n")
            assertTrue(Snippets.run(listOf(root), emptyList(), write = false).isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun sqlMarkersAndFence() {
        val (map, problems) = extract("-- @snippet:m-1", "CREATE TABLE t (id INTEGER);", "-- @end")
        assertTrue(problems.isEmpty())
        assertEquals("CREATE TABLE t (id INTEGER);", map.getValue("m-1").text)
        val text = "```sql snippet=m-1\nold\n```"
        val stale = ArrayList<Snippets.Problem>()
        val out = Snippets.rewrite(md, text, map, ArrayList(), stale)
        assertEquals("```sql snippet=m-1\nCREATE TABLE t (id INTEGER);\n```", out)
        assertEquals(1, stale.size)
    }

    @Test
    fun untaggedFencesUntouched() {
        val problems = ArrayList<Snippets.Problem>()
        val text = "```java\nplain\n```"
        assertEquals(text, Snippets.rewrite(md, text, snippets(), problems, ArrayList()))
    }
}
