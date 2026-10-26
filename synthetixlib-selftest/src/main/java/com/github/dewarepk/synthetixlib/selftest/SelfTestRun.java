package com.github.dewarepk.synthetixlib.selftest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * One sequential run: the optional setup, every test in order, then the result file and the shutdown. All state is
 * only touched on the global region thread (futures hop back through {@link SelfTestHost#runGlobal}).
 */
final class SelfTestRun {

    private final SelfTestHost host;
    private final @Nullable Runnable before;
    private final List<SelfTest> tests;
    private final long capSeconds;
    private final List<SelfTestResult> results = new ArrayList<>();
    private int index;
    private boolean finished;

    SelfTestRun(final SelfTestHost host, final @Nullable Runnable before, final List<SelfTest> tests,
                final long capSeconds) {
        this.host = host;
        this.before = before;
        this.tests = tests;
        this.capSeconds = capSeconds;
    }

    /** Call on the global region thread. */
    void start() {
        host.runGlobalLater(capSeconds * 20L, this::onCap);
        if (before != null) {
            final long start = System.nanoTime();
            try {
                before.run();
            } catch (final Throwable t) {
                record("selftest.before", start, t);
                finish();
                return;
            }
        }
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
            record(test.name(), start, t);
            advance(current);
            return;
        }
        future.orTimeout(test.timeout().toNanos(), TimeUnit.NANOSECONDS).whenComplete((value, error) ->
                // Completion may happen on any thread: hop back before touching state.
                host.runGlobal(() -> {
                    if (finished || index != current) {
                        return;
                    }
                    record(test.name(), start, error);
                    advance(current);
                }));
    }

    private void advance(final int current) {
        index = current + 1;
        host.runGlobal(this::next);
    }

    private void onCap() {
        if (finished) {
            return;
        }
        host.severe("[selftest] global cap of " + capSeconds + " s reached", null);
        final String capped = "global run cap (" + capSeconds + " s) reached";
        if (index < tests.size()) {
            record(tests.get(index).name(), System.nanoTime() - TimeUnit.SECONDS.toNanos(capSeconds),
                    new IllegalStateException(capped));
            index++;
        }
        for (; index < tests.size(); index++) {
            record(tests.get(index).name(), System.nanoTime(), new IllegalStateException("not run: " + capped));
        }
        finish();
    }

    private void record(final String name, final long startNanos, final @Nullable Throwable raw) {
        final long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        final Throwable error = raw instanceof CompletionException && raw.getCause() != null ? raw.getCause() : raw;
        if (error == null) {
            host.info("[selftest] PASS " + name + " (" + durationMs + " ms)");
            results.add(SelfTestResult.passed(name, durationMs));
        } else if (error instanceof SelfTest.Skipped) {
            host.info("[selftest] SKIP " + name + ": " + error.getMessage());
            results.add(SelfTestResult.skipped(name, durationMs, error.getMessage()));
        } else {
            host.severe("[selftest] FAIL " + name, error);
            results.add(SelfTestResult.failed(name, durationMs,
                    error.getClass().getSimpleName() + ": " + error.getMessage()));
        }
    }

    private void finish() {
        finished = true;
        final String json = SelfTestResult.toJson(host.minecraftVersion(), results);
        host.writeResult(json);
        final long failed = results.stream().filter(SelfTestResult::failed).count();
        final long skipped = results.stream().filter(SelfTestResult::skipped).count();
        host.info("[selftest] " + (failed == 0 ? "PASS" : "FAIL") + " (" + (results.size() - failed - skipped) + "/"
                + results.size() + " passed, " + skipped + " skipped)");
        host.runGlobal(host::shutdown);
    }
}
