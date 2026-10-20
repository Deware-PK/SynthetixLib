package com.github.dewarepk.synthetixlib.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.parallel.Isolated;

/** The MySQL tests are skipped, not failed, when the Gradle properties are missing (CI). */
@Isolated
class MysqlExtensionTest {

    private static final String[] NAMES = {"mysqlUrl", "mysqlUser", "mysqlPassword"};

    private static ConditionEvaluationResult evaluate(final Map<String, String> properties) {
        final String[] saved = new String[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            saved[i] = System.getProperty(NAMES[i]);
            System.clearProperty(NAMES[i]);
        }
        try {
            properties.forEach(System::setProperty);
            return new MysqlExtension().evaluateExecutionCondition(null);
        } finally {
            for (int i = 0; i < NAMES.length; i++) {
                if (saved[i] == null) {
                    System.clearProperty(NAMES[i]);
                } else {
                    System.setProperty(NAMES[i], saved[i]);
                }
            }
        }
    }

    @Test
    void withoutThePropertiesTheClassIsSkipped() {
        final ConditionEvaluationResult result = evaluate(Map.of());

        assertTrue(result.isDisabled());
        assertEquals("mysqlUrl, mysqlUser and mysqlPassword are not set", result.getReason().orElseThrow());
    }

    @Test
    void oneMissingPropertyIsStillSkipped() {
        assertTrue(evaluate(Map.of("mysqlUrl", "jdbc:mysql://h/d", "mysqlUser", "u")).isDisabled());
    }

    @Test
    void withAllThreeTheClassRuns() {
        assertFalse(evaluate(Map.of("mysqlUrl", "jdbc:mysql://h/d", "mysqlUser", "u", "mysqlPassword", "p")).isDisabled());
    }
}
