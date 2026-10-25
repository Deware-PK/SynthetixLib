# new-plugin

Creates a Paper plugin project from `templates/consumer-plugin/` (it builds as is).

```
pwsh new-plugin.ps1 -Target ../auction -Package com.acme.auction -Name Auction [-Id auction]
bash new-plugin.sh ../auction com.acme.auction Auction [auction]
```

- `Package`: lower-case, at least two parts; refused when it starts with `com.github.dewarepk.synthetixlib`.
- `Name`: plugin name and class prefix (`Auction`, `AuctionCommand`, `AuctionLoader`...); `Id`: plugin id, command name,
  jar name (default: the lower-cased name).
- The target folder must not exist or must be empty. Build output of the template is not copied, and the
  `@snippet:` / `@end` marker lines (docs-sync comments of the SynthetixLib repo) are removed.
- Next: `cd` into the folder and run `./gradlew build`. Add `-PsynthetixLocal=true` to use a SynthetixLib built on this
  machine (`publishToMavenLocal`).
