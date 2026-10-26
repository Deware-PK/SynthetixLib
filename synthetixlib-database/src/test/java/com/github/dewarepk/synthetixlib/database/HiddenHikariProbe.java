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

    private final boolean standalone;

    public HiddenHikariProbe(final Path folder) {
        this(folder, false);
    }

    public HiddenHikariProbe(final Path folder, final boolean standalone) {
        this.folder = folder;
        this.standalone = standalone;
    }

    @Override
    public String get() {
        try {
            final DatabaseSettings settings = DatabaseSettings.sqlite("probe.db");
            if (standalone) {
                Database.standalone("Probe", folder, Logger.getLogger("probe"), settings).close();
            } else {
                DatabaseImpl.open("Probe", folder, Logger.getLogger("probe"), settings).close();
            }
            return "opened without HikariCP";
        } catch (final IllegalStateException e) {
            return e.getMessage();
        }
    }
}
