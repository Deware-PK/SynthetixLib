package com.github.dewarepk.synthetixlib.config.migration;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * One step of a config migration: edits the raw data of a file of version {@code from} so that it matches
 * version {@code to}.
 *
 * @param from   the version this step reads
 * @param to     the version it produces; greater than {@code from}
 * @param action edits the data
 * @apiNote Internal: register steps with {@code ConfigFileBuilder#migrate}
 */
@org.jetbrains.annotations.ApiStatus.Internal
public record Migration(int from, int to, Consumer<ConfigTree> action) {

    public Migration {
        Objects.requireNonNull(action, "action");
        if (from < 1) {
            throw new IllegalArgumentException("Versions start at 1, got from=" + from);
        }
        if (to <= from) {
            throw new IllegalArgumentException("A migration must go to a higher version, got " + from + " to " + to);
        }
    }
}
