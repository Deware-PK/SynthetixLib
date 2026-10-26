package com.github.dewarepk.synthetixlib.database;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Statement parameters: the supported Java types and what they become in SQL. Conversion happens when the caller
 * invokes the database (so a wrong type fails at the call and an {@code ItemStack} is read on the caller's thread);
 * binding happens later on the worker.
 */
final class Params {

    private Params() {
    }

    /**
     * @param params the values as the caller gave them
     * @return values the JDBC driver understands: {@code String, Integer, Long, Double, Boolean, byte[]} or {@code null}
     * @throws IllegalArgumentException for a type that is not supported; the message names the position and the type,
     *                                  never the value
     */
    static @Nullable Object[] convert(final @Nullable Object[] params) {
        Objects.requireNonNull(params, "params (use new Object[] {null} for a single null)");
        final Object[] values = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            values[i] = convert(i + 1, params[i]);
        }
        return values;
    }

    /** @return {@link #convert(Object[])} for every row of a batch */
    static List<Object[]> convertRows(final List<Object[]> rows) {
        Objects.requireNonNull(rows, "rows");
        final Object[][] converted = new Object[rows.size()][];
        int next = 0;
        for (final Object[] row : rows) {
            converted[next++] = convert(row);
        }
        return List.of(converted);
    }

    /** @throws IllegalStateException without a running server (plain JUnit): item serialization needs one */
    static void requireServer() {
        if (Bukkit.getServer() == null) {
            throw new IllegalStateException("ItemStack columns need a running Paper server: test them in a self-test");
        }
    }

    private static @Nullable Object convert(final int position, final @Nullable Object value) {
        return switch (value) {
            case null -> null;
            case String text -> text;
            case Integer number -> number;
            case Long number -> number;
            case Double number -> number;
            case Boolean flag -> flag;
            case byte[] bytes -> bytes;
            case UUID uuid -> uuid.toString();
            case Instant instant -> instant.toEpochMilli();
            case Duration duration -> duration.toMillis();
            case Enum<?> constant -> constant.name();
            case ItemStack item -> {
                requireServer();
                yield item.serializeAsBytes();
            }
            default -> throw new IllegalArgumentException("Parameter " + position + " is a "
                    + value.getClass().getName() + ", which cannot be bound. Supported: String, int, long, double, "
                    + "boolean, byte[], UUID, Instant, Duration, Enum, ItemStack and null");
        };
    }

    /** Sets the already {@linkplain #convert converted} values as parameters 1..n. */
    static void bind(final PreparedStatement statement, final @Nullable Object[] values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            final int position = i + 1;
            switch (values[i]) {
                case null -> statement.setNull(position, Types.NULL);
                case String text -> statement.setString(position, text);
                case Integer number -> statement.setInt(position, number);
                case Long number -> statement.setLong(position, number);
                case Double number -> statement.setDouble(position, number);
                case Boolean flag -> statement.setBoolean(position, flag);
                case byte[] bytes -> statement.setBytes(position, bytes);
                default -> throw new IllegalStateException("not converted: " + values[i].getClass().getName());
            }
        }
    }
}
