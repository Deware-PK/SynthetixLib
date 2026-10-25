# gui

`synthetixlib-gui` (since 0.4.0). Inventory menus where **everything you see is a YAML file** and **everything that
happens is code the file refers to by name**. Dupe-safe by default (every slot locked), one view per viewer, Folia-safe.
Depends on core and config. Design: `docs/design/gui.md` (read its section 11, "Deviations").

## When to use / when not

- Use a menu to browse, page through, toggle, or let the player drop items into a slot.
- Player has to **type** text or a number, pick an option, answer yes/no: use a **dialog** (`dialog.md`), not an anvil.
- A menu and a dialog open each other from a button (`dialog:<id>` in the menu file, `menu:<id>` in the dialog file).
- Not in this module: resource-pack generation (titles and items just accept ItemsAdder / Nexo text and `item-model`).

## Minimal example

The file (copied from the jar into `plugins/<you>/menus/<id>.yml` on first start, **never overwritten**):

```yaml snippet=demo-menu-yml
title: '<lang:demo-title>'
type: chest
rows: 6
update-every: 20t        # re-renders the clock every second
layout:
  - "#########"
  - "#LLLLLLL#"
  - "#LLLLLLL#"
  - "#LLLLLLL#"
  - "#########"
  - "<TBKSODX>"
items:
  '#':
    material: gray_stained_glass_pane
    hide-tooltip: true
  'L':
    list: airdrops
    empty: { material: air }
  '<':
    material: arrow
    name: '<lang:demo-previous>'
    lore: ['<lang:demo-page>']
    hide-when: first-page
    click: [page-previous]
    sound: ui.button.click
  '>':
    material: arrow
    name: '<lang:demo-next>'
    lore: ['<lang:demo-page>']
    hide-when: last-page
    click: [page-next]
    sound: ui.button.click
  'T':
    state: notify
    states:
      'on':
        material: lime_dye
        name: '<lang:demo-notify-on>'
        lore: ['<lang:demo-toggle-hint>']
        click: ['action:toggle-notify']
      'off':
        material: gray_dye
        name: '<lang:demo-notify-off>'
        lore: ['<lang:demo-toggle-hint>']
        click: ['action:toggle-notify']
  'B':
    material: experience_bottle
    name: '<lang:demo-balance> <white><balance>'
  'K':
    material: clock
    name: '<lang:demo-clock> <white><time>'
  'S':
    input: true
    accept: ['*']
    max-amount: 64
  'O':
    material: emerald
    name: '<lang:demo-confirm>'
    lore: ['<lang:demo-input>']
    click: ['action:confirm']
  'D':
    material: writable_book
    name: '<lang:demo-dialog>'
    click: ['dialog:sell']
  'X':
    material: barrier
    name: '<lang:demo-close>'
    click: [close]
```

The code only registers the names the file uses (`list`, `state`, `action`, placeholders):

```java snippet=gui-menu-builder
final MenuType<Void> demo = gui.menu("demo")
        .placeholder("balance", view -> Component.text(view.player().getLevel()))
        .placeholder("time", view -> Component.text(LocalTime.now().format(CLOCK)))
        .list("airdrops", view -> entries(airdrops), (entry, view) -> icon(lib, lang, claims, entry, view.player()))
        .onListClick("airdrops", (Entry entry, MenuClick<Void> click) -> airdropMenu.open(click.player(), entry))
        .state("notify", view -> notify.get(view.player()).orElse(false) ? "on" : "off")
        .action("toggle-notify", click -> {
            notify.set(click.player(), !notify.get(click.player()).orElse(false));
            click.view().refresh();
        })
        .action("confirm", click -> confirm(click.player(), click.view().takeInputs('S'), lang))
        .build();
```

Wiring (once, in `onEnable`; `GuiService.create` attaches itself to `lib`, `lib.close()` closes every open view):

```java snippet=template-enable
lib = SynthetixLib.init(this);
final Configs configs = Configs.create(lib);
settings = configs.file("config.yml", Settings.class, Settings::defaults).load();
lang = configs.locale("lang", Messages.class, Messages::defaults).defaultLocale(Locale.US).load();

final DialogService dialogs = DialogService.create(lib, configs).messages(lang, Messages::dialog);
final GreetDialog greet = GreetDialog.create(dialogs, lang);
final GuiService gui = GuiService.create(lib, configs).messages(lang, Messages::gui)
        .link("dialog", dialogs.opener());
final MainMenu menu = MainMenu.create(gui);

CommandService.create(lib)
        .messages(lang, Messages::commands)
        .register(MyPluginCommand.build(settings, lang, menu, greet));

// Database module: Database.create(lib, <DatabaseSettings from config.yml>); closed together with lib.
getServer().getPluginManager().registerEvents(this, this);
```

