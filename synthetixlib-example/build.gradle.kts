plugins {
    id("synthetix.paper-plugin")
}

description = "SynthetixLib example plugin: test bed for smoke tests and source of docs examples"

synthetixPlugin {
    libsPackage.set("com.github.dewarepk.synthetixexample.libs")
}

dependencies {
    implementation(project(":synthetixlib-core"))
    implementation(project(":synthetixlib-config"))
    implementation(project(":synthetixlib-command"))
    implementation(project(":synthetixlib-gui"))
    implementation(project(":synthetixlib-database"))

    // Loaded at runtime by ExampleLoader (paper-plugin.yml), never shaded.
    compileOnly(libs.hikaricp)
}
