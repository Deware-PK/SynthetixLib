# 0009. Third-party runtime libraries via Paper PluginLoader

- Status: Accepted (scope under review, see ADR 0012)
- Date: 2026-10-14

## Context
Shading HikariCP/JDBC drivers bloats jars and duplicates them across products.

## Decision
Products use `paper-plugin.yml` with a `PluginLoader` + `MavenLibraryResolver` (using Paper's Maven
Central mirror constant, not Maven Central directly) to fetch runtime libs. SynthetixLib only shades
itself and provides a helper so a consumer adds the libs a module needs in one call.

## Consequences
Products are Paper plugins (`paper-plugin.yml`), not legacy `plugin.yml`. First server start downloads libs.
