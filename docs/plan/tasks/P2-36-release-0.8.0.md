# P2-36 Release 0.8.0: pom license, eval re-run, ship
Status: review · Approved: yes · Model: sonnet · Effort: low · Ship: yes (dialog 0.8.0)
Module: synthetixlib-dialog, synthetixlib-command · Depends on: P2-35 · Design: docs-skill.md §5

## Prompt (paste into Claude Code)
Read AGENTS.md and docs/plan/tasks/P2-36-release-0.8.0.md, then do Part A only and stop (Part B needs the owner).
After the owner's eval results are in, do Part C: run `gradlew.bat syncSnippets check smokeTest publishToMavenLocal`,
set Status: review, then ship: `powershell -ExecutionPolicy Bypass -File scripts/ship-module.ps1 -Module dialog -Version 0.8.0`.

## Part A (agent)
- build-logic `synthetix.library`: pom metadata (name, description, url, MIT license, developer Deware-PK, scm) for
  every published module. Version 0.8.0-SNAPSHOT (root + template `synthetixLocalVersion`), SKILL.md version line,
  CHANGELOG 0.8.0 complete. `publishToMavenLocal`, check a generated .pom has the license.
## Part B (owner)
- In game (`runServer`): `/example dialog sell` — type `1,000`, `abc`, a price above the max; error re-show keeps the text.
- Re-run evals **auction-lite** and **shop** (RUNNING.md "Re-run", folders `<eval>-2`, Sonnet medium,
  `synthetixLocalVersion=0.8.0-SNAPSHOT`) and **cooldown-command** (`-2`). The planner reviews them.
- Expected: price is a number-field with `inputRange`/`validate` (no hand-made re-show), cooldown via the library with
  `cooldowns().reset`. Results in RESULTS.md under "0.8.0".
## Part C (agent)
- Found in the owner's in-game test (0.8.0): the server kept its old `dialogs/sell.yml` (bundled files are copied once,
  never overwritten), so the price was still a slider. Fix:
  - demo `dialogs/sell.yml` → `config-version: 2` (it changed in P2-34).
  - gui + dialog: when the bundled file's `config-version` is higher than the server copy's, log one warning at load:
    `[dialogs/sell.yml] the plugin ships config-version 2, this file is 1: delete it to get the new default or update it
    by hand (old copy kept)`. No automatic overwrite. Unit test + self-test case.
  - skill: rule `files-bump-config-version.md` (HIGH): changing a bundled menu/dialog/config file → bump its
    `config-version`; mention in gui.md / dialog.md references and the template AGENTS.md. CHANGELOG 0.8.0.
- Found in the owner's in-game test (0.8.0), same root cause for language files: a new message key in a **map**
  section of `Messages` (e.g. `dialog: { sell-error-too-high: ... }`) never reaches an existing `lang/*.yml` (config.md:
  "a map entry the user removed is never added back"), so the client showed the raw key `sell-error-too-high`. Fix:
  - config, **locale files only**: on load, missing entries of map sections are added from the defaults (with the
    language's bundled text if the bundled `lang/<tag>.yml` has it, else the default locale's), like record components.
    An empty string stays the way to switch a message off. Settings/collection maps keep today's behaviour.
  - runtime: a `<lang:key>` that is in the default locale but not in the viewer's file uses the default text (warn once).
  - Unit tests (old file + new default key → added, user-emptied key stays empty) + self-test. config.md §deviations,
    skill references/config.md, CHANGELOG 0.8.0 (behaviour change, not breaking).
- Doc gap from cooldown-command run 2: references/command.md — an admin child of a command with a cooldown opts out
  with `.cooldown(Duration.ZERO)` (snippet).
- Proposal only (not in 0.8.0): `validateAsync` for checks that need the database (shop run 2) → write it in Result.
- Before shipping: README.md, references/setup.md (and any other doc) JitPack coordinates `v0.7.0` → `v0.8.0`.
- Fix any new doc gaps from the reviews, then ship (prompt above). After the push the owner opens JitPack for v0.8.0.

## Acceptance criteria
- [x] pom license, versions, CHANGELOG
- [x] 3 evals re-run ≥ 9/10 and use the new APIs (or gaps fixed)
- [x] `gradlew.bat syncSnippets check smokeTest publishToMavenLocal` green
- [ ] JitPack v0.8.0 green (owner, after the push)

## Verify
gradlew.bat syncSnippets check smokeTest publishToMavenLocal

## Result
### Result — Part A
- `build-logic/.../synthetix.library.gradle.kts`: `pom { name, description, url, MIT license, developer Deware-PK, scm }` for every published module.
- Version `0.8.0-SNAPSHOT`: `gradle.properties`, template `synthetixLocalVersion`; SKILL.md "Written for SynthetixLib 0.8.0". CHANGELOG 0.8.0 was already complete (no change).
- Ran `gradlew.bat syncSnippets check publishToMavenLocal`: BUILD SUCCESSFUL (smokeTest not part of Part A).
- Generated `~/.m2/.../synthetixlib-dialog/0.8.0-SNAPSHOT/synthetixlib-dialog-0.8.0-SNAPSHOT.pom`:
  ```xml
  <name>synthetixlib-dialog</name>
  <licenses>
    <license>
      <name>MIT License</name>
      <url>https://opensource.org/licenses/MIT</url>
    </license>
  </licenses>
  <developers><developer><id>Deware-PK</id>...</developer></developers>
  ```
