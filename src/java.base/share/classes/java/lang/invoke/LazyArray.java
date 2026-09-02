/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 */

package java.lang.invoke;

import java.util.Objects;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;

import static java.lang.invoke.MethodHandleStatics.uncaughtException;

/**
 * An array of independently lazily computed values.
 *
 * @param <A> the computing-function argument type
 * @param <T> the element type
 */
public abstract class LazyArray<A, T> {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final long ARRAY_BASE = Unsafe.ARRAY_OBJECT_BASE_OFFSET;
    private static final int ARRAY_SHIFT = 31 - Integer.numberOfLeadingZeros(Unsafe.ARRAY_OBJECT_INDEX_SCALE);

    private final Object[] values;

    /**
     * Creates an uninitialized lazy array with {@code size} elements.
     * @param size the array size
     */
    protected LazyArray(int size) {
        if (size < 0) throw new IllegalArgumentException("Negative size: " + size);
        values = new Object[size];
    }

    /**
     * Computes the element at {@code index} from {@code argument}.
     * @param argument the computing argument
     * @param index the element index
     * @return the computed element
     */
    protected abstract T compute(A argument, int index);

    /**
     * Returns an element, computing and publishing it with plain accesses when needed.
     * @param argument the computing argument
     * @param index the element index
     * @return the lazy element
     */
    @ForceInline
    public final T getPlain(A argument, int index) {
        Object value = values[index];
        if (value == null) {
            value = Objects.requireNonNull(compute(argument, index));
            values[index] = value;
        }
        return cast(value);
    }

    /**
     * Returns an element, allowing computations to race but publishing one outcome.
     * @param argument the computing argument
     * @param index the element index
     * @return the lazy element
     */
    @ForceInline
    public final T getCas(A argument, int index) {
        Objects.checkIndex(index, values.length);
        long offset = ARRAY_BASE + ((long) index << ARRAY_SHIFT);
        Object value = UNSAFE.getReferenceStable(values, offset);
        if (value == null) {
            Object candidate = Objects.requireNonNull(compute(argument, index));
            Object witness = UNSAFE.compareAndExchangeReference(values, offset, null, candidate);
            value = witness == null ? candidate : witness;
        }
        return cast(value);
    }

    /**
     * Returns an element, computing one successful or failed outcome.
     * @param argument the computing argument
     * @param index the element index
     * @return the lazy element
     */
    @ForceInline
    public final T getOnce(A argument, int index) {
        Objects.checkIndex(index, values.length);
        long offset = ARRAY_BASE + ((long) index << ARRAY_SHIFT);
        Object value = UNSAFE.getReferenceStable(values, offset);
        if (value == null) {
            synchronized (this) {
                value = UNSAFE.getReferenceVolatile(values, offset);
                if (value == null) {
                    try {
                        value = Objects.requireNonNull(compute(argument, index));
                    } catch (Throwable ex) {
                        value = new Failed(ex);
                    }
                    UNSAFE.putReferenceVolatile(values, offset, value);
                }
            }
        }
        if (value instanceof Failed failed) {
            throw uncaughtException(failed.exception);
        }
        return cast(value);
    }

    @SuppressWarnings("unchecked")
    private T cast(Object value) {
        return (T) value;
    }

    private record Failed(Throwable exception) { }
}
