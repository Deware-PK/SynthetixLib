# P2-34 Dialog: code-side validation hook for submit (config-driven limits)
Status: todo · Approved: no (proposed by eval auction-lite run 1) · Model: sonnet · Effort: medium
Module: synthetixlib-dialog · Depends on: P3-06 · Design: dialog.md §3, §4

## Why
Limits that come from the plugin's config (min/max price, max lots) cannot be file limits or `@Range`. The eval agent
re-built the "re-show with error + typed values" flow by hand with `next(dialog, ctxWithError)`.

## Spec (draft, owner to approve)
- `submit(name, Form.class, handler).validate((form, click) -> Optional<FieldError>)` or a `Validator` param:
  `FieldError.of("price", "<lang key or MiniMessage>", tagResolvers…)`; non-empty → the library re-shows exactly like a
  file-limit failure (errors line, typed values kept, fresh callbacks), handler not called.
- Runs on the player's thread before the handler; for async checks `validateAsync`.
- Input limits from code (eval shop run 1): `.inputRange(key, view -> range)` / `.maxLength(key, view -> n)` override
  the file values per showing, so a config value (max per purchase) and the slider never disagree.
- Optional: text input `kind: integer|decimal` so a wide-range number can be typed (no slider) and lands in a long/double
  component with server parsing + the same error flow.
