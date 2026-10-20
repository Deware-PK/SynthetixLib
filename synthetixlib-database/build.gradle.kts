import org.gradle.process.CommandLineArgumentProvider

plugins {
    id("synthetix.library")
}

description = "SynthetixLib database: async SQL for SQLite and MySQL on a HikariCP pool, migrations, safe shutdown"

dependencies {
    api(project(":synthetixlib-config"))

    // Loaded at runtime by the consumer's PluginLoader (SynthetixLibraries.database), never shaded (ADR 0014).
    compileOnly(libs.hikaricp)

    testImplementation(libs.hikaricp)
    // Test-only: at runtime the drivers are the ones Paper ships (same versions, see the catalog).
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.mysql.connector)
}

// --- HikariCP version: the catalog is the only place; the loader helper reads it from a generated class ---------
val generatedVersions = layout.buildDirectory.dir("generated/sources/versions")
val hikariVersion = libs.versions.hikaricp.get()

val generateLibraryVersions = tasks.register("generateLibraryVersions") {
    val outDir = generatedVersions
    inputs.property("hikaricp", hikariVersion)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("com/github/dewarepk/synthetixlib/database/LibraryVersions.java").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.github.dewarepk.synthetixlib.database;

            /** Generated from gradle/libs.versions.toml by generateLibraryVersions; do not edit. */
            final class LibraryVersions {

                /** HikariCP version that {@link SynthetixLibraries#database} adds to the plugin class path. */
                static final String HIKARICP = "$hikariVersion";

                private LibraryVersions() {
                }
            }
            """.trimIndent() + "\n"
        )
    }
}
sourceSets.main {
    java.srcDir(files(generatedVersions).builtBy(generateLibraryVersions))
}

// --- MySQL tests: the owner's user gradle.properties (mysqlUrl, mysqlUser, mysqlPassword), never the repo ---------
val mysqlProperties = listOf("mysqlUrl", "mysqlUser", "mysqlPassword")
val mysqlConfigured = mysqlProperties.all { providers.gradleProperty(it).isPresent }

tasks.test {
    // A provider, not systemProperty(): the password must not become a task input or show up in reports.
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        mysqlProperties.mapNotNull { name ->
            providers.gradleProperty(name).orNull?.let { "-D$name=$it" }
        }
    })
    if (mysqlConfigured) {
        // Tests that talk to a live database are never up to date and never taken from the build cache.
        outputs.upToDateWhen { false }
        outputs.doNotCacheIf("MySQL tests use a live database") { true }
    }
}
