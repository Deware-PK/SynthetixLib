package com.github.dewarepk.synthetixlib.config.mapping;

import com.github.dewarepk.synthetixlib.config.annotation.Key;
import com.github.dewarepk.synthetixlib.config.annotation.NotEmpty;
import com.github.dewarepk.synthetixlib.config.annotation.Range;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Everything read by reflection about one record class: components, keys, annotations, canonical constructor.
 * Independent of generic type arguments, so one instance per class is cached by the resolver.
 */
final class RecordMeta {

    /**
     * @param name        component name
     * @param key         data key (kebab-case or {@code @Key})
     * @param genericType declared type, may contain type variables
     * @param accessor    public accessor method
     * @param nullable    marked with jspecify {@code @Nullable}
     * @param range       {@code @Range} or {@code null}
     * @param notEmpty    marked with {@code @NotEmpty}
     */
    record Component(String name, String key, Type genericType, Method accessor, boolean nullable,
                     Range range, boolean notEmpty) {
    }

    private final Class<?> type;
    private final List<Component> components;
    private final Constructor<?> constructor;

    private RecordMeta(final Class<?> type, final List<Component> components, final Constructor<?> constructor) {
        this.type = type;
        this.components = components;
        this.constructor = constructor;
    }

    /** @throws IllegalArgumentException if the record cannot be mapped (not public, duplicate keys, blank {@code @Key}) */
    static RecordMeta of(final Class<?> type) {
        if (!Modifier.isPublic(type.getModifiers())) {
            throw new IllegalArgumentException("Record " + type.getName() + " must be public so it can be mapped "
                    + "(the library never uses setAccessible)");
        }
        final RecordComponent[] declared = type.getRecordComponents();
        final List<Component> components = new ArrayList<>(declared.length);
        final Set<String> keys = new HashSet<>();
        final Class<?>[] parameterTypes = new Class<?>[declared.length];
        for (int i = 0; i < declared.length; i++) {
            final RecordComponent component = declared[i];
            parameterTypes[i] = component.getType();
            final Key override = component.getAnnotation(Key.class);
            final String key = override != null ? override.value() : Names.kebab(component.getName());
            if (key.isBlank()) {
                throw new IllegalArgumentException("@Key on " + type.getName() + "." + component.getName()
                        + " must not be blank");
            }
            if (!keys.add(key)) {
                throw new IllegalArgumentException("Record " + type.getName() + " has two components with the key '"
                        + key + "'");
            }
            components.add(new Component(
                    component.getName(),
                    key,
                    component.getGenericType(),
                    component.getAccessor(),
                    component.getAnnotatedType().isAnnotationPresent(Nullable.class),
                    component.getAnnotation(Range.class),
                    component.isAnnotationPresent(NotEmpty.class)));
        }
        try {
            return new RecordMeta(type, List.copyOf(components), type.getDeclaredConstructor(parameterTypes));
        } catch (final NoSuchMethodException e) {
            throw new IllegalStateException("Record " + type.getName() + " has no canonical constructor", e);
        }
    }

    Class<?> type() {
        return type;
    }

    List<Component> components() {
        return components;
    }

    Constructor<?> constructor() {
        return constructor;
    }
}
