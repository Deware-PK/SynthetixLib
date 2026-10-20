plugins {
    id("synthetix.library")
}

description = "SynthetixLib gui: inventory menus defined in YAML files, behaviour registered by name in code"

dependencies {
    api(project(":synthetixlib-config"))
}