`gui.menu(id)` reads `menus/<id>.yml`. `build()` checks the file against your registrations: a typo in an `action:`,
`list:` or `state:` makes that item **inert** and logs it at startup (the menu still opens); an unregistered placeholder
only warns and shows as text. `menu.open(player)` is safe from any thread.

## The menu file

| Key | Meaning |
|---|---|
| `title` | MiniMessage; `<lang:key>`, `<page>`, `<pages>`, `<player>` and your placeholders |
| `type` | `chest` (with `rows` 1-6), `hopper`, `dispenser` |
| `update-every` | `20t` or a duration: redraw dynamic items (list, state, hide-when, placeholders) and the title |
| `layout` | one string per row, one character = one slot, space = empty; must match the size |
| `items.<char>` | `material`, `amount`, `name`, `lore`, `item-model`, `glint`, `hide-tooltip`, `tooltip-style`, `base64` |
| `click` | steps: a list (any click) or `left` / `right` / ... lists; several steps run in order |
| `sound` | sound key played on click |
| `hide-when` | `first-page`, `last-page` |
| `list` / `empty` / `loading` | the char fills with a registered list; `empty` for unused slots, `loading` while async |
| `state` + `states` | code returns a state name; each state is a full item. Quote `'on'` / `'off'` / `'yes'` / `'no'` keys |
| `input: true`, `accept`, `max-amount` | the player may put items here (`accept`: `'*'`, materials, `'#minecraft:swords'`) |

Click steps: `close`, `back`, `refresh`, `page-next`, `page-previous`, `open:<menu>`, `command:<cmd>` (as the player),
`console:<cmd>` (as console; the file is owner-controlled), `message:<text>`, `sound:<key>`, `action:<name>` (your code),
and `<prefix>:<id>` for a linked module (`dialog:sell`). A bad step leaves the item without clicks.

A menu file never throws at load: every mistake is an error with its path (`items.U.click[0]`) and something safe is
used instead. Only text that is not YAML gives no menu (first load: empty chest; reload: the last good menu stays).

## Context menus, lists and inputs

A menu opened *for something* (a lot, a player) is generic in its context. `withContext()` must come first.

```java snippet=gui-context-menu
final MenuType<Entry> airdropMenu = gui.menu("airdrop").<Entry>withContext()
        .placeholder("name", view -> Component.text(view.context().airdrop().name()))
        .placeholder("interval", view -> Component.text(view.context().airdrop().interval().toString()))
        .placeholder("players", view -> Component.text(view.context().airdrop().minPlayers()))
        .placeholder("rewards", view -> Component.text(view.context().airdrop().rewards().size()))
        .build();
```

Slow list sources, input-slot callbacks and view controls:

```java snippet=gui-async-input
final MenuType<Void> history = gui.menu("history")
        .listAsync("entries", view -> loadHistory(view.player()),          // slow source: runs async
                (entry, view) -> lib.items().of(Material.PAPER).name("<white>" + lib.text().escape(entry)).build())
        .onInputChange('S', (view, items) -> view.refresh('C'))             // an input slot changed
        .action("next", click -> click.view().page(click.view().page() + 1))
        .action("leave", click -> {
            click.view().back();                                            // previous menu of this viewer
            click.view().close();
        })
        .build();
history.open(player);                                                       // any thread
```

Input slots are the only place a player can put items. Take them atomically (the slot is emptied and the stack returned,
so a double-click on "confirm" cannot pay twice):

```java snippet=gui-take-inputs
/** The items leave the slot at once (so a double click cannot pay twice), then go back to the player. */
private static void confirm(final Player player, final List<ItemStack> taken, final LocaleHandle<Messages> lang) {
    if (taken.isEmpty()) {
        lang.send(player, Messages::inputEmpty);
        return;
    }
    int count = 0;
    for (final ItemStack stack : taken) {
        count += stack.getAmount();
        player.getInventory().addItem(stack).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
    }
    lang.send(player, Messages::inputConfirmed, Placeholder.unparsed("count", String.valueOf(count)));
}
```

Input items are handed back (inventory, overflow dropped at the player) on close, quit, kick, world change and
`lib.close()`; on death they join the drops. `MenuBuilder#onInputChange(char, handler)` runs once per player change, on the
viewer's thread, not for the library's own hand-back or `takeInputs`.

## Linking to dialogs

