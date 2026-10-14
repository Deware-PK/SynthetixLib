plugins {
    id("synthetix.paper-plugin")
}

description = "SynthetixLib example plugin: test bed for smoke tests and source of docs examples"

synthetixPlugin {
    libsPackage.set("com.github.dewarepk.synthetixexample.libs")
}

dependencies {
    implementation(project(":synthetixlib-core"))
}
