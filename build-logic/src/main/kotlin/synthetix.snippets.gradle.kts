// Root-project tasks that keep fenced code in skills/ identical to marked regions in real sources.
// Marker syntax: AGENTS.md "Docs snippets". Logic and tests: Snippets.kt, src/test.

val sourceRoots = listOf(
    layout.projectDirectory.dir("synthetixlib-example/src").asFile,
    layout.projectDirectory.dir("skills/synthetixlib/templates").asFile,
)
val markdownRoot = layout.projectDirectory.dir("skills").asFile

fun fail(problems: List<Snippets.Problem>): Nothing =
    throw GradleException("Snippet problems:\n" + problems.joinToString("\n") { "  $it" })

tasks.register("syncSnippets") {
    group = "documentation"
    description = "Rewrites every tagged code block under skills/ from its @snippet source."
    doLast {
        val problems = Snippets.run(sourceRoots, listOf(markdownRoot), write = true)
        if (problems.isNotEmpty()) fail(problems)
    }
}

val checkSnippets = tasks.register("checkSnippets") {
    group = "verification"
    description = "Fails when a tagged block under skills/ differs from its source, is unknown, duplicated or unclosed."
    doLast {
        val problems = Snippets.run(sourceRoots, listOf(markdownRoot), write = false)
        if (problems.isNotEmpty()) fail(problems)
    }
}

plugins.apply("base")
tasks.named("check") {
    dependsOn(checkSnippets)
    dependsOn(gradle.includedBuild("build-logic").task(":test"))
}
