# synthetixlib-config

Package: `com.github.dewarepk.synthetixlib.config` (only this module may use it). Design: docs/design/config.md.

Packages (all under the package above)
- `config` — public entry types (`Configs`) and what it builds: `ConfigHandle` (one file), `ConfigCollection` (one file
  per object, async write queue), `LocaleHandle` (one file per language)
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
- Collections: ids are `[a-z0-9_-]{1,64}`; file writes go through one queue per collection on the async scheduler and
  `close()` drains it; `delete` moves to `.deleted/`. Locale: an explicit `Locale` is always honoured, `perPlayer` only
  decides how an `Audience` is resolved.
- Implemented as designed in docs/design/config.md (deviations: section 10). Example usage: `synthetixlib-example`
  (`demo` package: settings, airdrops, lang).
- Known limits of file configs (comments only for fixed paths, a write drops blank lines, `ConfigTree` cannot address
  dotted keys, invalid list elements are dropped): docs/design/config.md section 2.
- Mapped records must be `public` (reflection without `setAccessible`); no static `Codec`/registry fields (ArchUnit) - codecs are built per `Codecs` instance.
