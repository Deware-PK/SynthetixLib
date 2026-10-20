package com.github.dewarepk.synthetixlib.database;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The current row of a result, read by column name or alias (case-insensitive). Passed to a {@link RowMapper}; only valid
 * during that call.
 *
 * <p>The plain getters throw {@link IllegalStateException} for SQL {@code NULL}; the {@code ...OrNull} ones return
 * {@code null}. A column that does not exist, or whose value cannot be read as the asked type, throws
 * {@link IllegalArgumentException} naming the column. Storage formats: UUID as {@code CHAR(36)} text, Instant and
 * Duration as milliseconds ({@code BIGINT}), enums by name, ItemStack as the bytes of {@code serializeAsBytes}.
 *
 * <pre>{@code
 * row -> new Lot(row.uuid("id"), row.longValue("price"), row.instant("ends_at"), row.stringOrNull("note"))
 * }</pre>
 */
public interface Row {

    /** @return the text in {@code column} */
    String string(String column);

    /** @return the text in {@code column}, or {@code null} for SQL NULL */
    @Nullable String stringOrNull(String column);

    /** @return the UUID stored as text in {@code column} */
    UUID uuid(String column);

    /** @return the UUID in {@code column}, or {@code null} for SQL NULL */
    @Nullable UUID uuidOrNull(String column);

    /** @return the integer in {@code column}; a value outside the int range is an error */
    int intValue(String column);

    /** @return the integer in {@code column}, or {@code null} for SQL NULL */
    @Nullable Integer intValueOrNull(String column);

    /** @return the integer in {@code column} */
    long longValue(String column);

    /** @return the integer in {@code column}, or {@code null} for SQL NULL */
    @Nullable Long longValueOrNull(String column);

    /** @return the number in {@code column} */
    double doubleValue(String column);

    /** @return the number in {@code column}, or {@code null} for SQL NULL */
    @Nullable Double doubleValueOrNull(String column);

    /** @return the flag in {@code column} ({@code BOOLEAN}, or an integer that is 0 or 1) */
    boolean bool(String column);

    /** @return the flag in {@code column}, or {@code null} for SQL NULL */
    @Nullable Boolean boolOrNull(String column);

    /** @return a copy of the bytes in {@code column} */
    byte[] bytes(String column);

    /** @return a copy of the bytes in {@code column}, or {@code null} for SQL NULL */
    byte @Nullable [] bytesOrNull(String column);

    /** @return the point in time stored as epoch milliseconds in {@code column} */
    Instant instant(String column);

    /** @return the point in time in {@code column}, or {@code null} for SQL NULL */
    @Nullable Instant instantOrNull(String column);

    /** @return the duration stored as milliseconds in {@code column} */
    Duration duration(String column);

    /** @return the duration in {@code column}, or {@code null} for SQL NULL */
    @Nullable Duration durationOrNull(String column);

    /**
     * @param type the enum class; the column holds the name of a constant
     * @return the constant named in {@code column}
     */
    <E extends Enum<E>> E enumOf(String column, Class<E> type);

    /** @return the constant named in {@code column}, or {@code null} for SQL NULL */
    <E extends Enum<E>> @Nullable E enumOfOrNull(String column, Class<E> type);

    /** @return the item stored in {@code column} (needs a running server) */
    ItemStack itemStack(String column);

    /** @return the item in {@code column}, or {@code null} for SQL NULL (needs a running server) */
    @Nullable ItemStack itemStackOrNull(String column);
}
