// Runnable Paper plugin (example / test bed, later real products):
//  - shadowJar with SynthetixLib relocated            (docs/adr/0005)
//  - verifyRelocation: no un-relocated SynthetixLib classes in the jar
//  - runServer   -> Paper 1.21.11 on Java 21, run/1.21.11
//    runServer26 -> Paper 26.x    on Java 25, run/26.x
//  - smokeTest   -> boots both servers headless with -Dsynthetix.selftest=true, plugin writes
//                   selftest-result.json and shuts the server down, task fails unless status PASS

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import xyz.jpenilla.runpaper.task.RunServer
import java.time.Duration
import java.util.zip.ZipFile

plugins {
    id("synthetix.java-base")
    id("com.gradleup.shadow")
    id("xyz.jpenilla.run-paper")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun ver(alias: String) = libs.findVersion(alias).get().requiredVersion

val paperMin = ver("paper-run-min")
val paperLatest = ver("paper-run-latest")
val javaMin = ver("java-target").toInt()
val javaLatest = ver("java-latest-runtime").toInt()

val synthetixPlugin = extensions.create<SynthetixPluginExtension>("synthetixPlugin")
val toolchains = extensions.getByType<JavaToolchainService>()
val libRootPackage = "com.github.dewarepk.synthetixlib"

// --- Shading --------------------------------------------------------------------------------------
val shadowJar = tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
}
tasks.named<Jar>("jar") {
    archiveClassifier.set("plain")
}
tasks.named("assemble") {
    dependsOn(shadowJar)
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching(listOf("paper-plugin.yml", "plugin.yml")) {
        expand(props)
    }
}

afterEvaluate {
    val target = synthetixPlugin.libsPackage.orNull
        ?: throw GradleException("synthetixPlugin.libsPackage must be set in ${project.path}")
    if (target.startsWith(libRootPackage)) {
        throw GradleException("synthetixPlugin.libsPackage must not start with $libRootPackage")
    }
    shadowJar.configure {
        relocate(libRootPackage, "$target.synthetixlib")
    }
}

val verifyRelocation = tasks.register("verifyRelocation") {
    group = "verification"
    description = "Fails if the shaded jar contains un-relocated SynthetixLib classes."
    val jarFile = shadowJar.flatMap { it.archiveFile }
    inputs.file(jarFile)
    val forbiddenPrefix = libRootPackage.replace('.', '/') + "/"
    doLast {
        ZipFile(jarFile.get().asFile).use { zip ->
            val leaked = zip.entries().asSequence().map { it.name }.filter { it.startsWith(forbiddenPrefix) }.toList()
            if (leaked.isNotEmpty()) {
                throw GradleException("Un-relocated SynthetixLib classes in shaded jar:\n" + leaked.take(20).joinToString("\n"))
            }
        }
    }
}
tasks.named("check") {
    dependsOn(verifyRelocation)
}

// --- Dev servers ----------------------------------------------------------------------------------
runPaper {
    // We wire pluginJars explicitly so custom RunServer tasks get the shaded jar exactly once.
    disablePluginJarDetection()
}

fun RunServer.synthetixServer(mcVersion: String, javaVersion: Int, dirName: String, port: Int) {
    minecraftVersion(mcVersion)
    javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(javaVersion)) })
    val dir = layout.projectDirectory.dir("run/$dirName")
    runDirectory.set(dir)
    pluginJars(shadowJar.flatMap { it.archiveFile })
    systemProperty("com.mojang.eula.agree", "true")
    jvmArgs("-Xms1G", "-Xmx2G")
    val propsFile = dir.file("server.properties").asFile
    doFirst {
        // Only seeds a fresh server; edit run/<dir>/server.properties freely afterwards.
        if (!propsFile.exists()) {
            propsFile.parentFile.mkdirs()
            propsFile.writeText(
                """
                server-port=$port
                online-mode=false
                level-type=minecraft\:flat
                generate-structures=false
                spawn-protection=0
                view-distance=4
                simulation-distance=4
                motd=SynthetixLib dev $mcVersion
                """.trimIndent() + "\n"
            )
        }
    }
}

tasks.named<RunServer>("runServer") {
    description = "Runs Paper $paperMin (Java $javaMin) with this plugin."
    synthetixServer(paperMin, javaMin, paperMin, 25565)
}

tasks.register<RunServer>("runServer26") {
    group = "run paper"
    description = "Runs Paper $paperLatest (Java $javaLatest) with this plugin."
    synthetixServer(paperLatest, javaLatest, paperLatest, 25566)
}

// --- Smoke tests ----------------------------------------------------------------------------------
fun registerSmoke(name: String, mcVersion: String, javaVersion: Int, port: Int) =
    tasks.register<RunServer>(name) {
        group = "verification"
        description = "Boots Paper $mcVersion headless, runs plugin self-tests, fails unless all pass."
        synthetixServer(mcVersion, javaVersion, "smoke-$mcVersion", port)
        val result = layout.projectDirectory.file("run/smoke-$mcVersion/selftest-result.json").asFile
        systemProperty("synthetix.selftest", "true")
        systemProperty("synthetix.selftest.output", result.absolutePath)
        timeout.set(Duration.ofMinutes(6))
        isIgnoreExitValue = true
        outputs.upToDateWhen { false }
        doFirst { result.delete() }
        doLast {
            if (!result.exists()) {
                throw GradleException("[$mcVersion] no self-test result: plugin failed to enable or server crashed. Check the log above.")
            }
            val json = result.readText()
            if (!json.contains("\"status\":\"PASS\"")) {
                throw GradleException("[$mcVersion] self-test FAILED:\n$json")
            }
            logger.lifecycle("[$mcVersion] self-test PASS")
        }
    }

val smokeMin = registerSmoke("smokeTestMin", paperMin, javaMin, 25575)
val smokeLatest = registerSmoke("smokeTestLatest", paperLatest, javaLatest, 25576)
smokeLatest.configure { mustRunAfter(smokeMin) }

tasks.register("smokeTest") {
    group = "verification"
    description = "Smoke test on Paper $paperMin and $paperLatest."
    dependsOn(smokeMin, smokeLatest)
}
