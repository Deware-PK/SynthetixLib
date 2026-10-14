package com.github.dewarepk.synthetixexample.selftest;

/**
 * One in-server check. Throw (e.g. {@link AssertionError}) to fail.
 *
 * @param name   unique id, convention {@code <module>.<feature>}, e.g. {@code core.init}
 * @param check  the check body, runs on the global region / main thread
 */
public record SelfTest(String name, Check check) {

    @FunctionalInterface
    public interface Check {
        void run() throws Exception;
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
