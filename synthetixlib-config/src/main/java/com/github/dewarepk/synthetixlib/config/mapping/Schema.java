package com.github.dewarepk.synthetixlib.config.mapping;

import com.github.dewarepk.synthetixlib.config.annotation.Comment;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads {@code @Comment} annotations of a record type, so the YAML layer can write them without reflection.
 * Internal.
 */
@org.jetbrains.annotations.ApiStatus.Internal
public final class Schema {

    private Schema() {
    }

    /**
     * @param type a public record type
     * @return the lines of the type-level {@code @Comment}, empty if absent
     */
    public static List<String> header(final Class<?> type) {
        final Comment comment = type.getAnnotation(Comment.class);
        return comment == null ? List.of() : List.of(comment.value());
    }

    /**
     * Comments of the components of {@code type} and of nested record components (static structure only; entries
     * of maps and lists have no fixed key). Records that contain themselves are entered once per path..
     *
     * @param type a public record type
     * @return component comments by dotted data path, e.g. {@code anti-snipe.enabled}; components without a
     *         comment are absent
     * @throws IllegalArgumentException if {@code type} cannot be mapped
     */
    public static Map<String, List<String>> comments(final Class<?> type) {
        final Map<String, List<String>> out = new LinkedHashMap<>();
        collect(type, "", out, new HashSet<>());
        return out;
    }

    /** @param onPath the record types being collected, outermost first; a type already on it is not entered again */
    private static void collect(final Class<?> type, final String prefix, final Map<String, List<String>> out,
                                final Set<Class<?>> onPath) {
        if (!onPath.add(type)) {
            return;
        }
        for (final RecordMeta.Component component : RecordMeta.of(type).components()) {
            final String path = prefix.isEmpty() ? component.key() : prefix + "." + component.key();
            if (!component.comment().isEmpty()) {
                out.put(path, component.comment());
            }
            final Class<?> nested = nestedRecord(component.genericType());
            if (nested != null) {
                collect(nested, path, out, onPath);
            }
        }
        onPath.remove(type);
    }

    /** The record a component holds directly or in an {@code Optional}, else {@code null}. */
    private static Class<?> nestedRecord(final Type declared) {
        if (declared instanceof ParameterizedType parameterized && parameterized.getRawType() == Optional.class
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> inner && inner.isRecord()) {
            return inner;
        }
        return declared instanceof Class<?> direct && direct.isRecord() ? direct : null;
    }
}
