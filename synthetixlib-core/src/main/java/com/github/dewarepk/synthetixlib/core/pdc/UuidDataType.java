package com.github.dewarepk.synthetixlib.core.pdc;

import java.nio.ByteBuffer;
import java.util.UUID;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/** {@link UUID} stored as 16 bytes: most significant bits, then least significant bits (big-endian). */
final class UuidDataType implements PersistentDataType<byte[], UUID> {

    UuidDataType() {
    }

    @Override
    public @NotNull Class<byte[]> getPrimitiveType() {
        return byte[].class;
    }

    @Override
    public @NotNull Class<UUID> getComplexType() {
        return UUID.class;
    }

    @Override
    public byte @NotNull [] toPrimitive(final @NotNull UUID complex, final @NotNull PersistentDataAdapterContext context) {
        return ByteBuffer.allocate(16)
                .putLong(complex.getMostSignificantBits())
                .putLong(complex.getLeastSignificantBits())
                .array();
    }

    @Override
    public @NotNull UUID fromPrimitive(final byte @NotNull [] primitive, final @NotNull PersistentDataAdapterContext context) {
        if (primitive.length != 16) {
            throw new IllegalArgumentException("UUID needs 16 bytes, got " + primitive.length);
        }
        final ByteBuffer buffer = ByteBuffer.wrap(primitive);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
