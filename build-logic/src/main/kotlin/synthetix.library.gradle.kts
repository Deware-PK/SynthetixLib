// Published library module: synthetixlib-<name>, package com.github.dewarepk.synthetixlib.<name>.
// Consumers shade + relocate it (docs/adr/0005). Until 1.0.0: publishToMavenLocal only.

plugins {
    id("synthetix.java-base")
    `java-library`
    `maven-publish`
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = project.name
            from(components["java"])
            pom {
                name.set(project.name)
                description.set("SynthetixLib ${project.name.removePrefix("synthetixlib-")} module: Paper plugin library (Brigadier, Folia-safe, Adventure only).")
                url.set("https://github.com/Deware-PK/SynthetixLib")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("Deware-PK")
                        name.set("Phakin (Deware)")
                        url.set("https://github.com/Deware-PK")
                    }
                }
                scm {
                    url.set("https://github.com/Deware-PK/SynthetixLib")
                    connection.set("scm:git:https://github.com/Deware-PK/SynthetixLib.git")
                    developerConnection.set("scm:git:ssh://git@github.com/Deware-PK/SynthetixLib.git")
                }
            }
        }
    }
}
