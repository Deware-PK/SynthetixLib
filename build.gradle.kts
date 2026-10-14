// Root project has no code. Shared build configuration lives in build-logic/ (convention plugins):
//   synthetix.java-base     - Java 21 toolchain, paper-api compileOnly, JUnit, forbidden-pattern check
//   synthetix.library       - published library module (java-library + maven-publish)
//   synthetix.paper-plugin  - runnable Paper plugin (shadow + relocate, run-paper, smoke tests)
// Versions live in gradle/libs.versions.toml.
