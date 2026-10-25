---
title: Keep comments on the default copy
impact: MEDIUM
tags: config, yaml
---

## Keep comments on the default copy

Server owners learn the options from the comments in the file. Put `@Comment` on record components and let the library write them; never copy defaults from a resource that was stripped of comments.

**❌ Incorrect**

```java
public record Airdrop(String name, Duration interval, int minPlayers) {}   // no explanation in the YAML
```

**✅ Correct**

```java snippet=config-airdrop-record
@Comment("An airdrop. Edit this file and reload, or let a command change it.")
public record Airdrop(@Comment("Name shown to players") String name,
                      @Comment("Time between two drops, e.g. 30m or 1h30m") Duration interval,
                      @Comment("The drop waits until this many players are online") @Range(min = 0) int minPlayers,
                      @Comment({"What a drop gives. Types: item, command", "A command can use %player%"})
                      @NotEmpty List<Reward> rewards) {

    /** A starting point for every new airdrop; the plugin's settings may replace it. */
    public static Airdrop standard() {
        return new Airdrop("Airdrop", Duration.ofMinutes(30), 1, List.of(
                new ItemReward(Material.DIAMOND, 3, 25),
                new ItemReward(Material.GOLDEN_APPLE, 1, 50),
                new CommandReward("say %player% found an airdrop!")));
    }

    /** @return a copy with another name */
    public Airdrop withName(final String name) {
        return new Airdrop(name, interval, minPlayers, rewards);
    }
}
```
