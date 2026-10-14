# P2-09 Codec layer (records, generics, sealed polymorphism, JSON)
Status: todo · Approved: yes · Model: sonnet · Effort: high
Module: synthetixlib-config · Depends on: P2-08 · Design: config.md §1, §3, §3b

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/config.md and docs/plan/tasks/P2-09-codecs.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.
This is the core of the config module: design the internal type model carefully before coding.

## Spec
- `config.codec`: `Codec<T>` (`T decode(Object raw, DecodeContext ctx)`, `Object encode(T value)`; helpers
  `Codec.of(Function, Function)`, `Codec.string(parse, format)`), `Codecs` (registry obtained from
  `configs.codecs()`; `register(Class, Codec)`, `encode(Object)`, `decode(Object, Class)`,
  `decode(Object, java.lang.reflect.Type)` is NOT public — public API stays Class-based + a `TypeToken<T>`-style
  helper for generic roots), `DecodeException` carrying a path like `zones.spawn.radius` / `rewards[1].chance`.
- Data tree: `Map<String,Object>` (LinkedHashMap, keeps order), `List<Object>`, `String`, `Number`, `Boolean`.
- Built-ins: boolean/int/long/double/float (+boxed, with range checks on narrowing), String, enums (case-insensitive,
  error lists valid values), Duration (`250ms`,`30s`,`5m`,`1h30m`,`2d`; encode to the shortest exact form), UUID,
  NamespacedKey, Adventure `Key`, List/Set/Map<String,T>/Optional<T>, nested records.
- Records (in `config.mapping`, reflection allowed): components ↔ kebab-case keys (`minBid`→`min-bid`,
  `@Key` override); missing key → error `missing` (the YAML layer later substitutes defaults); unknown keys
  → reported as warnings via `DecodeContext`; canonical constructor invocation; cache metadata per class in the
  `Codecs` instance.
- Annotations in `config.annotation` (runtime retention): `@Key`, `@Comment(String[])`, `@Range(min,max)`,
  `@NotEmpty`, `@TypeName`, `@Nullable` support (jspecify) — validation of `@Range/@NotEmpty` happens in decode.
- Sealed interfaces of records: discriminator key `type`, value = kebab-case of simple name with the interface
  simple name suffix removed (`ItemReward`→`item`) unless `@TypeName`. Duplicate names → IAE at first use.
- JSON: `codecs.toJson(Object)` / `fromJson(String, Class)` via Gson **if Gson is on paper-api's compile
  classpath**; if not, skip and report in Result (do not add a dependency — ADR 0012).
- Decode errors collect **all** problems (not just the first) into `DecodeException#problems()`.

## Tests (unit, no server)
Round-trip `decode(encode(x)).equals(x)` for every built-in and for nested records with generics
(`List<Map<String, Zone>>`), sealed polymorphism (incl. unknown type error), kebab-case + @Key, @Range/@NotEmpty
errors with correct paths, multiple errors collected, Duration parse/format table, enum error message, Optional/null.

## Acceptance criteria
- [ ] API as above; deviations listed in Result
- [ ] reflection only in `config.mapping` (ArchUnit green)
- [ ] all tests above pass
- [ ] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
