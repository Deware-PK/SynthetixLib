plugins {
    id("synthetix.library")
}

description = "SynthetixLib config: typed records <-> YAML, codecs, migrations, locale"

dependencies {
    api(project(":synthetixlib-core"))
}
