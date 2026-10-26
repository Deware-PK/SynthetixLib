package com.github.dewarepk.synthetixlib.selftest;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Outcome of one test, and the JSON of a whole run (read by the Gradle {@code smokeTest} task):
 * <pre>{"status":"PASS","minecraftVersion":"1.21.11","total":3,"failed":0,"skipped":1,"tests":[
 *   {"name":"a","passed":true,"durationMs":4},
 *   {"name":"b","passed":false,"durationMs":9,"error":"AssertionError: ..."},
 *   {"name":"c","passed":true,"skipped":true,"durationMs":0,"reason":"needs an online player"}]}</pre>
 * {@code status} is the first key, so a build can match {@code {"status":"PASS"} } at the start of the file.
 */
record SelfTestResult(String name, boolean failed, boolean skipped, long durationMs, @Nullable String message) {

    static SelfTestResult passed(final String name, final long durationMs) {
        return new SelfTestResult(name, false, false, durationMs, null);
    }

    static SelfTestResult skipped(final String name, final long durationMs, final @Nullable String reason) {
        return new SelfTestResult(name, false, true, durationMs, reason == null ? "skipped" : reason);
    }

    static SelfTestResult failed(final String name, final long durationMs, final String error) {
        return new SelfTestResult(name, true, false, durationMs, error);
    }

    static String toJson(final String minecraftVersion, final List<SelfTestResult> results) {
        final long failed = results.stream().filter(SelfTestResult::failed).count();
        final long skipped = results.stream().filter(SelfTestResult::skipped).count();
        final List<String> entries = new ArrayList<>(results.size());
        for (final SelfTestResult result : results) {
            entries.add(result.toJson());
        }
        return "{\"status\":\"" + (failed == 0 ? "PASS" : "FAIL") + "\""
                + ",\"minecraftVersion\":" + quote(minecraftVersion)
                + ",\"total\":" + results.size()
                + ",\"failed\":" + failed
                + ",\"skipped\":" + skipped
                + ",\"tests\":[" + String.join(",", entries) + "]}";
    }

    private String toJson() {
        return "{\"name\":" + quote(name)
                + ",\"passed\":" + !failed
                + (skipped ? ",\"skipped\":true" : "")
                + ",\"durationMs\":" + durationMs
                + (message == null ? "" : (failed ? ",\"error\":" : ",\"reason\":") + quote(message))
                + '}';
    }

    private static String quote(final @Nullable String value) {
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
