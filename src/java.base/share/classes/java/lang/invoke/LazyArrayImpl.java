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

final class LazyArrayImpl {
    private LazyArrayImpl() { }

    static <A, T> LazyArray<A, T> ofPlain(int size) {
        return OfPlain.of(size);
    }

    static <A, T> LazyArray<A, T> ofCas(int size) {
        return OfCas.of(size);
    }

    static <A, T> LazyArray<A, T> ofOnce(int size) {
        return OfOnce.of(size);
    }

    @TrustFinalFields
    static final class OfPlain<A, T> implements LazyArray<A, T> {
        private final Object[] values;

        private OfPlain(int size) {
            if (size < 0) throw new IllegalArgumentException("Negative size: " + size);
            values = new Object[size];
        }

        static <A, T> LazyArray<A, T> of(int size) {
            return new OfPlain<>(size);
        }

        @Override
        @ForceInline
        public T get(LazyArray.Computer<? super A, ? extends T> computer, A argument, int index) {
            Object value = values[index];
            if (value == null) {
                value = Objects.requireNonNull(computer.compute(argument, index));
                values[index] = value;
            }
            @SuppressWarnings("unchecked")
            T result = (T) value;
            return result;
        }
    }

    @TrustFinalFields
    static final class OfCas<A, T> implements LazyArray<A, T> {
        private static final Unsafe UNSAFE = Unsafe.getUnsafe();
        private static final long ARRAY_BASE = Unsafe.ARRAY_OBJECT_BASE_OFFSET;
        private static final int ARRAY_SHIFT = 31 - Integer.numberOfLeadingZeros(Unsafe.ARRAY_OBJECT_INDEX_SCALE);

        @Stable
        private final Object[] values;

        private OfCas(int size) {
            if (size < 0) throw new IllegalArgumentException("Negative size: " + size);
            values = new Object[size];
        }

        static <A, T> LazyArray<A, T> of(int size) {
            return new OfCas<>(size);
        }

        @Override
        @ForceInline
        public T get(LazyArray.Computer<? super A, ? extends T> computer, A argument, int index) {
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
    static final class OfOnce<A, T> implements LazyArray<A, T> {
        private static final Unsafe UNSAFE = Unsafe.getUnsafe();
        private static final long ARRAY_BASE = Unsafe.ARRAY_OBJECT_BASE_OFFSET;
        private static final int ARRAY_SHIFT = 31 - Integer.numberOfLeadingZeros(Unsafe.ARRAY_OBJECT_INDEX_SCALE);

        @Stable
        private final Object[] values;

        private OfOnce(int size) {
            if (size < 0) throw new IllegalArgumentException("Negative size: " + size);
            values = new Object[size];
        }

        static <A, T> LazyArray<A, T> of(int size) {
            return new OfOnce<>(size);
        }

        @Override
        @ForceInline
        public T get(LazyArray.Computer<? super A, ? extends T> computer, A argument, int index) {
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
