package com.github.dewarepk.synthetixlib.database;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** A {@link Row} over the cursor of a {@link ResultSet}; the cursor is moved by the caller. */
final class ResultRow implements Row {

    private static final int UUID_LENGTH = 36;

    private final ResultSet rows;

    ResultRow(final ResultSet rows) {
        this.rows = rows;
    }

    // --- text and uuid --------------------------------------------------------------------------------------------

    @Override
    public String string(final String column) {
        return required(column, stringOrNull(column), "stringOrNull");
    }

    @Override
    public @Nullable String stringOrNull(final String column) {
        return read(column, "a String", value -> {
            if (value instanceof String text) {
                return text;
            }
            throw new IllegalArgumentException();
        });
    }

    @Override
    public UUID uuid(final String column) {
        return required(column, uuidOrNull(column), "uuidOrNull");
    }

    @Override
    public @Nullable UUID uuidOrNull(final String column) {
        return read(column, "a UUID", value -> {
            if (value instanceof String text && text.length() == UUID_LENGTH) {
                return UUID.fromString(text);
            }
            throw new IllegalArgumentException();
        });
    }

    // --- numbers --------------------------------------------------------------------------------------------------

    @Override
    public int intValue(final String column) {
        return required(column, intValueOrNull(column), "intValueOrNull");
    }

    @Override
    public @Nullable Integer intValueOrNull(final String column) {
        return read(column, "an int", value -> Math.toIntExact(whole(value)));
    }

    @Override
    public long longValue(final String column) {
        return required(column, longValueOrNull(column), "longValueOrNull");
    }

    @Override
    public @Nullable Long longValueOrNull(final String column) {
        return read(column, "a long", ResultRow::whole);
    }

    @Override
    public double doubleValue(final String column) {
        return required(column, doubleValueOrNull(column), "doubleValueOrNull");
    }

    @Override
    public @Nullable Double doubleValueOrNull(final String column) {
        return read(column, "a double", value -> {
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            throw new IllegalArgumentException();
        });
    }

    @Override
    public boolean bool(final String column) {
        return required(column, boolOrNull(column), "boolOrNull");
    }

    @Override
    public @Nullable Boolean boolOrNull(final String column) {
        return read(column, "a boolean", value -> {
            if (value instanceof Boolean flag) {
                return flag;
            }
            final long number = whole(value);
            if (number == 0 || number == 1) {
                return number == 1;
            }
            throw new IllegalArgumentException();
        });
    }

    // --- bytes ----------------------------------------------------------------------------------------------------

    @Override
    public byte[] bytes(final String column) {
        return required(column, bytesOrNull(column), "bytesOrNull");
    }

    @Override
    public byte @Nullable [] bytesOrNull(final String column) {
        return read(column, "bytes", value -> {
            if (value instanceof byte[] bytes) {
                return bytes.clone();
            }
            throw new IllegalArgumentException();
        });
    }

    // --- time -----------------------------------------------------------------------------------------------------

    @Override
    public Instant instant(final String column) {
        return required(column, instantOrNull(column), "instantOrNull");
    }

    @Override
    public @Nullable Instant instantOrNull(final String column) {
        return read(column, "an Instant (epoch milliseconds)", value -> Instant.ofEpochMilli(whole(value)));
    }

    @Override
    public Duration duration(final String column) {
        return required(column, durationOrNull(column), "durationOrNull");
    }

    @Override
    public @Nullable Duration durationOrNull(final String column) {
        return read(column, "a Duration (milliseconds)", value -> Duration.ofMillis(whole(value)));
    }

    // --- enum and item --------------------------------------------------------------------------------------------

    @Override
    public <E extends Enum<E>> E enumOf(final String column, final Class<E> type) {
        return required(column, enumOfOrNull(column, type), "enumOfOrNull");
    }

    @Override
    public <E extends Enum<E>> @Nullable E enumOfOrNull(final String column, final Class<E> type) {
        return read(column, "a constant of " + type.getName(), value -> {
            if (value instanceof String name) {
                return Enum.valueOf(type, name);
            }
            throw new IllegalArgumentException();
        });
    }

    @Override
    public ItemStack itemStack(final String column) {
        return required(column, itemStackOrNull(column), "itemStackOrNull");
    }

    @Override
    public @Nullable ItemStack itemStackOrNull(final String column) {
        if (value(column) instanceof byte[]) {
            Params.requireServer();   // outside read(): it would turn the exception into "cannot be read"
        }
        return read(column, "an ItemStack", value -> {
            if (value instanceof byte[] bytes) {
                return ItemStack.deserializeBytes(bytes);
            }
            throw new IllegalArgumentException();
        });
    }

    // --- plumbing -------------------------------------------------------------------------------------------------

    private <T> @Nullable T read(final String column, final String wanted, final Function<Object, T> convert) {
        final Object value = value(column);
        if (value == null) {
            return null;
        }
        try {
            return convert.apply(value);
        } catch (final RuntimeException e) {
            // the value itself is player data: only its Java type goes into the message
            throw new IllegalArgumentException("Column \"" + column + "\" holds a " + value.getClass().getName()
                    + " that cannot be read as " + wanted, e);
        }
    }

    private static <T> T required(final String column, final @Nullable T value, final String orNull) {
        if (value == null) {
            throw new IllegalStateException("Column \"" + column + "\" is NULL; read it with " + orNull
                    + " if that is allowed");
        }
        return value;
    }

    private @Nullable Object value(final String column) {
        final int index;
        try {
            index = rows.findColumn(column);
        } catch (final SQLException e) {
            throw new IllegalArgumentException("No column \"" + column + "\" in this result (columns: "
                    + columnNames() + ")", e);
        }
        try {
            return rows.getObject(index);
        } catch (final SQLException e) {
            throw new IllegalStateException("Cannot read column \"" + column + "\": " + e.getMessage(), e);
        }
    }

    private String columnNames() {
        try {
            final ResultSetMetaData meta = rows.getMetaData();
            final List<String> names = new ArrayList<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                names.add(meta.getColumnLabel(i));
            }
            return String.join(", ", names);
        } catch (final SQLException e) {
            return "unknown";
        }
    }

    /** An integer column as a long: the Java type depends on the driver and the column type. */
    private static long whole(final Object value) {
        return switch (value) {
            case Byte number -> number;
            case Short number -> number;
            case Integer number -> number;
            case Long number -> number;
            case BigInteger number -> number.longValueExact();
            case BigDecimal number -> number.longValueExact();
            case Double number when number == Math.rint(number) && Math.abs(number) < 0x1p63 -> number.longValue();
            case Float number when number == Math.rint(number) && Math.abs(number) < 0x1p63 -> number.longValue();
            default -> throw new IllegalArgumentException();
        };
    }
}
