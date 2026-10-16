package com.github.dewarepk.synthetixlib.config;

import java.util.List;
import java.util.Objects;

/**
 * What a reload found. Messages read like {@code zones.spawn.radius: must be >= 1 (was 0) -> using 8}.
 *
 * <pre>{@code
 * ReloadResult result = config.reload();
 * result.errors().forEach(sender::sendMessage);
 * if (result.changed()) {
 *     sender.sendMessage("Config reloaded");
 * }
 * }</pre>
 *
 * @param errors   values that were invalid (the default is in use, the file still holds the value), or a file that
 *                 could not be read or written; empty if everything loaded cleanly
 * @param warnings things that changed nothing, e.g. keys the config does not know
 * @param changed  {@code true} if the loaded config differs from the one before
 */
public record ReloadResult(List<String> errors, List<String> warnings, boolean changed) {

    public ReloadResult {
        errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }

    /** @return {@code true} if there are no errors */
    public boolean ok() {
        return errors.isEmpty();
    }
}
