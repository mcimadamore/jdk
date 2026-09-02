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
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;

import static java.lang.invoke.MethodHandleStatics.uncaughtException;

/**
 * A value computed lazily from an argument supplied at access time.
 *
 * @param <A> the computing-function argument type
 * @param <T> the value type
 */
public abstract class AbstractLazyValueDeclSite<A, T> {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final long VALUE_OFFSET = UNSAFE.objectFieldOffset(AbstractLazyValueDeclSite.class, "value");

    private Object value;

    /** Creates an uninitialized lazy value. */
    protected AbstractLazyValueDeclSite() { }

    /**
     * Computes the value from {@code argument}.
     * @param argument the computing argument
     * @return the computed value
     */
    protected abstract T compute(A argument);

    /**
     * Returns the value, computing and publishing it with plain accesses when needed.
     * @param argument the computing argument
     * @return the lazy value
     */
    @ForceInline
    @SuppressWarnings("unchecked")
    public final T getPlain(A argument) {
        Object value = this.value;
        if (value == null) {
            return getPlainSlow(argument);
        }
        return (T)value;
    }

    @DontInline
    private T getPlainSlow(A argument) {
        T value = Objects.requireNonNull(compute(argument));
        this.value = value;
        return value;
    }

    /**
     * Returns the value, allowing computations to race but publishing one outcome.
     * @param argument the computing argument
     * @return the lazy value
     */
    @ForceInline
    @SuppressWarnings("unchecked")
    public final T getCas(A argument) {
        Object value = UNSAFE.getReferenceStable(this, VALUE_OFFSET);
        if (value == null) {
            return getCasSlow(argument);
        }
        return (T)value;
    }

    @SuppressWarnings("unchecked")
    @DontInline
    private T getCasSlow(A argument) {
        Object candidate = Objects.requireNonNull(compute(argument));
        Object witness = UNSAFE.compareAndExchangeReference(
                this, VALUE_OFFSET, null, candidate);
        return (T)(witness == null ? candidate : witness);
    }

    /**
     * Returns the value, computing one successful or failed outcome.
     * @param argument the computing argument
     * @return the lazy value
     */
    @ForceInline
    @SuppressWarnings("unchecked")
    public final T getOnce(A argument) {
        Object value = UNSAFE.getReferenceStable(this, VALUE_OFFSET);
        if (value == null) {
            return getOnceSlow(argument);
        }
        if (value instanceof Failed failed) {
            throw uncaughtException(failed.exception);
        }
        return (T)value;
    }

    @SuppressWarnings("unchecked")
    @DontInline
    private T getOnceSlow(A argument) {
        Object value;
        synchronized (this) {
            value = UNSAFE.getReferenceVolatile(this, VALUE_OFFSET);
            if (value == null) {
                try {
                    value = Objects.requireNonNull(compute(argument));
                } catch (Throwable ex) {
                    value = new Failed(ex);
                }
                UNSAFE.putReferenceVolatile(this, VALUE_OFFSET, value);
            }
        }
        if (value instanceof Failed failed) {
            throw uncaughtException(failed.exception);
        }
        return (T) value;
    }

    private record Failed(Throwable exception) { }
}
