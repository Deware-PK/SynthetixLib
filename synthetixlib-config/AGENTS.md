# synthetixlib-config

Package: `com.github.dewarepk.synthetixlib.config` (only this module may use it). Design: docs/design/config.md.

Packages (all under the package above)
- `config` — public entry types (`Configs`, handles)
- `config.annotation` — `@Comment`, `@Key`, `@Range`, ...
- `config.codec` — `Codec` / `Codecs` serialization layer
- `config.migration` — `ConfigTree` and version migrations
- `config.mapping` — INTERNAL, the only place allowed to use `java.lang.reflect` (ADR 0013)
- `config.yaml` — INTERNAL, YAML read/write on top of Bukkit `YamlConfiguration`

Rules specific to config
- Reflection only in `config.mapping`, only on classes passed in by the consumer; never `Class.forName`,
  never `setAccessible` (ArchUnit enforces).
- No third-party dependencies (ADR 0012); `Configs.create(lib)` attaches itself to the lib lifecycle.
- Never re-save a file on a plain reload; keep comments (SnapPlug bug #6).
