package com.github.dewarepk.synthetixlib.archtest.fixture;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Deliberately violates the setAccessible rule; only imported by the rule's own test. */
public final class SetsAccessible {

    private int value;

    public void violate() throws NoSuchFieldException, NoSuchMethodException {
        final Field field = SetsAccessible.class.getDeclaredField("value");
        field.setAccessible(true);
        final Method method = SetsAccessible.class.getDeclaredMethod("violate");
        method.trySetAccessible();
        AccessibleObject.setAccessible(new AccessibleObject[] {field, method}, true);
    }
}
