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

final class LazyArrayDeclSiteImpl {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final long ARRAY_BASE = UNSAFE.arrayBaseOffset(Object[].class);
    private static final int ARRAY_SHIFT = Integer.numberOfTrailingZeros(
            UNSAFE.arrayIndexScale(Object[].class));

    private LazyArrayDeclSiteImpl() { }

    static <A, T> LazyArrayDeclSite<A, T> ofPlain(
            int size, LazyArrayDeclSite.Computer<? super A, ? extends T> computer) {
        return new OfPlain<>(size, computer);
    }

    static <A, T> LazyArrayDeclSite<A, T> ofCas(
            int size, LazyArrayDeclSite.Computer<? super A, ? extends T> computer) {
        return new OfCas<>(size, computer);
    }

    static <A, T> LazyArrayDeclSite<A, T> ofOnce(
            int size, LazyArrayDeclSite.Computer<? super A, ? extends T> computer) {
        return new OfOnce<>(size, computer);
    }

    private static int checkSize(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Negative size: " + size);
        }
        return size;
    }

    private static long offset(Object[] values, int index) {
        Objects.checkIndex(index, values.length);
        return ARRAY_BASE + ((long) index << ARRAY_SHIFT);
    }

    @TrustFinalFields
    static final class OfPlain<A, T> implements LazyArrayDeclSite<A, T> {
        private final Computer<? super A, ? extends T> computer;
        private final Object[] values;

        OfPlain(int size, Computer<? super A, ? extends T> computer) {
            this.computer = computer;
            this.values = new Object[checkSize(size)];
        }

        @Override
        @ForceInline
        @SuppressWarnings("unchecked")
        public T get(A argument, int index) {
            Object value = values[index];
            if (value == null) {
                values[index] = value = Objects.requireNonNull(
                        computer.compute(argument, index));
            }
            return (T) value;
        }
    }

    @TrustFinalFields
    static final class OfCas<A, T> implements LazyArrayDeclSite<A, T> {
        private final Computer<? super A, ? extends T> computer;
        @Stable
        private final Object[] values;

        OfCas(int size, Computer<? super A, ? extends T> computer) {
            this.computer = computer;
            this.values = new Object[checkSize(size)];
        }

        @Override
        @ForceInline
        @SuppressWarnings("unchecked")
        public T get(A argument, int index) {
            long offset = offset(values, index);
            Object value = UNSAFE.getReferenceStable(values, offset);
            if (value == null) {
                Object candidate = Objects.requireNonNull(
                        computer.compute(argument, index));
                Object witness = UNSAFE.compareAndExchangeReference(
                        values, offset, null, candidate);
                value = witness == null ? candidate : witness;
            }
            return (T) value;
        }
    }

    @TrustFinalFields
    static final class OfOnce<A, T> implements LazyArrayDeclSite<A, T> {
        private final Computer<? super A, ? extends T> computer;
        @Stable
        private final Object[] values;

        OfOnce(int size, Computer<? super A, ? extends T> computer) {
            this.computer = computer;
            this.values = new Object[checkSize(size)];
        }

        @Override
        @ForceInline
        @SuppressWarnings("unchecked")
        public T get(A argument, int index) {
            long offset = offset(values, index);
            Object value = UNSAFE.getReferenceStable(values, offset);
            if (value == null) {
                synchronized (this) {
                    value = UNSAFE.getReferenceVolatile(values, offset);
                    if (value == null) {
                        try {
                            value = Objects.requireNonNull(
                                    computer.compute(argument, index));
                        } catch (Throwable ex) {
                            value = new Failure(ex);
                        }
                        UNSAFE.putReferenceVolatile(values, offset, value);
                    }
                }
            }
            if (value instanceof Failure failure) {
                throw uncaughtException(failure.exception);
            }
            return (T) value;
        }
    }

    private record Failure(Throwable exception) { }
}
