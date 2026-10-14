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
        }
    }
}
