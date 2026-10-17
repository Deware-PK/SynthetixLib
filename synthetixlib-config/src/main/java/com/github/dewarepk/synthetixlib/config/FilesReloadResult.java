package com.github.dewarepk.synthetixlib.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What a reload of several files found (a {@link ConfigCollection} or a {@link LocaleHandle}): one
 * {@link ReloadResult} per file that was read, so one broken file never hides the others.
 *
 * <pre>{@code
 * FilesReloadResult result = airdrops.reload();
 * result.errors().forEach(sender::sendMessage);       // "myairdrop: loot[2]: unknown material ..."
 * sender.sendMessage(result.changed() ? "Reloaded" : "Nothing changed");
 * }</pre>
 *
 * @param files   the result of each file read, by id (collection) or locale tag (locale), in name order
 * @param removed ids or tags that were loaded before and whose file is gone now
 */
public record FilesReloadResult(Map<String, ReloadResult> files, List<String> removed) {

    public FilesReloadResult {
        files = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(files, "files")));
        removed = List.copyOf(Objects.requireNonNull(removed, "removed"));
    }

    /** @return {@code true} if no file reported an error */
    public boolean ok() {
        return files.values().stream().allMatch(ReloadResult::ok);
    }

    /** @return {@code true} if a file changed the loaded values or a file is gone */
    public boolean changed() {
        return !removed.isEmpty() || files.values().stream().anyMatch(ReloadResult::changed);
    }

    /** @return every error as {@code id: message}, files in order */
    public List<String> errors() {
        return prefixed(true);
    }

    /** @return every warning as {@code id: message}, files in order */
    public List<String> warnings() {
        return prefixed(false);
    }

    private List<String> prefixed(final boolean errors) {
        final List<String> out = new ArrayList<>();
        for (final Map.Entry<String, ReloadResult> entry : files.entrySet()) {
            final List<String> messages = errors ? entry.getValue().errors() : entry.getValue().warnings();
            for (final String message : messages) {
                out.add(entry.getKey() + ": " + message);
            }
        }
        return out;
    }
}
