package com.github.dewarepk.synthetixlib.selftest;

import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.jspecify.annotations.Nullable;

/** The real {@link SelfTestHost}: Paper events, the lib scheduler and the plugin logger. */
final class ServerHost implements SelfTestHost {

    private final SynthetixLib lib;

    ServerHost(final SynthetixLib lib) {
        this.lib = lib;
    }

    @Override
    public void onServerLoad(final Runnable action) {
        lib.plugin().getServer().getPluginManager().registerEvents(new LoadListener(action), lib.plugin());
    }

    @Override
    public void runGlobal(final Runnable action) {
        lib.scheduler().global().run(action);
    }

    @Override
    public void runGlobalLater(final long delayTicks, final Runnable action) {
        lib.scheduler().global().runLater(delayTicks, action);
    }

    @Override
    public void info(final String message) {
        lib.plugin().getLogger().info(message);
    }

    @Override
    public void severe(final String message, final @Nullable Throwable error) {
        lib.plugin().getLogger().log(Level.SEVERE, message, error);
    }

    @Override
    public String minecraftVersion() {
        return lib.plugin().getServer().getMinecraftVersion();
    }

    @Override
    public void writeResult(final String json) {
        final String configured = System.getProperty(SelfTests.OUTPUT_PROPERTY);
        final Path path = configured != null
                ? Path.of(configured)
                : lib.plugin().getDataFolder().toPath().resolve("selftest-result.json");
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            severe("[selftest] could not write " + path, e);
        }
    }

    @Override
    public void shutdown() {
        lib.plugin().getServer().shutdown();
    }

    /** Fires the action once, on the first {@link ServerLoadEvent} (a /reload fires it again). */
    private static final class LoadListener implements Listener {

        private final Runnable action;
        private boolean ran;

        private LoadListener(final Runnable action) {
            this.action = action;
        }

        @EventHandler
        public void onServerLoad(final ServerLoadEvent event) {
            if (ran) {
                return;
            }
            ran = true;
            action.run();
        }
    }
}
