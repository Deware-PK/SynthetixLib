package com.github.dewarepk.synthetixlib.config.codec;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** JSON text to and from the data tree, using Gson's streaming classes (shipped with Paper). */
final class Json {

    private static final int MAX_DEPTH = 128;
    private static final Pattern INTEGER = Pattern.compile("-?\\d+");

    private Json() {
    }

    static String write(final Object tree) {
        final StringWriter out = new StringWriter();
        try (JsonWriter writer = new JsonWriter(out)) {
            writeValue(writer, tree);
        } catch (final IOException e) {
            throw new IllegalStateException("cannot write JSON", e);
        }
        return out.toString();
    }

    private static void writeValue(final JsonWriter writer, final Object value) throws IOException {
        if (value == null) {
            writer.nullValue();
        } else if (value instanceof String s) {
            writer.value(s);
        } else if (value instanceof Boolean b) {
            writer.value(b.booleanValue());
        } else if (value instanceof Number n) {
            if ((n instanceof Double || n instanceof Float) && !Double.isFinite(n.doubleValue())) {
                throw new IllegalArgumentException("NaN and infinity cannot be written as JSON: " + n);
            }
            writer.value(n);
        } else if (value instanceof Map<?, ?> map) {
            writer.beginObject();
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                writer.name(String.valueOf(entry.getKey()));
                writeValue(writer, entry.getValue());
            }
            writer.endObject();
        } else if (value instanceof List<?> list) {
            writer.beginArray();
            for (final Object item : list) {
                writeValue(writer, item);
            }
            writer.endArray();
        } else {
            throw new IllegalArgumentException(
                    "not a data tree value: " + value.getClass().getName() + " (a codec returned the wrong kind of object)");
        }
    }

    /** @throws DecodeException if {@code json} is not valid JSON (path is empty) */
    static Object read(final String json, final DecodeContext ctx) {
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            final Object value = readValue(reader, ctx, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw ctx.fail("invalid JSON: unexpected content after the value");
            }
            return value;
        } catch (final IOException | RuntimeException e) {
            if (e instanceof DecodeException decode) {
                throw decode;
            }
            throw ctx.fail("invalid JSON: " + e.getMessage());
        }
    }

    private static Object readValue(final JsonReader reader, final DecodeContext ctx, final int depth)
            throws IOException {
        if (depth > MAX_DEPTH) {
            throw ctx.fail("invalid JSON: nested deeper than " + MAX_DEPTH + " levels");
        }
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                final Map<String, Object> map = new LinkedHashMap<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    final String name = reader.nextName();
                    map.put(name, readValue(reader, ctx, depth + 1));
                }
                reader.endObject();
                yield map;
            }
            case BEGIN_ARRAY -> {
                final List<Object> list = new ArrayList<>();
                reader.beginArray();
                while (reader.hasNext()) {
                    list.add(readValue(reader, ctx, depth + 1));
                }
                reader.endArray();
                yield list;
            }
            case STRING -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> {
                reader.nextNull();
                yield null;
            }
            case NUMBER -> number(reader.nextString());
            default -> throw ctx.fail("invalid JSON: unexpected " + reader.peek());
        };
    }

    private static Number number(final String text) {
        if (INTEGER.matcher(text).matches()) {
            try {
                return Long.parseLong(text);
            } catch (final NumberFormatException tooBig) {
                return Double.parseDouble(text);
            }
        }
        return Double.parseDouble(text);
    }
}
