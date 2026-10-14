# P2-05 ItemBuilder
Status: done · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-core · Depends on: P2-03, P2-04 · Design: docs/design/core.md §4

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/core.md (§4) and docs/plan/tasks/P2-05-itembuilder.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- Package `com.github.dewarepk.synthetixlib.core.item`. Public `Items` (from `lib.items()`: `of(Material)`,
  `from(ItemStack)`) and `ItemBuilder`.
- Builder methods exactly as design §4: `name, itemName, lore(String, TagResolver...), lore(List<Component>),
  clearLore, itemModel(NamespacedKey), amount, glint(boolean), hideTooltip(boolean),
  set(PdcKey<T>, T), meta(Consumer<ItemMeta>), edit(Consumer<ItemStack>), build()`.
- Text via the instance's `TextService.parseItem` (non-italic default).
- `from(stack)` clones the input; the input is never mutated. `build()` returns a new stack every call.
- Operations are recorded and applied in order at `build()` on a fresh clone of the base stack
  (so the builder is a reusable template). `amount` validated 1..max stack size at build.
- Forbidden: `setCustomModelData` (ArchUnit already blocks it).

## Tests
- Self-tests: `core.item.basic` (name/lore/model/glint read back via meta), `core.item.from-no-mutate`,
  `core.item.build-distinct` (two builds are not the same instance and editing one doesn't affect the other),
  `core.item.pdc` (value set through builder readable through the key), `core.item.lore-placeholder`.

## Acceptance criteria
- [x] API matches design §4
- [x] self-tests pass on 1.21.11 and 26.3
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
Implemented `core.item` (`Items`, `ItemBuilder`) and `SynthetixLib.items()`.
- Operations are recorded and replayed on a fresh clone of the base stack at `build()`; `from(stack)` clones the input. `amount` is validated against `1..maxStackSize` at build.
- Text goes through the instance's `TextService.parseItem` (non-italic default); markup is parsed when the method is called.
- `Items.create(text)` is public only so `SynthetixLib` can build it; not meant for consumers.
- Self-tests: `core.item.{basic,from-no-mutate,build-distinct,pdc,lore-placeholder,lore-replace-clear,amount-validated}`.
- `gradlew.bat check smokeTest`: BUILD SUCCESSFUL; 33/33 self-tests PASS on 1.21.11 and 26.3.
