package com.github.dewarepk.synthetixlib.core.pdc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PdcKeysTest {

    @Test
    void validNamesAccepted() {
        assertDoesNotThrow(() -> PdcKeys.validateName("lot_id"));
        assertDoesNotThrow(() -> PdcKeys.validateName("a/b.c-d_1"));
    }

    @Test
    void invalidNamesRejectedWithNameInMessage() {
        for (final String bad : new String[] {"Lot", "has space", "", "a:b", "ü"}) {
            final var e = assertThrows(IllegalArgumentException.class, () -> PdcKeys.validateName(bad));
            assertTrue(e.getMessage().contains("'" + bad + "'"), e.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> PdcKeys.validateName(null));
    }

    @Test
    void uuidRoundTrip() {
        final UUID id = UUID.fromString("01234567-89ab-cdef-fedc-ba9876543210");
        final byte[] bytes = new UuidDataType().toPrimitive(id, null);
        assertEquals(16, bytes.length);
        assertArrayEquals(new byte[] {0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef,
                (byte) 0xfe, (byte) 0xdc, (byte) 0xba, (byte) 0x98, 0x76, 0x54, 0x32, 0x10}, bytes);
        assertEquals(id, new UuidDataType().fromPrimitive(bytes, null));
    }

    @Test
    void uuidWrongLengthRejected() {
        assertThrows(IllegalArgumentException.class, () -> new UuidDataType().fromPrimitive(new byte[3], null));
    }
}
