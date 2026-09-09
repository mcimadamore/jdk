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
import java.util.function.Function;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;
import jdk.internal.vm.annotation.TrustFinalFields;

import static java.lang.invoke.MethodHandleStatics.uncaughtException;

final class LazyCacheImpl {
    private LazyCacheImpl() { }

    static <T> LazyCache<T> ofRetry() {
        return OfRetry.of();
    }

    static <T> LazyCache<T> ofOnce() {
        return OfOnce.of();
    }

    @TrustFinalFields
    static final class OfRetry<T> implements LazyCache<T> {
        private static final Unsafe UNSAFE = Unsafe.getUnsafe();
        private static final long VALUE_OFFSET = UNSAFE.objectFieldOffset(OfRetry.class, "value");

        @Stable
        private Object value;

        static <T> LazyCache<T> of() {
            return new OfRetry<>();
        }

        @Override
        @ForceInline
        public <A> T getOrCompute(A argument, Function<? super A, ? extends T> computer) {
            Object value = UNSAFE.getReferenceStable(this, VALUE_OFFSET);
            if (value != null) {
                @SuppressWarnings("unchecked")
                T result = (T) value;
                return result;
            }
            return getSlow(computer, argument);
        }

        private <A> T getSlow(Function<? super A, ? extends T> computer, A argument) {
            Object candidate = Objects.requireNonNull(computer.apply(argument));
            Object witness = UNSAFE.compareAndExchangeReference(
                    this, VALUE_OFFSET, null, candidate);
            Object value = witness == null ? candidate : witness;
            @SuppressWarnings("unchecked")
            T result = (T) value;
            return result;
        }
    }

    @TrustFinalFields
    static final class OfOnce<T> implements LazyCache<T> {
        private static final Unsafe UNSAFE = Unsafe.getUnsafe();
        private static final long VALUE_OFFSET = UNSAFE.objectFieldOffset(OfOnce.class, "value");

        @Stable
        private Object value;

        static <T> LazyCache<T> of() {
            return new OfOnce<>();
        }

        @Override
        @ForceInline
        public <A> T getOrCompute(A argument, Function<? super A, ? extends T> computer) {
            Object value = UNSAFE.getReferenceStable(this, VALUE_OFFSET);
            if (value != null) {
                if (value instanceof Failed failed) {
                    throw uncaughtException(failed.exception);
                }
                @SuppressWarnings("unchecked")
                T result = (T) value;
                return result;
            }
            return getSlow(computer, argument);
        }

        private <A> T getSlow(Function<? super A, ? extends T> computer, A argument) {
            Object value;
            synchronized (this) {
                value = UNSAFE.getReferenceVolatile(this, VALUE_OFFSET);
                if (value == null) {
                    try {
                        value = Objects.requireNonNull(computer.apply(argument));
                    } catch (Throwable ex) {
                        value = new Failed(ex);
                    }
                    UNSAFE.putReferenceVolatile(this, VALUE_OFFSET, value);
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
