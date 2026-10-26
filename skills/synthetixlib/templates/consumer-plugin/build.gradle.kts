import java.time.Duration
import java.util.zip.ZipFile
import xyz.jpenilla.runpaper.task.RunServer

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "com.example.myplugin"
version = "1.0.0"

// -PsynthetixLocal=true uses a SynthetixLib built on this machine (publishToMavenLocal) instead of JitPack.
val synthetixLocal = providers.gradleProperty("synthetixLocal").map(String::toBoolean).getOrElse(false)

// @snippet:template-repositories
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    if (synthetixLocal) {
        mavenLocal()
    } else {
        maven("https://jitpack.io")
    }
}
// @end

// @snippet:template-dependencies
val synthetixGroup = if (synthetixLocal) "com.github.dewarepk" else "com.github.Deware-PK.SynthetixLib"
val synthetixVersion = providers.gradleProperty(if (synthetixLocal) "synthetixLocalVersion" else "synthetixVersion").get()

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // Shaded into your jar and relocated (see shadowJar below). Add only the modules you use.
    for (module in listOf("core", "config", "command", "gui", "dialog", "selftest" /*, "database" */)) {
        implementation("$synthetixGroup:synthetixlib-$module:$synthetixVersion")
    }
    // Database module: add "database" to the list above, enable the line in MyPluginLoader and add
    // compileOnly("com.zaxxer:HikariCP:7.1.0")   // loaded at runtime by the PluginLoader, never shaded
}
// @end

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.add("-parameters")
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
}

// @snippet:template-shadow
// Every consumer shades and relocates SynthetixLib (one jar for buyers). The target package must be yours and must
// not start with com.github.dewarepk.synthetixlib.
val libRootPackage = "com.github.dewarepk.synthetixlib"

tasks.shadowJar {
    archiveClassifier.set("")
    relocate(libRootPackage, "${project.group}.lib.synthetixlib")
}
tasks.jar {
    archiveClassifier.set("plain")
}
tasks.assemble {
    dependsOn(tasks.shadowJar)
}
// @end

val verifyRelocation = tasks.register("verifyRelocation") {
    group = "verification"
    description = "Fails if the shaded jar still contains un-relocated SynthetixLib classes."
    val jarFile = tasks.shadowJar.flatMap { it.archiveFile }
    inputs.file(jarFile)
    doLast {
        val prefix = libRootPackage.replace('.', '/') + "/"
        ZipFile(jarFile.get().asFile).use { zip ->
            val leaked = zip.entries().asSequence().map { it.name }.filter { it.startsWith(prefix) }.toList()
            if (leaked.isNotEmpty()) {
                throw GradleException("Un-relocated SynthetixLib classes in the shaded jar:\n" + leaked.take(20).joinToString("\n"))
            }
        }
    }
}
tasks.check {
    dependsOn(verifyRelocation)
}

// --- Dev servers and smoke tests (Paper 1.21.11 on Java 21, latest 26.x on Java 25) ----------------
// ./gradlew runServer      Paper 1.21.11, port 25565, run/1.21.11     ./gradlew runServer26   Paper 26.x, port 25566
// ./gradlew smokeTest      boots both headless with -Dsynthetix.selftest=true; fails unless both write "status":"PASS"
// Java 25 must be installed for the 26.x server (Gradle finds installed JDKs by itself).
// @snippet:template-dev-servers
val paperMin = "1.21.11"
val paperLatest = "26.3"   // the newest 26.x Paper build you test on

runPaper {
    // The shaded jar is wired explicitly below, exactly once per server.
    disablePluginJarDetection()
}

// Extra plugins your product needs on the test servers (downloaded once into run-paper's cache, used by runServer*
// and smokeTest). Uncomment what you need; look up the exact version string on the plugin's page.
val extraPlugins = runPaper.downloadPluginsSpec {
    // github("MilkBowl", "Vault", "1.7.3", "Vault.jar")
    // modrinth("essentialsx", "2.21.2")
    // modrinth("packetevents", "2.11.0")
}

fun RunServer.devServer(mcVersion: String, javaVersion: Int, dirName: String, port: Int) {
    minecraftVersion(mcVersion)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(javaVersion)) })
    val dir = layout.projectDirectory.dir("run/$dirName")
    runDirectory.set(dir)
    pluginJars(tasks.shadowJar.flatMap { it.archiveFile })
    downloadPlugins.from(extraPlugins)
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
                motd=${project.name} dev $mcVersion
                """.trimIndent() + "\n"
            )
        }
    }
}

tasks.runServer {
    description = "Runs Paper $paperMin (Java 21) with this plugin."
    devServer(paperMin, 21, paperMin, 25565)
}

tasks.register<RunServer>("runServer26") {
    group = "run paper"
    description = "Runs Paper $paperLatest (Java 25) with this plugin."
    devServer(paperLatest, 25, paperLatest, 25566)
}

fun registerSmoke(name: String, mcVersion: String, javaVersion: Int, port: Int) =
    tasks.register<RunServer>(name) {
        group = "verification"
        description = "Boots Paper $mcVersion headless, runs the self-tests, fails unless all pass."
        devServer(mcVersion, javaVersion, "smoke-$mcVersion", port)
        val result = layout.projectDirectory.file("run/smoke-$mcVersion/selftest-result.json").asFile
        systemProperty("synthetix.selftest", "true")
        systemProperty("synthetix.selftest.output", result.absolutePath)
        timeout.set(Duration.ofMinutes(6))
        isIgnoreExitValue = true
        outputs.upToDateWhen { false }
        doFirst { result.delete() }
        doLast {
            if (!result.exists()) {
                throw GradleException("[$mcVersion] no self-test result: the plugin failed to enable or the server crashed. See the log above.")
            }
            val json = result.readText()
            if (!json.startsWith("{\"status\":\"PASS\"")) {
                throw GradleException("[$mcVersion] self-test FAILED:\n$json")
            }
            logger.lifecycle("[$mcVersion] self-test PASS")
        }
    }

val smokeMin = registerSmoke("smokeTestMin", paperMin, 21, 25575)
val smokeLatest = registerSmoke("smokeTestLatest", paperLatest, 25, 25576)
smokeLatest.configure { mustRunAfter(smokeMin) }

tasks.register("smokeTest") {
    group = "verification"
    description = "Self-tests on Paper $paperMin and $paperLatest."
    dependsOn(smokeMin, smokeLatest)
}
// @end
