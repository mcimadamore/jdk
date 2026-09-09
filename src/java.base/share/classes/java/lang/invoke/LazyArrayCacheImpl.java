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
import jdk.internal.vm.annotation.Stable;
import jdk.internal.vm.annotation.TrustFinalFields;

import static java.lang.invoke.MethodHandleStatics.uncaughtException;

final class LazyArrayCacheImpl {
    private LazyArrayCacheImpl() { }

    static <T> LazyArrayCache<T> ofRetry(int size) {
        return OfRetry.of(size);
    }

    static <T> LazyArrayCache<T> ofOnce(int size) {
        return OfOnce.of(size);
    }

    @TrustFinalFields
    static final class OfRetry<T> implements LazyArrayCache<T> {
        private static final Unsafe UNSAFE = Unsafe.getUnsafe();
        private static final long ARRAY_BASE = Unsafe.ARRAY_OBJECT_BASE_OFFSET;
        private static final int ARRAY_SHIFT = 31 - Integer.numberOfLeadingZeros(Unsafe.ARRAY_OBJECT_INDEX_SCALE);

        @Stable
        private final Object[] values;

        private OfRetry(int size) {
            if (size < 0) throw new IllegalArgumentException("Negative size: " + size);
            values = new Object[size];
        }

        static <T> LazyArrayCache<T> of(int size) {
            return new OfRetry<>(size);
        }

        @Override
        @ForceInline
        public <A> T getOrCompute(A argument, int index, LazyArrayCache.Computer<? super A, ? extends T> computer) {
            Objects.checkIndex(index, values.length);
            long offset = ARRAY_BASE + ((long) index << ARRAY_SHIFT);
            Object value = UNSAFE.getReferenceStable(values, offset);
            if (value == null) {
                Object candidate = Objects.requireNonNull(computer.compute(argument, index));
                Object witness = UNSAFE.compareAndExchangeReference(values, offset, null, candidate);
                value = witness == null ? candidate : witness;
            }
            @SuppressWarnings("unchecked")
            T result = (T) value;
            return result;
        }
    }

    @TrustFinalFields
    static final class OfOnce<T> implements LazyArrayCache<T> {
        private static final Unsafe UNSAFE = Unsafe.getUnsafe();
        private static final long ARRAY_BASE = Unsafe.ARRAY_OBJECT_BASE_OFFSET;
        private static final int ARRAY_SHIFT = 31 - Integer.numberOfLeadingZeros(Unsafe.ARRAY_OBJECT_INDEX_SCALE);

        @Stable
        private final Object[] values;

        private OfOnce(int size) {
            if (size < 0) throw new IllegalArgumentException("Negative size: " + size);
            values = new Object[size];
        }

        static <T> LazyArrayCache<T> of(int size) {
            return new OfOnce<>(size);
        }

        @Override
        @ForceInline
        public <A> T getOrCompute(A argument, int index, LazyArrayCache.Computer<? super A, ? extends T> computer) {
            Objects.checkIndex(index, values.length);
            long offset = ARRAY_BASE + ((long) index << ARRAY_SHIFT);
            Object value = UNSAFE.getReferenceStable(values, offset);
            if (value == null) {
                synchronized (this) {
                    value = UNSAFE.getReferenceVolatile(values, offset);
                    if (value == null) {
                        try {
                            value = Objects.requireNonNull(computer.compute(argument, index));
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
            @SuppressWarnings("unchecked")
            T result = (T) value;
            return result;
        }

        private record Failed(Throwable exception) { }
    }
}
