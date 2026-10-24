import java.io.File

/**
 * Docs snippets (AGENTS.md "Docs snippets"): code regions in real sources are marked
 * `// @snippet:<name>` ... `// @end` (or `# ...` in YAML) and copied into fenced markdown blocks tagged
 * `java snippet=<name>` / `yaml snippet=<name>`.
 */
object Snippets {

    data class Snippet(val name: String, val text: String, val file: File, val line: Int)

    /** A marker problem or a stale/unknown block; [where] is `file:line`. */
    data class Problem(val where: String, val message: String) {
        override fun toString() = "$where: $message"
    }

    private val NAME = Regex("[a-z0-9-]+")
    private val START = Regex("""^\s*(?://|#)\s*@snippet:(\S*)\s*$""")
    private val END = Regex("""^\s*(?://|#)\s*@end\s*$""")
    private val FENCE_OPEN = Regex("""^```(java|yaml)\s+snippet=(\S+)\s*$""")
    private const val FENCE = "```"

    val sourceExtensions = setOf("java", "kt", "kts", "yml", "yaml", "properties")

    /** Reads the markers of one source file. */
    fun extract(file: File, lines: List<String>, into: MutableMap<String, Snippet>, problems: MutableList<Problem>) {
        var openName: String? = null
        var openLine = 0
        val body = ArrayList<String>()
        for ((i, line) in lines.withIndex()) {
            val where = "${file.path}:${i + 1}"
            val start = START.matchEntire(line)
            if (start != null) {
                val name = start.groupValues[1]
                if (openName != null) {
                    problems += Problem(where, "snippet '$name' starts inside unclosed snippet '$openName' (line $openLine); nesting is not allowed")
                } else if (!NAME.matches(name)) {
                    problems += Problem(where, "invalid snippet name '$name' (use [a-z0-9-]+)")
                } else {
                    openName = name
                    openLine = i + 1
                    body.clear()
                }
            } else if (END.matches(line)) {
                if (openName == null) {
                    problems += Problem(where, "@end without a matching @snippet")
                } else {
                    val previous = into[openName]
                    if (previous != null) {
                        problems += Problem("${file.path}:$openLine", "duplicate snippet '$openName' (also at ${previous.file.path}:${previous.line})")
                    } else {
                        into[openName] = Snippet(openName, deindent(body), file, openLine)
                    }
                    openName = null
                }
            } else if (openName != null) {
                body += line
            }
        }
        if (openName != null) problems += Problem("${file.path}:$openLine", "snippet '$openName' is never closed with @end")
    }

    /** Removes the indentation of the shallowest non-blank line; trims blank lines at both ends. */
    fun deindent(lines: List<String>): String {
        val trimmed = lines.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
        val indent = trimmed.filter { it.isNotBlank() }.minOfOrNull { l -> l.takeWhile { it == ' ' || it == '\t' }.length } ?: 0
        return trimmed.joinToString("\n") { if (it.isBlank()) "" else it.substring(indent) }
    }

    /**
     * Rewrites tagged blocks of one markdown file from [snippets]. Returns the new text (same as the old when
     * nothing changed); unknown names and unclosed fences go to [problems], a changed block to [stale].
     */
    fun rewrite(
        file: File,
        text: String,
        snippets: Map<String, Snippet>,
        problems: MutableList<Problem>,
        stale: MutableList<Problem>,
    ): String {
        val lines = text.split("\n")
        val out = ArrayList<String>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val open = FENCE_OPEN.matchEntire(line.trimEnd('\r'))
            if (open == null) {
                out += line
                i++
                continue
            }
            val where = "${file.path}:${i + 1}"
            var close = i + 1
            while (close < lines.size && lines[close].trimEnd('\r') != FENCE) close++
            if (close >= lines.size) {
                problems += Problem(where, "fenced block is never closed")
                out.addAll(lines.subList(i, lines.size))
                break
            }
            val name = open.groupValues[2]
            val snippet = snippets[name]
            out += line
            val current = lines.subList(i + 1, close)
            if (snippet == null) {
                problems += Problem(where, "unknown snippet '$name'")
                out.addAll(current)
            } else {
                val fresh = if (snippet.text.isEmpty()) emptyList() else snippet.text.split("\n")
                if (current.map { it.trimEnd('\r') } != fresh) stale += Problem(where, "snippet '$name' differs from ${snippet.file.path}:${snippet.line}")
                out.addAll(fresh)
            }
            out += lines[close]
            i = close + 1
        }
        return out.joinToString("\n")
    }

    /** Scans [sourceRoots], then every markdown file under [markdownRoots]; writes changes when [write]. */
    fun run(sourceRoots: List<File>, markdownRoots: List<File>, write: Boolean): List<Problem> {
        val problems = ArrayList<Problem>()
        val stale = ArrayList<Problem>()
        val snippets = HashMap<String, Snippet>()
        sourceRoots.filter { it.exists() }.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension in sourceExtensions }.sortedBy { it.path }.toList()
        }.forEach { extract(it, it.readLines(Charsets.UTF_8), snippets, problems) }

        markdownRoots.filter { it.exists() }.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "md" }.sortedBy { it.path }.toList()
        }.forEach { md ->
            val old = md.readText(Charsets.UTF_8)
            val updated = rewrite(md, old, snippets, problems, stale)
            if (write && updated != old) md.writeText(updated, Charsets.UTF_8)
        }
        return if (write) problems else problems + stale
    }
}
