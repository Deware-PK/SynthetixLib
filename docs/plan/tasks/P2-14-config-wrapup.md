# P2-14 Config wrap-up: example demo, docs, publish
Status: done · Approved: yes · Model: sonnet · Effort: low
Module: synthetixlib-config, synthetixlib-example · Depends on: P2-10..P2-13, P2-11a

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/config.md and docs/plan/tasks/P2-14-config-wrapup.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- Example plugin demo (outside self-test mode): `settings.yml` (Settings record with a nested default airdrop),
  `airdrops/` collection with a sealed `Reward` (item / command), `lang/en_US.yml` + bundled `lang/th_TH.yml`.
  No commands yet (command module comes later): on enable log loaded airdrop ids; create `example` airdrop if none.
- Carry-over fixes from the P2-12/P2-13 review (each with a unit test):
  - `LocaleHandle.get(Locale, ...)` / `getLines(Locale, ...)` ignore the given locale when `perPlayer` is false.
    An explicit `Locale` argument is always honoured; `perPlayer` only decides how an `Audience` is resolved.
  - `ConfigCollection.create(id)` on a case-insensitive file system (Windows) when e.g. `MyDrop.yml` exists:
    today it says "exists but could not be loaded". Detect a file whose name equals the id ignoring case and
    report "a file named MyDrop.yml already exists (ids are lower case); rename it to mydrop.yml".
    The scan already logs `MyDrop.yml` as an invalid id; make that message suggest the lower-case name too.
- Javadoc with snippet on every public type; package-info for every package.
- config.md Status → "Implemented (0.2.0)" + deviations; ROADMAP row 2 → done; module AGENTS.md final.
- Version → `0.2.0-SNAPSHOT`; `publishToMavenLocal`.

## Acceptance criteria
- [x] demo files generated on `runServer` look clean and commented (paste settings.yml + one airdrop file in Result)
- [x] `gradlew.bat check smokeTest publishToMavenLocal` green

## Verify
gradlew.bat check smokeTest publishToMavenLocal

## Result
Ran `gradlew.bat check smokeTest publishToMavenLocal`: green (Paper 1.21.11 and 26.3 PASS; `0.2.0-SNAPSHOT` of core + config in
mavenLocal). Not committed.
- **Demo** (`synthetixlib-example`, package `...synthetixexample.demo`, only runs outside self-test mode): `Settings`
  (`settings.yml`, nested `default-airdrop`), `Airdrop` + sealed `Reward` (`ItemReward` / `CommandReward`), `Messages`
  (`lang/en_US.yml` generated, `lang/th_TH.yml` bundled from the jar). On enable: creates the `example` airdrop if the
  folder is empty, prints "Loaded N airdrop(s): ids" to the console through the locale, logs collection changes. The
  join handler now uses `lang` and `settings.welcomeTicket`. Checked on a fresh `runServer`; the first try showed my own
  `th_TH.yml` was invalid YAML (`: ` in an unquoted value) - quoted, and the library reported it correctly.
- **Fix 1** `LocaleHandle.get(Locale)` / `getLines(Locale)` honour the locale also when `perPlayer` is false (tests:
  `anExplicitLocaleIsHonouredEvenWithoutPerPlayer`; `withoutPerPlayerEveryoneGetsTheDefault` now only asserts audiences).
- **Fix 2** `ConfigCollection.create(id)` throws "a file named MyDrop.yml already exists in drops (ids are lower case); rename it
  to mydrop.yml" for a file that differs from the id only in case (checked on every OS, not only Windows, so behaviour is
  the same everywhere); the scan message for such a file ends with "; rename it to mydrop.yml" (no hint if the lower-case
  name is invalid too, e.g. `Bad Name.yml`). Tests: `aFileWithUpperCaseLettersIsReportedWithTheNameToRenameItTo`,
  `createExplainsAFileThatDiffersOnlyInLetterCase`, `createStillSaysBrokenForAFileOfTheSameName`.
- Docs: `config.md` Status -> Implemented (0.2.0) + new section 10 "Deviations" (notably: no `Validated` interface,
  `configs.codecs().register` instead of `configs.codec`); ROADMAP row 2 done; module AGENTS.md updated; snippet added to
  `Problem` (the other public types already had one or are `@ApiStatus.Internal`); every package has a `package-info`.
- Version `0.2.0-SNAPSHOT` in `gradle.properties`.

Generated `settings.yml` (fresh `runServer`):
```yaml
# SynthetixExample settings

config-version: 1
# Give every joining player a demo ticket
welcome-ticket: true
# New airdrops start from this one (the name is replaced by the id)
default-airdrop:
  # Name shown to players
  name: Airdrop
  # Time between two drops, e.g. 30m or 1h30m
  interval: 30m
  # The drop waits until this many players are online
  min-players: 1
  # What a drop gives. Types: item, command
  # A command can use %player%
  rewards:
  - type: item
    material: diamond
    amount: 3
    chance: 25
  - type: item
    material: golden_apple
    amount: 1
    chance: 50
  - type: command
    command: say %player% found an airdrop!
```
Generated `airdrops/example.yml`:
```yaml
# An airdrop. Edit this file and reload, or let a command change it.

config-version: 1
# Name shown to players
name: example
# Time between two drops, e.g. 30m or 1h30m
interval: 30m
# The drop waits until this many players are online
min-players: 1
# What a drop gives. Types: item, command
# A command can use %player%
rewards:
- type: item
  material: diamond
  amount: 3
  chance: 25
- type: item
  material: golden_apple
  amount: 1
  chance: 50
- type: command
  command: say %player% found an airdrop!
```
