package com.github.dewarepk.synthetixlib.archtest.fixture;

/** Deliberate violation: the static-array rule must reject this class. */
public final class StaticArray {

    static final String[] VALUES = {"a", "b"};

    private StaticArray() {
    }
}
