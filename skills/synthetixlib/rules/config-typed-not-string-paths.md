---
title: Config is a typed record, never string paths
impact: CRITICAL
tags: config, yaml
---

## Config is a typed record, never string paths

String paths (`getInt("min-bid")`) fail silently when mistyped, have no defaults in one place and cannot be validated. A public record maps to YAML with defaults, comments, ranges and migrations, and gives you an immutable snapshot.

**❌ Incorrect**

```java
int minBid = plugin.getConfig().getInt("min-bid", 100);
String msg = plugin.getConfig().getString("messages.outbid");
```

**✅ Correct**

```java snippet=config-settings-record
@Comment("SynthetixExample settings")
public record Settings(@Comment("Give every joining player a demo ticket") boolean welcomeTicket,
                       @Key("default-airdrop")
                       @Comment("New airdrops start from this one (the name is replaced by the id)")
                       Airdrop defaultAirdrop,
                       @Comment("Where the airdrop claims are stored (sqlite or mysql); a restart applies a change")
                       DatabaseSettings database,
                       @Comment("Dialogs: how long their buttons work; debug logs the thread of the first click at INFO")
                       DialogSettings dialog) {

    public static Settings defaults() {
        return new Settings(true, Airdrop.standard(),
                DatabaseSettings.sqlite("example.db").withTablePrefix("ex_"),
                new DialogSettings(Duration.ofMinutes(5), true));
    }
}
```

```java snippet=config-wiring
final Configs configs = Configs.create(lib);
DatabaseSettings.register(configs.codecs());
settings = configs.file("settings.yml", Settings.class, Settings::defaults).load();
lang = configs.locale("lang", Messages.class, Messages::defaults)
        .defaultLocale(Locale.US)
        .bundled("th_TH")
        .perPlayer(true)
        .load();
airdrops = configs.collection("airdrops", Airdrop.class, id -> settings.get().defaultAirdrop().withName(id))
        .load();
```