- Not shipped, not committed. Next: Part B (owner).

### Result — Part C
- **Old bundled files (dialog/gui):** `DialogStore#outdated` / `MenuStore#outdated` read the `config-version` of the data-folder copy
  (missing = 1) and of the bundled one; `DialogLoader` / `MenuLoader` put the warning first in the load report
  (`[dialogs/sell.yml] the plugin ships config-version 2, this file is 1: delete it to get the new default or update it by hand
  (old copy kept)`), `SynthetixDialogs.register` (bootstrap registry) logs it too. Nothing is overwritten. Demo `sell.yml` is now
  `config-version: 2`. Side effect: the jar is opened at every load of an existing file to read the version (the gui test
  `anExistingFileIsNeverOverwrittenNorReadFromTheJar` lost its "jar not opened" assertion, renamed `...NeverOverwritten`).
  Unit tests: `DialogFilesTest` and `MenuFilesTest` (older -> warning once + file kept; missing = 1, equal / newer -> no warning).
- **Locale map entries:** internal only, no public API change. `MapCodec` fills the entries a map lacks when `LenientReport.fillMaps()`
  (new `Codecs#decodeLenientFillingMaps`, `FileConfig#fillingMaps(supplement)`, both `@Internal`); `LocaleHandle` passes the data of
  the bundled `lang/<tag>.yml` as the supplement, so a missing entry gets the language's bundled text, else the default locale's.
  `''` stays; settings files / collections are unchanged (test `aRemovedMapSectionOfOtherConfigsIsStillTheUsersToRemove`).
  A map section missing as a whole still comes from the defaults (English), as before. Tests in `LocaleHandleTest` (key added to
  old file, `''` kept, bundled text then default, second reload leaves the file alone).
- **Runtime:** `GuiService#messages` / `DialogService#messages`: a `<lang:key>` found in the default language but not in the
  viewer's file uses the default text and warns once (`views.warnOnce`). Not covered by a test: with the fill above the viewer's
  file normally has the key, and the fallback needs a real viewer; checked by reading only.
- **Self-test `upgrade.old-files`** (`UpgradeSelfTests`, registered in `SelfTestTrigger`): plants the 0.7 `dialogs/sell.yml`
  (config-version 1, slider) in the data folder (the service only reads `<data>/dialogs`, so this is not a separate temp folder:
  the original bytes are restored in `finally`) and a `selftest-upgrade/lang/en_US.yml` without `sell-error-too-high` (with a
  hand-changed `sell-title`). Checks: the config-version warning is logged (log handler on the plugin logger), the old dialog file
  is byte-identical and still the slider, the lang key is added, the owner's text stays, the key is non-empty at runtime. PASS on 1.21.11 and 26.3.
- **Skill:** rule `files-bump-config-version` (HIGH; SKILL.md, llms.txt), notes in `references/gui.md` / `dialog.md`, template
  `AGENTS.md` rule 13, `references/config.md` (locale), `docs/design/config.md` §2 + §10, CHANGELOG 0.8.0.
- **Doc gap (cooldown-command run 2):** `references/command.md` + snippet `command-cooldown-child-opt-out` (`DocExamples`):
  an admin child opts out with `.cooldown(Duration.ZERO)` (already covered by `CommandCooldownTest`).
- JitPack coordinates `v0.7.0` -> `v0.8.0`: README, `references/setup.md`, template `AGENTS.md` and `gradle.properties`, `evals/RUNNING.md`.
- Ran `gradlew.bat syncSnippets check smokeTest publishToMavenLocal`: BUILD SUCCESSFUL; smoke 88/88 on Paper 1.21.11 and 26.3.

### Proposal (not in 0.8.0): `validateAsync`
Shop run 2: "not enough money" needs the database, so the agent re-opens the dialog by hand with a `tooPoor` flag in the context.
Proposed API, next to `validate` / `validateAll`:
```java
.validateAsync("buy", BuyForm.class, (form, click) ->
        db.query(...).thenApply(balance -> balance < form.amount() * price
                ? Optional.of(FieldError.of("amount", "<lang:buy-error-poor>")) : Optional.<FieldError>empty()))
```
- Runs after the sync `validate`s pass and before the handler of `submit` / `submitAsync`; `validateAllAsync` returns `List<FieldError>`.
- The callback is called on the viewer's thread, the future may complete anywhere, the result hops back to the viewer's thread
  (like `submitAsync`). The one-use guard of the click stays closed while the check runs, so a double click runs it once;
  an error re-shows the dialog with the typed values like any other field error.
- A future that fails or exceeds a `DialogSettings` timeout: log once, tell the viewer a locale message
  (`CommandMessages`-style `DialogMessages.checkFailed`), re-show, never run the handler.
- Not a replacement for checking inside the handler's transaction (balance can change between check and handler): document it.
- Open question for the planner: whether the dialog should show a "checking..." state; Paper cannot update a shown dialog, so
  probably no (the client keeps it open until the server answers with a new one).
