// Base for every Java module: Java 21 toolchain + bytecode, paper-api (compileOnly), JUnit 5,
// and a source scan for patterns ArchUnit cannot see (string literals).

plugins {
    java
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()
val javaTarget = libs.findVersion("java-target").get().requiredVersion.toInt()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaTarget))
    }
}

dependencies {
    "compileOnly"(lib("paper-api"))

    "testImplementation"(lib("paper-api"))
    "testImplementation"(platform(lib("junit-bom")))
    "testImplementation"(lib("junit-jupiter"))
    "testRuntimeOnly"(lib("junit-platform-launcher"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaTarget)
    options.compilerArgs.addAll(listOf("-Xlint:deprecation,removal,unchecked", "-parameters"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// --- Forbidden source patterns (legacy colour codes etc.) ---------------------------------------
val forbiddenPatterns = listOf(
    Regex("§") to "legacy section sign - use MiniMessage",
    Regex("""\\u00[aA]7""") to "escaped legacy section sign - use MiniMessage",
)
val mainJavaSources = fileTree("src/main/java") { include("**/*.java") }

val checkForbiddenPatterns = tasks.register("checkForbiddenPatterns") {
    group = "verification"
    description = "Fails if main sources contain forbidden patterns (see AGENTS.md)."
    inputs.files(mainJavaSources)
    val sources = mainJavaSources
    doLast {
        val hits = sources.files.sorted().flatMap { file ->
            file.readLines(Charsets.UTF_8).withIndex().flatMap { (i, line) ->
                forbiddenPatterns.filter { (re, _) -> re.containsMatchIn(line) }
                    .map { (_, why) -> "${file.path}:${i + 1}: $why" }
            }
        }
        if (hits.isNotEmpty()) {
            throw GradleException("Forbidden patterns found:\n" + hits.joinToString("\n"))
        }
    }
}

tasks.named("check") {
    dependsOn(checkForbiddenPatterns)
}
