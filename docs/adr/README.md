# Architecture Decision Records

One decision per file, numbered, never renumbered. To change a decision, add a new ADR that
**supersedes** the old one (and mark the old one `Superseded by NNNN`). Template: `0000-template.md`.

| # | Decision | Status |
|---|---|---|
| 0001 | Paper only, min 1.21.11, must run on 26.x | Accepted |
| 0002 | Java 21 toolchain and bytecode | Accepted |
| 0003 | Gradle multi-module + build-logic + version catalog | Accepted |
| 0004 | `SynthetixLib.init(plugin)` entry, no static state | Accepted |
| 0005 | Consumers shade + relocate the library | Accepted |
| 0006 | Native Brigadier commands | Accepted |
| 0007 | Typed config | Accepted |
| 0008 | Paper API only: no NMS/reflection, packets via PacketEvents | Accepted |
| 0009 | Runtime third-party libs via Paper PluginLoader | Accepted |
| 0010 | Verification harness (check / smokeTest / bot E2E) | Accepted |
| 0011 | Publishing: mavenLocal until 1.0.0, then JitPack | Accepted |
| 0012 | No third-party runtime deps except PacketEvents | Accepted |
| 0013 | Reflection allowed only in config.mapping | Accepted |
