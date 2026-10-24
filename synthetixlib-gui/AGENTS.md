# synthetixlib-gui

Package: `com.github.dewarepk.synthetixlib.gui` (only this module may use it). Design: docs/design/gui.md.
Depends on core + config (menu files are read with the config codecs).

Status: **complete, 0.4.0** (P2-19 menu files, P2-20 views, P2-21 click safety, P2-22 locale/async/packs, P2-23 demo + carry-overs).

Types
- `GuiService` - one per `SynthetixLib`; `menu(id)` -> `MenuBuilder<Void>`; `reload()` re-reads every menu file.
  Registers the `click:` codec in `configs.codecs()`.
- `MenuBuilder<C>` - stores `placeholder`, `list`, `onListClick`, `state`, `action` (names a file may refer to) and
  `build()`s a `MenuType<C>`. `withContext()` must come first (it changes `C`).
- `MenuType<C>` - immutable apart from the swapped file contents; `file()` (parsed `MenuFile`), `isInert(char)`,
  `open(player)` / `open(player, context)` (any thread), `preview(...)` (`@ApiStatus.Internal`, for self-tests).
- `MenuView<C>` / `MenuClick<C>` - public interfaces; implemented by `MenuViewImpl` (below).
- `gui.file` (INTERNAL, public only so the config codecs can map them): `MenuFile`, `MenuItem`, `ClickAction`
  (sealed, `parse`/`format`), `ClickBindings`, `ClickKind`, `MenuKind`, `HideWhen`, `Ticks`.
- Package-private: `MenuStore` (menus/<id>.yml, bundled copy from the jar once, never overwritten), `MenuParser`
  (raw YAML -> `MenuFile`), `MenuChecker` (file vs registrations -> inert chars), `MenuLoader`, `MenuRegistry`
  (the menus of one service, reload), `ClickBindingsCodec`, `Registrations`.
- Views (P2-20), all package-private:
  - `ViewManager` - one per `GuiService`: opens views, owns the `ViewRegistry`, registers the events (click, drag,
    close, quit, death, world change) with plain executors (no reflection), hops threads, closes everything on `close()`.
  - `ViewRegistry<V, B>` - viewer UUID -> current view, and the `back` stack per viewer (max 32). Pure, unit-tested.
  - `MenuViewImpl` - one viewer's copy: inventory, page, context, lists, update task. All state is here.
  - `Renderer` - file + registrations + view -> inventory: `frame` (lists, pages, title), `fill` (slots), `build`
    (item), `effective` (hide-when / state). `Renderer.Pass` = the placeholders of one render, each value asked once.
  - `ViewHolder` - the library's `InventoryHolder`, one per inventory; `Pagination`; `DetachedView` (no viewer).
