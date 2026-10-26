package com.github.dewarepk.synthetixlib.selftest;

import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Collects a product's {@link SelfTest}s and runs them inside a real Paper server, but only when the JVM was started
 * with {@code -Dsynthetix.selftest=true} (the Gradle {@code smokeTest} task does that). On a live server the property
 * is never set, so nothing is registered, written or stopped.
 *
 * <pre>{@code
 * // onEnable, after SynthetixLib.init(this)
 * SelfTests.of(lib)
 *         .add(SelfTest.sync("myplugin.lang-loaded", () -> Checks.isTrue(lang.get() != null, "lang")))
 *         .addAll(OrderSelfTests.all(lib, orders))
 *         .runIfRequested();
 * }</pre>
 *
 * <p>When requested, the tests run one by one on the global region thread after {@code ServerLoadEvent}, each with its
 * own timeout and all together under a cap ({@code -Dsynthetix.selftest.cap=<seconds>}, default 120). The result goes
 * to the file named by {@code -Dsynthetix.selftest.output} (default {@code selftest-result.json} in the plugin's data
 * folder), one line per failed test is logged, then the server stops.
 *
 * <p>Never set the property on a live server: it stops the server when the run ends.
 */
public final class SelfTests {

    /** JVM property that turns the run on. */
    public static final String ENABLE_PROPERTY = "synthetix.selftest";
    /** JVM property: path of the result JSON. */
    public static final String OUTPUT_PROPERTY = "synthetix.selftest.output";
    /** JVM property: whole-run cap in seconds. */
    public static final String CAP_PROPERTY = "synthetix.selftest.cap";
    /** The cap when {@link #CAP_PROPERTY} is not set. */
    public static final long DEFAULT_CAP_SECONDS = 120;

    private final SelfTestHost host;
    private final List<Supplier<? extends Collection<SelfTest>>> sources = new ArrayList<>();
    private @Nullable Runnable before;

    SelfTests(final SelfTestHost host) {
        this.host = host;
    }

    /**
     * Starts a collection for one plugin. Call in {@code onEnable}, after {@code SynthetixLib.init}.
     *
     * @param lib the plugin's library instance
     * @return a new builder
     */
    public static SelfTests of(final SynthetixLib lib) {
        Objects.requireNonNull(lib, "lib");
        return new SelfTests(new ServerHost(lib));
    }

    /**
     * @param test one test; they run in the order they were added
     * @return this
     */
    public SelfTests add(final SelfTest test) {
        Objects.requireNonNull(test, "test");
        sources.add(() -> List.of(test));
        return this;
    }

    /**
     * @param tests several tests, in order
     * @return this
     */
    public SelfTests addAll(final Collection<SelfTest> tests) {
        Objects.requireNonNull(tests, "tests");
        final List<SelfTest> copy = List.copyOf(tests);
        sources.add(() -> copy);
        return this;
    }

    /**
     * Like {@link #addAll(Collection)}, but {@code tests} is called when the run starts (after the server loaded), not
     * now. Use it for tests whose set-up needs worlds or other plugins, which do not exist yet in {@code onEnable}.
     *
     * @param tests builds the tests; called once, on the global region thread
     * @return this
     */
    public SelfTests addLater(final Supplier<? extends Collection<SelfTest>> tests) {
        Objects.requireNonNull(tests, "tests");
        sources.add(tests);
        return this;
    }

    /**
     * Set-up that runs once before the first test (and before {@link #addLater} suppliers are called), on the global
     * region thread. If it throws, the run ends with a failed {@code selftest.before} and no test runs.
     *
     * @param setup the set-up
     * @return this
     */
    public SelfTests before(final Runnable setup) {
        this.before = Objects.requireNonNull(setup, "setup");
        return this;
    }

    /**
     * Does nothing unless {@code -Dsynthetix.selftest=true}. Then it waits for {@code ServerLoadEvent} (once), runs the
     * tests, writes the result and stops the server.
     */
    public void runIfRequested() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        final long cap = Math.max(1, Long.getLong(CAP_PROPERTY, DEFAULT_CAP_SECONDS));
        final List<Supplier<? extends Collection<SelfTest>>> collected = List.copyOf(sources);
        final @Nullable Runnable setup = before;
        host.info("[selftest] self-test mode: tests run after the server loaded, then the server stops");
        host.onServerLoad(() -> {
            final List<SelfTest> tests = new ArrayList<>();
            // Set-up first, then the lazily built tests: a failure in either is the failed test "selftest.before".
            new SelfTestRun(host, () -> {
                if (setup != null) {
                    setup.run();
                }
                for (final Supplier<? extends Collection<SelfTest>> source : collected) {
                    tests.addAll(source.get());
                }
            }, tests, cap).start();
        });
    }
}