```java snippet=gui-links
gui.link("dialog", dialogs.opener());              // menu files may now say: click: ['dialog:sell']
dialogs.link("menu", gui.opener());                // dialog files may now say: click: ['menu:history']
final FilesReloadResult reloaded = gui.reload();   // re-reads every menu file; open views redraw on refresh
```

Link **before** building menus (or `gui.reload()` afterwards). Built-in names (`close`, `back`, ...) cannot be prefixes. A
`<prefix>:<id>` nobody linked is reported when the menu loads and the item is inert.

## Text and locale

Texts can be plain MiniMessage in the file, or `<lang:key>` read from the viewer's language file:
`GuiService#messages(lang, Messages::gui)` where `gui` is a `Map<String, String>` component of your `Messages` record (keys
appear under `gui:` in `lang/<tag>.yml`). A `<lang:key>` that is in no language file is MiniMessage's own translatable tag
(vanilla keys such as `<lang:item.minecraft.diamond>` show in the client's language). Placeholder values are components and
are **never re-parsed** (no MiniMessage injection through item names or player input); put player text in a
placeholder or `lib.text().escape(...)`.

## API table

| Type | For | Since |
|---|---|---|
| `GuiService` | `create(lib, configs)`, `messages(lang, keys)`, `menu(id)`, `link(prefix, ScreenOpener)`, `opener()`, `reload()`, `lib()`, `configs()`, `isClosed()`, `close()` | 0.4.0 (`link`, `opener` 0.6.0) |
| `MenuBuilder<C>` | `withContext()`, `placeholder`, `list`, `listAsync`, `onListClick`, `state`, `action`, `onInputChange`, `build()` | 0.4.0 |
| `MenuType<C>` | `id()`, `file()`, `isInert(char)`, `open(player)`, `open(player, context)` | 0.4.0 |
| `MenuView<C>` | `player`, `context`, `page()` / `page(int)`, `refresh()` / `refresh(char)`, `close`, `back`, `inputs(char)`, `takeInputs()` / `takeInputs(char)` | 0.4.0 |
| `MenuClick<C>` | `player()`, `view()` | 0.4.0 |
| `core.ScreenOpener` | `boolean open(Player, String id)`, used by `link` | 0.6.0 |

Left out on purpose: `gui.file.*` (`MenuFile`, `MenuItem`, `ClickAction`, `ClickBindings`, `ClickKind`, `MenuKind`, `HideWhen`,
`Ticks`; public only so the config codecs can map them) and the `@ApiStatus.Internal` hooks `MenuType#preview`,
`#previewTitle`, `#attach` (self-tests).

## Threads

`open`, `refresh`, `close` may be called from any thread; they hop onto the player's entity scheduler. Click handlers run on
the player's thread, one at a time per view (a second click while an `action` still runs is ignored). `listAsync` sources run
on the async scheduler and the menu shows the item's `loading:` meanwhile; never call such a source yourself on the
viewer's thread. `list` sources are asked on open / refresh / page change: keep them fast and read thread-safe snapshots.

## Pitfalls

- Wrong: a menu field `Player viewer` set on open -> right: per-viewer data in `view.context()` or PDC; `MenuType` is shared
  and immutable (rule `gui-one-view-per-viewer`).
- Wrong: `inventory.getHolder()` -> right: the library uses `getHolder(false)` only; so must your listeners (rule
  `gui-getholder-false`).
- Wrong: your own `InventoryClickEvent` handler to allow a click in a menu -> right: mark an `input: true` slot; every other
  slot is locked, including double-click collect, shift-click and drag (rules `gui-deny-by-default`,
  `gui-shift-click-input-only`).
- Wrong: `setCustomModelData(int)` for menu art -> right: `item-model: 'pack:key'` in the file (rule
  `gui-item-model-not-custom-model-data`).
- Wrong: reading input slots with `inventory.getItem(i)` then clearing -> right: `view.takeInputs('S')` (rule
  `gui-take-inputs-atomically`).
- Wrong: editing a menu file and expecting open views to change -> right: `gui.reload()`; views redraw on the next refresh.
- Wrong: `<page>` in a title while a mouse pointer matters -> a changed title re-opens the inventory (see limits).

## Limits

- Fixed 9-column grid, one-line title, hover and click only on slots. Animation = item or title swaps via `update-every`.
- Paper 1.21.11 cannot change an open title: a title (or shape) that changes re-opens a new inventory carrying the input
  items and the cursor, which re-centres the mouse pointer. Keep live values out of titles where that matters.
- Chest, hopper and dispenser only; no anvil (use a dialog).
- Not covered by the library's tests (needs a real client or Folia): vanilla carrying out an allowed click, the cursor
  surviving a real re-open, quit/death order.

Design: `docs/design/gui.md`.
