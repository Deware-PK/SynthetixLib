# P2-10 Bukkit codecs: registry types, Location, ItemStack
Status: todo · Approved: yes · Model: sonnet · Effort: medium
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
- [ ] all types above round-trip on 1.21.11 and 26.3
- [ ] no deprecated API used (list warnings in Result)
- [ ] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
