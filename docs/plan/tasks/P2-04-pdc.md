# P2-04 Typed PDC keys
Status: todo · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-core · Depends on: P2-01 · Design: docs/design/core.md §5

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/core.md (§5) and docs/plan/tasks/P2-04-pdc.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- Package `com.github.dewarepk.synthetixlib.core.pdc`. Public `PdcKeys` (factory, from `lib.keys()`) and
  `PdcKey<T>` (immutable; `key()`, `get/has/set/remove` for `PersistentDataHolder` and `ItemStack`).
- Factories: `string, integer, longKey, doubleKey, bool, uuid, bytes, of(String, PersistentDataType<?, T>)`.
- Names validated against `[a-z0-9/._-]+` → IAE with the bad name in the message.
- `NamespacedKey(plugin, name)` — namespace is always the consumer plugin.
- `uuid`: custom `PersistentDataType<byte[], UUID>` (16 bytes, big-endian msb/lsb), package-private.
- ItemStack: read with `getPersistentDataContainer()` (view, no meta copy); write/remove with
  `editPersistentDataContainer(...)`. `get` returns `Optional<T>`.
- `SynthetixLib` gains `keys()`.

## Tests
- Unit: name validation, uuid type round-trip (pure, no server).
- Self-tests `core.pdc.item.<type>` round-trip for every factory type on an ItemStack;
  `core.pdc.entity` on a spawned marker entity (remove it afterwards; run on the entity/region thread via
  `Bukkit` region scheduler directly or the core scheduler if P2-02 is done).

## Acceptance criteria
- [ ] API matches design §5
- [ ] ItemStack reads do not clone meta (use the view API)
- [ ] tests pass on 1.21.11 and 26.3
- [ ] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
