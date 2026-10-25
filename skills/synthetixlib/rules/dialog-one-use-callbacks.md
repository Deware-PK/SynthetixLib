---
title: Buttons are one-use; do not cache dialogs
impact: HIGH
tags: dialog, dupe
---

## Buttons are one-use; do not cache dialogs

Every `open` / `reopen` builds a new Paper dialog with fresh one-use callbacks, so a double click, a replayed packet or a stale dialog after a reload does nothing (no double sell). Keep the `DialogType`, never a Paper `Dialog` or its callbacks. Registry dialogs have no one-use callback, so their handlers must check state.

**❌ Incorrect**

```java
private Dialog cached;   // built once with Dialog.create(...) and shown again
```

**✅ Correct**

```java snippet=dialog-more
final DialogType<String> search = dialogs.dialog("search").<String>withContext()
        .input("query", view -> view.context())                              // initial value from code
        .action("clear", click -> click.view().reopen())                     // a button without a form
        .submitAsync("search", SearchForm.class, (form, click) -> {          // slow: runs async
            final String found = form.query() + (form.exact() ? " (exact)" : "");
            click.view().player().sendMessage(found);
        })
        .onClose(view -> lib.plugin().getLogger().info(view.player().getName() + " closed it"))
        .build();
search.open(player, "last query");                                           // any thread
```
