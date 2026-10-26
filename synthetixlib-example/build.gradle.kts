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
    implementation(project(":synthetixlib-dialog"))

    // Loaded at runtime by ExampleLoader (paper-plugin.yml), never shaded.
    compileOnly(libs.hikaricp)

    // Only for StoreTestExample (recipe 4): a plain JUnit test needs the pool and a driver itself.
    testImplementation(libs.hikaricp)
    testImplementation(libs.sqlite.jdbc)
}
