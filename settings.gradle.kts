pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
    }
}

rootProject.name = "SynthetixLib"

// Library modules (published). Add new ones here AND to synthetixlib-archtest dependencies.
include("synthetixlib-core")
include("synthetixlib-config")
include("synthetixlib-command")
include("synthetixlib-gui")
include("synthetixlib-database")
include("synthetixlib-dialog")
include("synthetixlib-selftest")

// Internal modules (not published)
include("synthetixlib-example")
include("synthetixlib-archtest")
