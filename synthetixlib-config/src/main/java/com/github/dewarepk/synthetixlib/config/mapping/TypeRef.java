package com.github.dewarepk.synthetixlib.config.mapping;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Internal type model: a Java {@link Type} together with the bindings of the type variables in scope, so
 * {@code record Page<T>(List<T> items)} used as {@code Page<Zone>} resolves {@code items} to {@code List<Zone>}.
 * Immutable; equal references resolve to the same codec. Public only so the {@code codec} package can build one
 * from a {@code Class} or a {@code TypeToken}; everything else is package-private. Internal, do not use.
 */
@org.jetbrains.annotations.ApiStatus.Internal
public final class TypeRef {

    private final Type type;
    private final Map<TypeVariable<?>, TypeRef> bindings;

    private TypeRef(final Type type, final Map<TypeVariable<?>, TypeRef> bindings) {
        this.type = type;
        this.bindings = bindings;
    }

    /** Reference to a plain class (generic classes must be given as a {@code TypeToken} instead). */
    public static TypeRef of(final Class<?> type) {
        return new TypeRef(Objects.requireNonNull(type, "type"), Map.of());
    }

    /**
     * Reads the type argument of an anonymous {@code tokenBase} subclass.
     *
     * @throws IllegalArgumentException if {@code tokenClass} does not extend {@code tokenBase} with a type argument
     */
    public static TypeRef capture(final Class<?> tokenClass, final Class<?> tokenBase) {
        if (tokenClass.getGenericSuperclass() instanceof ParameterizedType parameterized
                && parameterized.getRawType() == tokenBase) {
            final TypeRef ref = new TypeRef(parameterized.getActualTypeArguments()[0], Map.of());
            if (ref.type instanceof TypeVariable<?>) {
                throw new IllegalArgumentException(tokenBase.getSimpleName() + " needs a concrete type, not the type "
                        + "variable " + ref.type.getTypeName());
            }
            return ref;
        }
        throw new IllegalArgumentException("Create the token as an anonymous subclass with a type argument: "
                + "new " + tokenBase.getSimpleName() + "<List<String>>() {}");
    }

    /** @return the erased class */
    Class<?> raw() {
        if (type instanceof Class<?> c) {
            return c;
        }
        if (type instanceof ParameterizedType p) {
            return (Class<?>) p.getRawType();
        }
        if (type instanceof TypeVariable<?> v) {
            return bound(v).raw();
        }
        if (type instanceof WildcardType w) {
            return normalize(w.getUpperBounds()[0], bindings).raw();
        }
        throw new IllegalArgumentException("Unsupported type " + type.getTypeName() + " (arrays are not supported, "
                + "use List)");
    }

    /** @return the type arguments, empty for a class or a raw type */
    List<TypeRef> arguments() {
        if (type instanceof ParameterizedType p) {
            return java.util.Arrays.stream(p.getActualTypeArguments()).map(a -> normalize(a, bindings)).toList();
        }
        if (type instanceof TypeVariable<?> v) {
            return bound(v).arguments();
        }
        if (type instanceof WildcardType w) {
            return normalize(w.getUpperBounds()[0], bindings).arguments();
        }
        return List.of();
    }

    /**
     * @param what describes the generic type for the error message, e.g. {@code "List"}
     * @return the argument at {@code index}
     * @throws IllegalArgumentException if the type is raw
     */
    TypeRef argument(final int index, final String what) {
        final List<TypeRef> arguments = arguments();
        if (arguments.size() <= index) {
            throw new IllegalArgumentException("Raw " + what + " is not supported, declare its type arguments "
                    + "(e.g. " + what + "<String>)");
        }
        return arguments.get(index);
    }

    /** @return a reference to a member type (e.g. a record component) declared inside this type */
    TypeRef member(final Type memberType) {
        final Class<?> raw = raw();
        final TypeVariable<?>[] parameters = raw.getTypeParameters();
        if (parameters.length == 0) {
            return new TypeRef(memberType, Map.of());
        }
        final List<TypeRef> arguments = arguments();
        if (arguments.size() != parameters.length) {
            throw new IllegalArgumentException("Generic type " + raw.getName() + " needs its type arguments: use "
                    + "a TypeToken, e.g. new TypeToken<" + raw.getSimpleName() + "<...>>() {}");
        }
        final Map<TypeVariable<?>, TypeRef> scope = new HashMap<>();
        for (int i = 0; i < parameters.length; i++) {
            scope.put(parameters[i], arguments.get(i));
        }
        return new TypeRef(memberType, Map.copyOf(scope));
    }

    /**
     * Equal Java types must give equal references: a type variable bound in {@code bindings} is replaced by its
     * binding (also as the upper bound of a wildcard), and a plain class carries no bindings.
     */
    private static TypeRef normalize(final Type type, final Map<TypeVariable<?>, TypeRef> bindings) {
        if (type instanceof TypeVariable<?> variable) {
            final TypeRef bound = bindings.get(variable);
            if (bound != null) {
                return bound;
            }
        }
        if (type instanceof WildcardType wildcard) {
            return normalize(wildcard.getUpperBounds()[0], bindings);
        }
        return new TypeRef(type, type instanceof Class<?> ? Map.of() : bindings);
    }

    private TypeRef bound(final TypeVariable<?> variable) {
        final TypeRef bound = bindings.get(variable);
        if (bound == null) {
            throw new IllegalArgumentException("Type variable " + variable.getName() + " cannot be resolved: use a "
                    + "TypeToken that gives the concrete type, e.g. new TypeToken<Page<Zone>>() {}");
        }
        return bound;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof TypeRef ref && type.equals(ref.type) && bindings.equals(ref.bindings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, bindings);
    }

    @Override
    public String toString() {
        if (type instanceof TypeVariable<?> v && bindings.containsKey(v)) {
            return bindings.get(v).toString();
        }
        return type.getTypeName();
    }
}
