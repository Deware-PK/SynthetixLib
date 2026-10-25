# P2-35 Command cooldowns: reloadable duration, per-player reset, start only on success
Status: todo · Approved: no (proposed by eval cooldown-command run 1) · Model: sonnet · Effort: medium
Module: synthetixlib-command · Depends on: P3-06 · Design: command.md §5

## Why
The eval agent wrote its own cooldown map: `.cooldown(Duration)` is fixed at registration (config reload cannot change
it), cannot be reset for one player (admin `/kit reset <player>`), and the task needed "no wait when the kit did not fit".

## Spec (draft, owner to approve)
- `.cooldown(Function<CommandSender, Duration>)` (or `Supplier<Duration>`) read at each use, so a reloaded config applies.
- `CommandService.cooldowns()` → `remaining(player, commandPath)`, `reset(player, commandPath)`, `reset(player)`.
- The cooldown starts only when the handler finished without `ctx.fail(...)` (check what 0.3.0 does and document it);
  handler can call `ctx.skipCooldown()` for "done, but do not start the wait".
- `<time>` formatting helper for "1m 12s" with locale words (config `Durations` has parsing; add a formatter).
