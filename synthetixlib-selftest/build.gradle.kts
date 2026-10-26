plugins {
    id("synthetix.library")
}

description = "SynthetixLib selftest: in-server self-tests for products (boot Paper, run checks, write a JSON result, stop)"

dependencies {
    api(project(":synthetixlib-core"))
}
