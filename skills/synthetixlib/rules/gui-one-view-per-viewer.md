---
title: One menu definition, one view per viewer
impact: CRITICAL
tags: gui, state, folia
---

## One menu definition, one view per viewer

SnapPlug shared one viewer field between players, so two players in the same menu saw and changed each other's page and context. `MenuType` is immutable and shared; `open` creates a `MenuView` with its own inventory, page, context and state. Per-viewer data goes into the context or PDC.

**❌ Incorrect**

```java
private Player viewer;          // field of a menu class shared by everyone
private int page;
void open(Player p) { this.viewer = p; this.page = 0; ... }
```

**✅ Correct**

```java snippet=gui-context-menu
final MenuType<Entry> airdropMenu = gui.menu("airdrop").<Entry>withContext()
        .placeholder("name", view -> Component.text(view.context().airdrop().name()))
        .placeholder("interval", view -> Component.text(view.context().airdrop().interval().toString()))
        .placeholder("players", view -> Component.text(view.context().airdrop().minPlayers()))
        .placeholder("rewards", view -> Component.text(view.context().airdrop().rewards().size()))
        .build();
```
