# synthetixlib-gui — API design

Status: **Implemented (0.4.0)** — approved 2026-10-20 (all §10 defaults accepted); deviations in §11 · Task: P1-03 · Implementation: P2-19..P2-23 · Date: 2026-10-19
Package root: `com.github.dewarepk.synthetixlib.gui` · Depends on: core, config (codecs, locale), paper-api only

Inventory menus where **everything you see comes from a YAML file** and **everything that happens comes from code**
that the file refers to by name. A buyer can redesign a whole menu (layout, size, title, every item, sounds, texts)
without touching code. Resource packs are not built by the library: titles and items accept ItemsAdder / Nexo
placeholders and `item_model` keys, so the pack plugin the server already uses does the drawing.
Fixes SnapPlug bugs #2, #3, #5, #8 (see AGENTS.md).

---

## 1. A menu file (what the buyer edits)

```yaml
# plugins/Auction/menus/auction.yml   — copied from the jar on first start, then never overwritten
config-version: 1
# MiniMessage. ItemsAdder ":offset_-8::auction_bg:" or a Nexo glyph tag go through unchanged (§7).
title: ':offset_-8::auction_bg:<white>Auction <gray>(<page>/<pages>)'
type: chest            # chest (rows 1-6) | hopper | dispenser
rows: 6
update-every: 20t      # optional: re-render dynamic items/placeholders every 20 ticks (live prices, timers)
layout:                # one char = one slot; space = empty; must match rows x 9 (or the type's size)
  - "#########"
  - "#LLLLLLL#"
  - "#LLLLLLL#"
  - "#LLLLLLL#"
  - "#########"
  - "<##BIT##>"
items:
  '#':
    material: black_stained_glass_pane
    item-model: 'mypack:invisible'     # transparent model from the pack -> background art shows through
    hide-tooltip: true
  'L':
    list: lots                         # filled by code (§2); empty list slots use `empty:` below
    empty: { material: air }
  '<':
    material: arrow
    name: '<yellow>Previous page'
    hide-when: first-page
    click: [page-previous]
    sound: ui.button.click
  '>':
    material: arrow
    name: '<yellow>Next page'
    hide-when: last-page
    click: [page-next]
  'B':
    material: gold_ingot
    name: '<gold>Balance: <balance>'   # placeholder from code
  'T':
    state: notify                      # code returns the state name; each state is a full item
    states:
      'on':  { material: lime_dye, name: '<green>Notifications ON',  click: [action:toggle-notify] }
      'off': { material: gray_dye, name: '<gray>Notifications OFF', click: [action:toggle-notify] }
  'I':
    material: chest
    name: '<aqua>My listings'
    click:
      left:  [open:my-listings]        # per click type; a plain list = any click
      right: [close, 'command:auction sell']
```

Quote `'on'` / `'off'` / `'yes'` / `'no'` keys (YAML 1.1 reads them as booleans; unquoted ones still work via
`MenuItem.stateItem`). Item keys = the config `ItemStack` readable form (`material, amount, name, lore, item-model, glint`) plus
`hide-tooltip`, `tooltip-style`, `base64` for anything exotic. Unknown keys → config error, the item is kept; an item that cannot be decoded → empty slot. Menu still opens.

## 2. Code side (what the plugin author writes)

```java
GuiService gui = GuiService.create(lib, configs)          // attaches to lib; closes every open view on lib.close()
        .messages(lang, Messages::gui);                   // optional: <lang:key> tag in menu texts (§6)

MenuType auction = gui.menu("auction")                    // menus/auction.yml, bundled default from the jar
        .placeholder("balance", view -> Component.text(economy.balance(view.player())))
        .list("lots", view -> auctions.active(),          // source, called on open / refresh / page change
              (lot, view) -> lot.icon())                  // how one entry looks (ItemBuilder or a config item)
        .onListClick("lots", (lot, click) -> bidMenu.open(click.player(), lot))
        .state("notify", view -> prefs.notify(view.player()) ? "on" : "off")
        .action("toggle-notify", click -> { prefs.toggle(click.player()); click.view().refresh(); })
        .build();

auction.open(player);                                     // safe from any thread (§5)
MenuType bid = gui.menu("bid").<Lot>withContext()          // a menu opened *for something*: view.context() = the lot
        .placeholder("price", view -> Component.text(view.context().price()))
        .action("confirm", click -> auctions.bid(click.player(), click.view().context()))
        .build();
```

