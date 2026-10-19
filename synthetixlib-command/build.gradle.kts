plugins {
    id("synthetix.library")
}

description = "SynthetixLib command: native Brigadier fluent API, typed arguments, permissions, locale messages"

dependencies {
    api(project(":synthetixlib-config"))
}
