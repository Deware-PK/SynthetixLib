package com.github.dewarepk.synthetixexample.selftest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Self-tests for the test harness itself. */
final class HarnessSelfTests {

    private HarnessSelfTests() {
    }

    static List<SelfTest> all(final JavaPlugin plugin) {
        return List.of(
                // Keeps the async path of the runner covered: completes 2 ticks later.
                SelfTest.async("harness.async", () -> {
                    final CompletableFuture<Void> future = new CompletableFuture<>();
                    Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> future.complete(null), 2L);
                    return future;
                })
        );
    }
}