- `menu(id)` fails fast at `build()` if the file uses an `action:`, `list:`, `state:` or placeholder the code did not
  register (logged with file + char, menu still opens with that item inert; an unknown placeholder only warns), so a typo shows at startup, not on click.
- Built-in click actions: `close`, `back` (previous menu of this viewer), `open:<menu>`, `refresh`, `page-next`,
  `page-previous`, `command:<cmd>` (run as the player), `console:<cmd>` (run as console — the file is owner-controlled),
  `message:<lang key or MiniMessage>`, `sound:<key>`, `action:<name>` (code). Several run in order.
- Placeholders in every text: `<player>`, `<page>`, `<pages>`, plus the menu's own. Values are plain components,
  never re-parsed (no MiniMessage injection from item names / player input).
- `gui.reload()` re-reads all menu files; open views re-render with the new file on the next refresh.

## 3. Views — one per viewer (bug #5)

`MenuType` is immutable and shared. `open` creates a `MenuView`: own inventory, own page, own context, own state.
Nothing per-player lives in the `MenuType` or in a static map (bug #4). `MenuView`: `player()`, `context()`,
`page()`, `page(int)`, `refresh()`, `refresh(char)`, `close()`, `back()`. The view is dropped on close / quit / world
change / death, and every view is closed on `lib.close()`.

Holder: each view's inventory has the library's own `InventoryHolder`; checks use `getHolder(false)` only (bug #8).

## 4. Click safety (bugs #2, #3) — deny by default

Every slot is locked unless the file marks it as an input slot:

```yaml
  'S':
    input: true                 # the player may put/take items here (e.g. "item to sell")
    accept: [ '*' ]             # or a list of materials / tags; others are refused
    max-amount: 64
```

Listener rules (all unit-tested on a fake click model, then smoke/E2E):
- Cancel every click, drag and move by default; allow only inside input slots.
- `COLLECT_TO_CURSOR` (double-click) is cancelled whenever the view has any locked slot (bug #2).
- Shift-click from the player inventory is cancelled and re-done by us into input slots only (bug #3).
- Number-key swap, off-hand swap (F), drop (Q) on locked slots, `MOVE_TO_OTHER_INVENTORY` from a locked slot: cancelled.
- Drag over any locked slot: whole drag cancelled.
- `InventoryMoveItemEvent` (hoppers) into a view: cancelled.
- Input items are handed back on close / quit / kick / server stop (to inventory, overflow dropped at the player); on
  death they join the drops, or stay in a kept inventory. Code gets `MenuBuilder#onInputChange(char, (view, items) -> ..)`
  and `MenuView#takeInputs()` / `takeInputs(char)` / `inputs(char)` (atomic: empties the slots and returns them, so a
  confirm click cannot pay twice).
- An input item in the file has no `material`, `name` or `click` (it shows nothing of its own); `accept` takes `*`,
  material names and item tags (`'#minecraft:swords'`); `max-amount` caps the slot.
- Clicks are debounced per view (one handler at a time; a second click while an `action` is still running is ignored).

## 5. Threads (Folia-safe)

`open`, `refresh`, `close` may be called from any thread; they hop onto the player's entity scheduler.
Click handlers run on the player's thread. `list` sources may be slow: `.listAsync(...)` runs the source on
`scheduler().async()` and renders back on the player's thread (shows the `loading:` item meanwhile).

## 6. Text and locale

- Texts are MiniMessage written in the menu file (simplest for buyers).
- Multi-language servers write `<lang:menus.auction.title>` instead; the key is read from the viewer's locale file
    `LocaleHandle`, per player). Both can be mixed.
- `<lang:key[:arg...]>` with a key that is **not** in the language files is MiniMessage's own tag: a
  `Component.translatable(key, args)` with the arguments as text, so vanilla keys (`<lang:item.minecraft.diamond>`) show in
  the client's language. The key is reported once per view as a warning unless it starts with a vanilla prefix
  (`block. item. entity. effect. enchantment. biome. container. gui. key. options. attribute. advancements. stat.`),
  because then it is most likely a typo.

## 7. Resource packs (no pack building in the library)

- `title` is MiniMessage; ItemsAdder `:name:` / `:offset_-8:` is plain text for MiniMessage and goes through as is.
  Nexo glyph tags must not be eaten by our parser: unknown tags are kept literally (non-strict MiniMessage). A smoke
  self-test asserts both strings reach the inventory title unchanged.
- `item-model` on any item → the pack's model (transparent "click areas", custom buttons). `tooltip-style` too.
- Big buttons = several slots with the same char and one invisible model; background art = title glyph.
- Paper 1.21.11 cannot change an open title: a title that changes (e.g. contains `<page>`) re-opens the inventory,
  which re-centres the mouse pointer on every page change. Keep live values out of titles where that matters. Input
  items and the item on the cursor move to the new inventory; nothing is lost or doubled.
- Without the pack the menu still works with the vanilla `material` (no special fallback system).

## 8. Minecraft limits (documented, not hidden)
Fixed 9-column grid; one-line title (multi-line only via font offsets); hover/click only on slots; animation = item
or title swaps through `update-every`; text input → anvil is not in this module (use the dialog module).

## 9. Testing
- Unit: layout parsing (size mismatch, unknown char, missing item), action parsing, click policy table
  (every `ClickType` × `InventoryAction` × locked/input slot), placeholders not re-parsed, pagination math.
- Smoke (in-server, no player): build every bundled menu, unknown action reported, title passthrough (§7).
- E2E with go-mcbots (ROADMAP): bot opens a menu and tries every dupe path from §4; inventory diff must be zero.

## 10. Decisions for owner (defaults chosen, change if you disagree)
1. **Menus in YAML files `menus/<id>.yml`**, bundled default copied once, never overwritten (chosen) vs code-only menus
   with optional config.
2. **Behaviour by name** (`action:<name>`, `list:`, `state:` registered in code; file only refers to them) (chosen).
3. **`console:<cmd>` action allowed** (chosen; file is owner-controlled) vs only `command:` as the player.
4. **Types:** chest 1-6 rows + hopper + dispenser (chosen) vs chest only. No anvil (dialog module covers input).
5. **Text inline in menu files, `<lang:key>` optional** (chosen) vs every text in lang files.
6. **`update-every`** per menu for live data/animation (chosen) vs manual `refresh()` only.
7. **Input slots opt-in with `accept` filter, returned on close, `takeInputs()` atomic** (chosen).
8. **No resource-pack generation / no fallback system** (decided 2026-10-19, owner: ItemsAdder does it via title).

## 11. Deviations from the design (as built, 0.4.0)
- `MenuType<C>` / `MenuBuilder<C>` are generic in the context type (`gui.menu("bid").<Lot>withContext()`); `open:<menu>` from a
  file opens the target without a context.
- Public additions the design did not spell out: `MenuBuilder#listAsync`, `#onInputChange`, `MenuView#inputs/takeInputs`;
  `@ApiStatus.Internal` seams for self-tests: `MenuType#preview`, `#previewTitle`, `#attach`.
- `update-every` accepts `<n>t` and durations (`gui.file.Ticks`; the config module has no tick unit).
- Unknown keys in a menu file are errors but the item is kept; an item that cannot be decoded gives empty slots; a bad click
  action leaves the whole item without clicks. YAML reads `on`/`off` as booleans: `MenuItem#stateItem` maps them back.
- An unregistered placeholder only warns (it is text); an unregistered `action:` / `list:` / `state:` makes the item inert.
- Paper 1.21.11 has no `Component` title setter: a changed title (or shape) re-opens a new inventory carrying the input items
  and cursor (§7). A view that cannot be closed on its thread clears only its own items, never the player's input items.
- Resource packs needed no code: ItemsAdder `:x:` is plain text and Nexo `<glyph:x>` stays literal in MiniMessage.
- Not covered by self-tests (needs a real client or Folia, left for the go-mcbots E2E): vanilla carrying out an allowed click,
  the cursor surviving a real re-open, Folia thread hops, quit/death order.

- Added in P2-32 (links to the dialog module, no compile dependency, dialog.md §8): `core.ScreenOpener` (`boolean open(Player, String id)`),
  `GuiService#link(prefix, opener)` and `#opener()`. A click step `<prefix>:<id>` that is not a built-in name parses to
  `ClickAction.Linked(prefix, id)` (so a typo with an argument is no longer a parse error; it is reported when the menu loads: item
  inert, "no module is linked to it"). Link before building the menus (or `reload()` after). `dialogs.link("menu", gui.opener())` is the
  other direction. Built-in names cannot be prefixes.
