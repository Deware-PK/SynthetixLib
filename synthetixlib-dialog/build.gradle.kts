plugins {
    id("synthetix.library")
}

description = "SynthetixLib dialog: Paper dialogs defined in YAML files, behaviour registered by name in code"

dependencies {
    api(project(":synthetixlib-config"))
}
