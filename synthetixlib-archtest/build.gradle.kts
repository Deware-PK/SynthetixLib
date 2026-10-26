plugins {
    id("synthetix.java-base")
}

description = "Architecture rules (ArchUnit) for every SynthetixLib module. Not published."

dependencies {
    // Every published library module MUST be listed here, otherwise its classes are not checked.
    testImplementation(project(":synthetixlib-core"))
    testImplementation(project(":synthetixlib-config"))
    testImplementation(project(":synthetixlib-command"))
    testImplementation(project(":synthetixlib-gui"))
    testImplementation(project(":synthetixlib-database"))
    testImplementation(project(":synthetixlib-dialog"))
    testImplementation(project(":synthetixlib-selftest"))

    testImplementation(libs.archunit)
    // Only so the fixture that breaks the HikariCP rule compiles; the rule itself works on names.
    testCompileOnly(libs.hikaricp)
}
