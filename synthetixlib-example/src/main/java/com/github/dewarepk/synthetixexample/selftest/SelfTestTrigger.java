package com.github.dewarepk.synthetixexample.selftest;

import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Runs every registered self-test once the server finished loading, writes
 * {@code selftest-result.json} (read by the Gradle {@code smokeTest} task) and stops the server.
 */
public final class SelfTestTrigger implements Listener {

    private final JavaPlugin plugin;
    private final SynthetixLib lib;
    private boolean ran;

    public SelfTestTrigger(final JavaPlugin plugin, final SynthetixLib lib) {
        this.plugin = plugin;
        this.lib = lib;
    }

    @EventHandler
    public void onServerLoad(final ServerLoadEvent event) {
        if (ran) {
            return;
        }
        ran = true;

        final List<SelfTest> tests = new ArrayList<>();
        tests.addAll(CoreSelfTests.all(plugin, lib));
        // tests.addAll(ConfigSelfTests.all(...));  <- one line per module as modules are added

        final StringBuilder results = new StringBuilder();
        int failed = 0;
        for (final SelfTest test : tests) {
            String error = null;
            try {
                test.check().run();
                plugin.getLogger().info("[selftest] PASS " + test.name());
            } catch (final Throwable t) {
                failed++;
                error = t.getClass().getSimpleName() + ": " + t.getMessage();
                plugin.getLogger().log(Level.SEVERE, "[selftest] FAIL " + test.name(), t);
            }
            if (results.length() > 0) {
                results.append(',');
            }
            results.append("{\"name\":").append(json(test.name()))
                    .append(",\"passed\":").append(error == null)
                    .append(error == null ? "" : ",\"error\":" + json(error))
                    .append('}');
        }

        final String status = failed == 0 ? "PASS" : "FAIL";
        final String out = "{\"status\":\"" + status + "\""
                + ",\"minecraftVersion\":" + json(plugin.getServer().getMinecraftVersion())
                + ",\"total\":" + tests.size()
                + ",\"failed\":" + failed
                + ",\"tests\":[" + results + "]}";

        write(out);
        plugin.getLogger().info("[selftest] " + status + " (" + (tests.size() - failed) + "/" + tests.size() + ")");
        plugin.getServer().shutdown();
    }

    private void write(final String content) {
        final String configured = System.getProperty("synthetix.selftest.output");
        final Path path = configured != null
                ? Path.of(configured)
                : plugin.getDataFolder().toPath().resolve("selftest-result.json");
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[selftest] could not write " + path, e);
        }
    }

    private static String json(final String value) {
        if (value == null) {
            return "null";
        }
        final StringBuilder sb = new StringBuilder("\"");
        for (final char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
