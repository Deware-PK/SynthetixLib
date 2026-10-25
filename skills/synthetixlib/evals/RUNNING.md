# Running an eval

An eval checks that a **fresh agent that only has the skill** can build a correct plugin. Run each of the three evals
(`auction-lite.md`, `shop.md`, `cooldown-command.md`) in its own folder. The builder must not see this repo's source,
docs or the checklist: that is why it works in an empty folder outside the repo.

## Before v0.8.0 is on JitPack (local checkout)

The first evals run before the ship, so there is no JitPack build and the skill is not on GitHub yet. Use the checkout.

1. In the SynthetixLib checkout: `gradlew.bat publishToMavenLocal` (installs `0.8.0-SNAPSHOT` into `~/.m2`).
   Note the `version=` in the root `gradle.properties`: that is the `synthetixLocalVersion` for the eval.
2. Make the eval folder and copy the skill into it (PowerShell):

   ```powershell
   $eval = 'D:\Minecraft\Evals\auction-lite'          # or shop / cooldown-command
   New-Item -ItemType Directory -Force "$eval\.claude\skills" | Out-Null
   Copy-Item -Recurse -Force 'D:\Minecraft\Plugins\SynthetixLib\skills\synthetixlib' "$eval\.claude\skills\synthetixlib"
   Remove-Item -Recurse -Force "$eval\.claude\skills\synthetixlib\evals"     # the builder must not read the checklists
   ```

3. Open a **new Claude Code session with Sonnet** in that folder (`cd $eval; claude --model sonnet`). Fresh session, no
   memory, no extra instructions.
4. Paste the block from the `## Prompt` section of the eval file, then add this one line at the end (replace the version
   with the root `version=` from step 1):

   ```text
   Build with `gradlew build -PsynthetixLocal=true -PsynthetixLocalVersion=0.8.0-SNAPSHOT` (SynthetixLib is in mavenLocal, not on JitPack yet).
   ```

5. Give **no other help**. If the agent asks a question, answer "decide yourself, keep it simple". Let it finish (it
   should report a green build). Note anything you had to do by hand.
6. Close the session. Start a **second** session in the same folder and run the reviewer (`REVIEW.md`).
7. Paste the review into `RESULTS.md` (row for the eval, lib version, score, gaps).

Do not fix the generated plugin by hand: the result must show what the skill alone produces.

## After the ship (v0.8.0 on JitPack, skill on GitHub)

Same steps, but step 1 is skipped and step 2 installs the skill the way a buyer does, in the eval folder:

```powershell
cd D:\Minecraft\Evals\auction-lite
npx skills add Deware-PK/SynthetixLib
```

Remove the `evals` directory from the installed skill if the CLI copied it, and skip the extra line in step 4 (the agent
builds with plain `gradlew build` against JitPack). A JitPack first build can take several minutes.

## Re-run after a doc fix

Re-run only the failed evals, in a new empty folder (`D:\Minecraft\Evals\<eval>-2`), with the updated skill copied in.
Add a new row to `RESULTS.md`; keep the old one.
