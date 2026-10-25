---
title: Changed a bundled menu, dialog or config file? Bump its config-version
impact: HIGH
tags: gui, dialog, config, files, upgrade
---

## Changed a bundled menu, dialog or config file? Bump its config-version

A file in the plugin jar (`menus/shop.yml`, `dialogs/sell.yml`, `config.yml`) is copied to the data folder **once** and never overwritten, because the owner edits it. So a server that updates your plugin keeps the old copy, and your change (a slider that became a `number-field`, a new button) never reaches it. The code then runs against a file of the old shape and the owner sees the old behaviour.

Whenever a release changes such a file, raise its `config-version` (the first key, a missing one means `1`). A menu or dialog file whose version is lower than the bundled one makes the library log one warning at load: `[dialogs/sell.yml] the plugin ships config-version 2, this file is 1: delete it to get the new default or update it by hand (old copy kept)`. The old copy is **not** replaced. For a config file (`configs.file(...)`) raise the `version` and add a migration for what moved or changed meaning; added keys are filled in by themselves. Mention the bump in your release notes so owners know to delete or edit the file.

New keys of the language files need nothing: every `lang/*.yml` gets the keys it lacks on load, also the entries of a `Map<String, String>` section.

**❌ Incorrect**

```yaml
# dialogs/sell.yml in release 1.4: the price was a slider, now it is typed, and the version stayed 1
config-version: 1
inputs:
  price: { type: number-field, label: '<lang:sell-price>', min: 1, max: 1000000 }
```

Servers that updated keep the 1.3 copy with the slider, and nothing says so.

**✅ Correct**

```yaml
config-version: 2        # raised with the change; a server with version 1 gets the warning above in its log
inputs:
  price: { type: number-field, label: '<lang:sell-price>', min: 1, max: 1000000 }
```
