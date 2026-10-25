import java.util.zip.ZipFile

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
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
    for (module in listOf("core", "config", "command", "gui", "dialog" /*, "database" */)) {
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
