package com.github.dewarepk.synthetixexample.selftest;

import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Runs every registered self-test once the server finished loading, writes
 * {@code selftest-result.json} (read by the Gradle {@code smokeTest} task) and stops the server.
 * Tests run sequentially in registration order; the server thread is never blocked.
 */
public final class SelfTestTrigger implements Listener {

    /** Hard cap for the whole run, in ticks (60 s). */
    private static final long GLOBAL_CAP_TICKS = 60L * 20L;

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
        tests.addAll(HarnessSelfTests.all(plugin));
        tests.addAll(CoreSelfTests.all(plugin, lib));
        tests.addAll(CoreSchedulerSelfTests.all(plugin, lib));
        tests.addAll(CorePdcSelfTests.all(plugin, lib));
        tests.addAll(CoreItemSelfTests.all(plugin, lib));
        tests.addAll(ConfigSelfTests.all(plugin));
        tests.addAll(ConfigBukkitSelfTests.all(plugin, lib));
        tests.addAll(ConfigFileSelfTests.all(plugin, lib));
        // one line per module as modules are added

        new Run(tests).start();
    }

    /** One sequential run. All fields are only touched on the global region thread. */
    private final class Run {
        private final List<SelfTest> tests;
        private final List<String> results = new ArrayList<>();
        private int failed;
        private int index;
        private boolean finished;

        private Run(final List<SelfTest> tests) {
            this.tests = tests;
        }

        void start() {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> onCap(), GLOBAL_CAP_TICKS);
            next();
        }

        private void next() {
            if (finished) {
                return;
            }
            if (index >= tests.size()) {
                finish();
                return;
            }
            final SelfTest test = tests.get(index);
            final int current = index;
            final long start = System.nanoTime();

            final CompletableFuture<?> future;
            try {
                future = test.body().get();
            } catch (final Throwable t) {
                record(test, start, t);
                advance(current);
                return;
            }
            future.orTimeout(test.timeout().toNanos(), TimeUnit.NANOSECONDS).whenComplete((value, error) ->
                    // Completion may happen on any thread: hop back before touching state.
                    Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                        if (finished || index != current) {
                            return;
                        }
                        record(test, start, error);
                        advance(current);
                    }));
        }

        private void advance(final int current) {
            index = current + 1;
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> next());
        }

        private void onCap() {
            if (finished) {
                return;
            }
            plugin.getLogger().severe("[selftest] global cap of 60 s reached");
            if (index < tests.size()) {
                final SelfTest stuck = tests.get(index);
                record(stuck, System.nanoTime() - TimeUnit.SECONDS.toNanos(60),
                        new IllegalStateException("global run cap (60 s) reached"));
                index++;
            }
            for (; index < tests.size(); index++) {
                record(tests.get(index), System.nanoTime(),
                        new IllegalStateException("not run: global run cap (60 s) reached"));
            }
            finish();
        }

        private void record(final SelfTest test, final long startNanos, final Throwable raw) {
            final long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            final Throwable error = raw instanceof CompletionException && raw.getCause() != null
                    ? raw.getCause() : raw;
            String message = null;
            if (error == null) {
                plugin.getLogger().info("[selftest] PASS " + test.name() + " (" + durationMs + " ms)");
            } else {
                failed++;
                message = error.getClass().getSimpleName() + ": " + error.getMessage();
                plugin.getLogger().log(Level.SEVERE, "[selftest] FAIL " + test.name(), error);
            }
            results.add("{\"name\":" + json(test.name())
                    + ",\"passed\":" + (message == null)
                    + ",\"durationMs\":" + durationMs
                    + (message == null ? "" : ",\"error\":" + json(message))
                    + '}');
        }

        private void finish() {
            finished = true;
            final String status = failed == 0 ? "PASS" : "FAIL";
            final String out = "{\"status\":\"" + status + "\""
                    + ",\"minecraftVersion\":" + json(plugin.getServer().getMinecraftVersion())
                    + ",\"total\":" + tests.size()
                    + ",\"failed\":" + failed
                    + ",\"tests\":[" + String.join(",", results) + "]}";

            write(out);
            plugin.getLogger().info("[selftest] " + status + " (" + (tests.size() - failed) + "/" + tests.size() + ")");
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> plugin.getServer().shutdown());
        }
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
