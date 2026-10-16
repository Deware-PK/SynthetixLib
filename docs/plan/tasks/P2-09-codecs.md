# P2-09 Codec layer (records, generics, sealed polymorphism, JSON)
Status: done · Approved: yes · Model: sonnet · Effort: high
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
- [x] API as above; deviations listed in Result
- [x] reflection only in `config.mapping` (ArchUnit green)
- [x] all tests above pass
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
1. **Verified:** `gradlew.bat check smokeTest` BUILD SUCCESSFUL. Unit: `CodecsTest` 56 + `DurationsTest` 38, ArchUnit 11 (all green, rules not touched).
   Smoke on Paper 1.21.11 and 26.3: 37/37 self-tests, incl. new `config.codecs.records-json` and `config.codecs.errors`
   (Gson, jspecify and record reflection inside the relocated jar).
2. **Design.** Internal type model = `mapping.TypeRef` (a `Type` + bindings of type variables in scope, so `Page<Zone>`
   resolves `items` to `List<Zone>`; used as cache key). `mapping.CodecResolver` turns a `TypeRef` into a `Codec`
   (registered > Optional/List/Set/Map<String,T> > enum > record > sealed), caches per `Codecs` instance (no statics),
   builds records/sealed in two steps (construct, link) so self-referencing records work, and fails with IAE on first
   use for anything unsupported. Record metadata (`RecordMeta`) is cached per class. Reflection lives only in
   `config.mapping`; no `setAccessible`, so **records must be `public`** (clear IAE otherwise).
3. **Public API (`config.codec`):** `Codec` (`of`, `string`), `Codecs` (`create()`, `register`, `encode`, `decode`,
   `toJson`, `fromJson`, `newContext`), `DecodeContext`, `DecodeException#problems()`, `Problem(path, message)`,
   `TypeToken<T>`. Annotations `@Key @Comment @Range @NotEmpty @TypeName`; `@Nullable` = jspecify (on the component type).
   `Configs#codecs()` added.
4. **Deviations / additions (please review):**
   - Extra public types the spec implied but did not name: `Problem`, `TypeToken`, `Codecs.create()` (usable without `Configs`),
     `Codecs.newContext()` + `decode(raw, type, ctx)` overloads (to read warnings), `encode(value, Class|TypeToken)` and
     `toJson(value, Class|TypeToken)` (needed to write the `type` key of a sealed value; plain `encode(Object)` goes by runtime
     class, so a sealed leaf is written **without** `type`), `register` returns `this`, `DecodeContext.fail/expected/decode`.
   - `Duration`: units `d h m s ms us ns` (us/ns added so every `Duration` round-trips exactly); **non-negative only**
     (negative = error on parse and IAE on encode). "Shortest exact form" implemented as canonical largest units, zero groups
     omitted (`90m` -> `1h30m`, `90s` -> `1m30s`, zero -> `0s`). Plain numbers are rejected (no implicit unit).
   - Leniency: `String` and `Codec.string` accept numbers/booleans as text; `int/long` accept whole floating values (`5.0`);
     enums also match `dark-oak` / `dark oak`. `Map` keys must be `String` (IAE otherwise).
   - `missing` message is exactly `missing`; null for a non-Optional/non-`@Nullable` component is `must not be null`.
     Empty `Optional` / null `@Nullable` are omitted from the encoded map.
   - Validation messages: `must be >= 1 (was 0)`, `<= n`, `between a and b`, `must not be empty`. `@Range` compares as doubles.
     `@Range/@NotEmpty` on an unsuitable type -> IAE on first use. Unknown keys: `ctx.warn("unknown key")` at the key's path.
   - Sealed: nested sealed interfaces are flattened; a leaf with a custom registered codec works if it encodes to a map;
     a component named `type` clashes -> IAE (use `@Key`). Duplicate names -> IAE at first use.
   - JSON: Gson **is** on paper-api's compile classpath (2.11.0) and ships with the server, so implemented with the streaming
     `JsonReader/JsonWriter` only (no Gson reflection). Integers read as `Long`, others `Double`; NaN/Infinity -> IAE on write;
     nesting limited to 128 (DecodeException). Compact output only.
   - `@Comment` is declared but nothing reads it yet (P2-11 writes comments). `Validated` (design §1) is not part of this task.
   - Not exposed yet, P2-11 will need it: per-field fallback to a default record on invalid values (and telling "missing" from
     "invalid"). `RecordCodec` is the place to add it; `Problem` has only `path` + `message` for now.
