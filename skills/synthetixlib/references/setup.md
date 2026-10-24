# Setup

TODO P3-0x (full consumer build, shade + relocate, PluginLoader)

## Init and close

```java snippet=hello-init
lib = SynthetixLib.init(this);
```

```java snippet=hello-close
if (lib != null) {
    lib.close();
}
```

## Example menu file

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
