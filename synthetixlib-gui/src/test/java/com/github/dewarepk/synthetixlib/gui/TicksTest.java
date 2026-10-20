package com.github.dewarepk.synthetixlib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.dewarepk.synthetixlib.gui.file.Ticks;
import org.junit.jupiter.api.Test;

class TicksTest {

    @Test
    void ticksAreTakenAsTheyAre() {
        assertEquals(20, Ticks.parse("20t"));
        assertEquals(1, Ticks.parse("1T"));
        assertEquals(5, Ticks.parse(" 5t "));
    }

    @Test
    void durationsUseTheConfigSyntaxAndRoundUpToTicks() {
        assertEquals(20, Ticks.parse("1s"));
        assertEquals(10, Ticks.parse("500ms"));
        assertEquals(1, Ticks.parse("1ms"));
        assertEquals(2, Ticks.parse("51ms"));
        assertEquals(1800, Ticks.parse("1m30s"));
    }

    @Test
    void zeroNegativeUnitlessAndHugeValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Ticks.parse("0t"));
        assertThrows(IllegalArgumentException.class, () -> Ticks.parse("0s"));
        assertThrows(IllegalArgumentException.class, () -> Ticks.parse("20"));
        assertThrows(IllegalArgumentException.class, () -> Ticks.parse("-5t"));
        assertThrows(IllegalArgumentException.class, () -> Ticks.parse("2d"));
        assertThrows(IllegalArgumentException.class, () -> Ticks.parse("9999999999t"));
        assertThrows(IllegalArgumentException.class, () -> Ticks.parse("soon"));
    }
}
