# P1-01 Core API design
Status: todo
Module: synthetixlib-core   Depends on: P0-01

## Goal
Write `docs/design/core.md`: how a plugin author uses core, as code examples first, then type list.
No implementation code. Owner reviews and approves before P2 core tasks are created.

## Must cover
- `SynthetixLib.init(plugin)` / `close()` lifecycle, what the instance owns, bootstrap hook (PluginBootstrap) shape
- Scheduler facade: global / region(Location|Chunk) / entity(Entity) / async; delayed + repeating; cancellation;
  auto-cancel on close; works on Folia and Paper
- Text: MiniMessage helpers, placeholders (tag resolvers), sending to Audience
- ItemBuilder: data components, `item_model`, name/lore via MiniMessage, PDC tags, immutability/copy semantics
- PDC helpers (typed keys namespaced to the consumer plugin)
- What is explicitly NOT in core (goes to other modules)
- How each part will be tested (unit vs self-test)

## Acceptance criteria
- [ ] docs/design/core.md exists with runnable-looking examples for every bullet above
- [ ] each design choice that differs from SnapPlug explains why (link bug # if relevant)
- [ ] open questions listed at the end for the owner