- Click safety and input slots (P2-21), all package-private and written without the server so they are unit-tested as
  tables:
  - `ClickPolicy` - THE dupe-critical function. `decide(SlotMap, StackOps, Click)` -> `ALLOW | DENY | REDO_SHIFT`,
    `decideDrag(...)`. Deny by default; only input slots take part, only for `accept`ed items within `max-amount`;
    double-click is denied while the menu has any locked slot (bug #2); shift-click in the player's inventory is
    `REDO_SHIFT` (bug #3). `ClickPolicyTest` compares every `ClickType` x `InventoryAction` x {locked, input,
    input-refused, bottom, outside} cell with an oracle. Never loosen a cell to get green.
  - `SlotMap` (which slot is an input and its rules), `StackOps<S>` (+ `BukkitStacks`, the only Bukkit implementation),
    `Inputs` (shift distribution, drain, hand back; counts must always add up), `Handoff` (re-open: input items and the
    cursor move to the new inventory), `AcceptFilter` (`*`, materials, `#tags`), `ClickGate` (one handler at a time per
    view), `Shutdown` (close every view, then unregister the listener).
  - `ViewManager` turns each event into `ClickPolicy.Click` facts and does what the verdict says; the event priority is
    `HIGHEST` so it has the last word. Also cancels `InventoryMoveItemEvent` / `InventoryPickupItemEvent` into or out of a
    menu.

Rules specific to gui
- A menu file never throws at load: every mistake is an error with its path (`items.U.click[0]`) and something safe is
  used instead (item dropped -> empty slots, bad action -> item without clicks, wrong layout size -> cut/padded). Only
  text that is not YAML gives no menu: first load = empty chest, reload = the last good menu stays.
- Errors = anything wrong in the file (unknown keys too, as the design says). Warnings = unregistered action / list /
  state (that item is inert), an unregistered placeholder (warning only: the item keeps its clicks and shows the tag as
  text) and layout characters nobody uses.
- Only bare tags `<word>` are treated as placeholders (`MenuChecker`); a name that `TextService#hasTag` knows (every
  standard MiniMessage tag, or a tag of `lib.text()`), MiniMessage tags with arguments (`<lang:..>`, `<#ff0000>`) and
  closing tags are not.
- Views: everything per viewer lives in `MenuViewImpl` or the service's `ViewRegistry`, never in a `MenuType` or a
  static. A click is answered by what the viewer saw (`Renderer.Shown`), not by recomputing.
- Titles: Paper 1.21.11 only has `InventoryView#setTitle(String)` (legacy), no `Component`. When the rendered title
  (or the size/type after a reload) differs, the view re-opens with a new inventory of the same contents; the old
  one's close event is ignored because `view.inventory()` is already the new one. Cost: the mouse pointer re-centres.
- A `<page>` (or any placeholder that changes) in a title makes the view re-open the inventory on every change; the
  pointer re-centres each time. Input items and the item on the cursor go along (`Handoff`); keep live values out of
  titles where that matters.
- `update-every` renders only dynamic items (list, state, hide-when, placeholders in name/lore) and the title;
  `refresh()` renders everything. Input slots are never drawn, cleared or redrawn: what is in them is the player's.
- Input items are handed back (inventory, overflow dropped at the player) when a view ends: close, quit, kick, world
  change, `lib.close()`; on death they join the drops (or the kept inventory). Every path drains the slots first, so a
  second path finds nothing: never lost, never doubled. `MenuView#takeInputs` drains the same way (atomic).
- `GuiService.close()` closes every inventory first and unregisters the listener last. Where a close cannot run on the
  viewer's thread (Folia, or the scheduler is closed) the menu's own items are cleared at once and the input items stay.
- A change to an input slot made by a player calls the handler of `MenuBuilder#onInputChange` once, on the viewer's thread
  after the click; the library's own changes (hand-back, `takeInputs`) do not.
- `MenuType#attach` (`@ApiStatus.Internal`) registers a view without opening it; the `gui.policy` self-test uses it with a
  Proxy player to send real events through the listener.
- YAML reads unquoted `on`/`off`/`yes`/`no` as booleans, so a state written `on:` is stored as `true`:
  look states up with `MenuItem.stateItem(name)`, never `states().get(name)`.
- Texts stay MiniMessage strings in the model; views parse them with the placeholders of that view (never re-parse
  placeholder values).
- No static state; the model records must stay `public` (the config module maps them by reflection, this module has none).
- Unit tests need no server: `GuiFixtures.codecs()` uses `Material.matchMaterial`. Everything that builds an
  `ItemStack` is covered by the `gui.*` self-tests in `synthetixlib-example`.
- Locale / async / packs (P2-22):
  - `GuiService#messages(lang, keys)` stores a `GuiMessages` in the `ViewManager`; `Renderer.Pass` adds `<lang:key>` on top of the
    placeholder resolver. Messages are parsed with the *placeholders only* resolver, so `<lang>` inside a message is never
    resolved again. Without `messages(...)` the tag is MiniMessage's own. A key in no language file becomes `Component.translatable(key, args as text)` (MiniMessage's own `<lang>`, so vanilla keys work); it warns once per renderer unless the key starts with a vanilla prefix.
  - `listAsync`: `Registrations.ListBinding#async`; the view owns an `AsyncLoads` (results + pending per list id, viewer thread
    only). `Renderer.frame` asks it for async lists: no answer yet -> `Frame#loading` holds the id and the slots show the
    item's `loading:`. When an answer lands the view renders with `keepLists` (no source is asked again, no loop). Previews
    have no `AsyncLoads`: their async source runs inline. Never call the source of an async list on the viewer's thread.
  - Resource-pack strings need no code: ItemsAdder `:x:` is text and unknown tags (`<glyph:x>`) stay literal in MiniMessage.
    `LangAndPassthroughTest` and the `gui.title-passthrough` self-test guard that; do not switch the parser to strict mode.
- Links (P2-32): `ClickAction.Linked(prefix, id)` for any `name:argument` that is not built in; `GuiService#link(prefix, ScreenOpener)` /
  `#opener()`; `MenuChecker` makes an item inert when its prefix is not linked (`MenuLoader` gets `links::containsKey`);
  `ViewManager#openLinked` calls the opener (the client replaces the inventory screen, no explicit close).
