# P2-10 Bukkit codecs: registry types, Location, ItemStack
Status: done · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-config · Depends on: P2-09 · Design: config.md §3, §4c

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/config.md and docs/plan/tasks/P2-10-bukkit-codecs.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- Registry-backed types via Paper `RegistryAccess.registryAccess().getRegistry(RegistryKey.X)`: Material
  (use `Material.matchMaterial` if Material has no registry key in 1.21.11), Sound, EntityType, Enchantment,
  PotionEffectType, Biome. Value = key string, `minecraft:` namespace optional on input, always written in short
  form for vanilla keys. Unknown key → DecodeException with suggestions (closest 3 by edit distance).
- Location: `{world, x, y, z, yaw, pitch}` (yaw/pitch optional, default 0). Unknown world → error (keep raw).
- ItemStack: readable form `{material, amount, name, lore, item-model, glint}` (name/lore MiniMessage via
  `lib.text()`) when the stack round-trips exactly through that form (encode, decode, compare with `isSimilar` +
  amount); otherwise `{base64: ...}` from `serializeAsBytes()`. Decoding accepts both.
- Registered by default in every `Codecs` created by `Configs`.

## Tests
Unit where possible (Location parsing logic, base64 detection); self-tests `config.codec.material`,
`.sound`, `.location`, `.item.simple` (readable form chosen), `.item.complex` (e.g. enchanted book with stored
enchant → base64 form, exact round-trip), `.unknown-key-suggestion`.

## Acceptance criteria
- [x] all types above round-trip on 1.21.11 and 26.3
- [x] no deprecated API used (list warnings in Result)
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
Ran `gradlew.bat check smokeTest`: green; smokeTest 47/47 on Paper 1.21.11 and 26.3.
- Code: `config.codec` gets `BukkitCodecs` (public, `@Internal`; `Configs` calls it with `lib.text()` and the server),
  plus package-private `RegistryCodec`, `MaterialCodec`, `LocationCodec`, `ItemStackCodec`, `Suggestions`, `Keys`.
- Registry types (Sound, EntityType, Enchantment, PotionEffectType, Biome) go through
  `RegistryAccess...getRegistry(RegistryKey.X)`. Material has no registry key in 1.21.11 (ITEM/BLOCK exist but hold
  `ItemType`/`BlockType`), so it uses `Material.matchMaterial`; the enum name `DIAMOND` is accepted on input, short key
  `diamond` is written. Sound also accepts the old enum style `ENTITY_PLAYER_LEVELUP` on input (extra, 3 lines).
- Unknown key -> `unknown sound "x", did you mean a, b, c?` (3 closest by Levenshtein, always shown).
- ItemStack: readable form is only used when decode(readable) `isSimilar` + same amount to the original, else
  `{base64}`. Names are `customName` (same as `ItemBuilder.name`), serialized with MiniMessage; an explicit
  `italic=false` is left implicit because decoding adds it back (an anvil-style italic name therefore becomes base64).
  Amount is limited to 1..99 on decode; non-item materials (water, air) are rejected.
- Rule fallout: ArchUnit forbids static `Set` fields, so key lists are `switch` methods, not constants.
- Unit tests (no server): `BukkitCodecsTest` (Location via Proxy `World`, Suggestions, Keys, base64 detection, and
  `MaterialCodec`, which works without a server). Self-tests: `ConfigBukkitSelfTests` (material, sound, registries
  with a full round trip of every registry value, unknown-key-suggestion, location, item.simple, item.complex).
- No deprecation warnings (clean recompile of config, tests and example).
