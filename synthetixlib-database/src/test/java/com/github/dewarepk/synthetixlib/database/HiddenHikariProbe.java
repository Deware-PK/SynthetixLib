package com.github.dewarepk.synthetixlib.database;

import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Loaded by a class loader that cannot see HikariCP (see {@code SqliteDatabaseTest}); returns the message that
 * opening a database gives then.
 */
public final class HiddenHikariProbe implements Supplier<String> {

    private final Path folder;

    public HiddenHikariProbe(final Path folder) {
        this.folder = folder;
    }

    @Override
    public String get() {
        try {
            DatabaseImpl.open("Probe", folder, Logger.getLogger("probe"), DatabaseSettings.sqlite("probe.db")).close();
            return "opened without HikariCP";
        } catch (final IllegalStateException e) {
            return e.getMessage();
        }
    }
}
