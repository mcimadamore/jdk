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

final class LazyValueImpl {
    private LazyValueImpl() { }

    static <A, T> LazyValue<A, T> ofPlain(Function<? super A, ? extends T> computer) {
        return OfPlain.of(computer);
    }

    static <A, T> LazyValue<A, T> ofCas(Function<? super A, ? extends T> computer) {
        return OfCas.of(computer);
    }

    static <A, T> LazyValue<A, T> ofOnce(Function<? super A, ? extends T> computer) {
        return OfOnce.of(computer);
    }

    @TrustFinalFields
    static final class OfPlain<A, T> implements LazyValue<A, T> {
        private final Function<? super A, ? extends T> computer;
        private Object value;

        private OfPlain(Function<? super A, ? extends T> computer) {
            this.computer = computer;
        }

        static <A, T> LazyValue<A, T> of(Function<? super A, ? extends T> computer) {
            return new OfPlain<>(computer);
        }

        @Override
        @ForceInline
        public T get(A argument) {
            Object value = this.value;
            if (value == null) {
                value = Objects.requireNonNull(computer.apply(argument));
                this.value = value;
            }
            @SuppressWarnings("unchecked")
            T result = (T) value;
            return result;
        }
    }

    @TrustFinalFields
    static final class OfCas<A, T> implements LazyValue<A, T> {
        private static final Unsafe UNSAFE = Unsafe.getUnsafe();
        private static final long VALUE_OFFSET = UNSAFE.objectFieldOffset(OfCas.class, "value");

        private final Function<? super A, ? extends T> computer;
        @Stable
        private Object value;

        private OfCas(Function<? super A, ? extends T> computer) {
            this.computer = computer;
        }

        static <A, T> LazyValue<A, T> of(Function<? super A, ? extends T> computer) {
            return new OfCas<>(computer);
        }

        @Override
        @ForceInline
        public T get(A argument) {
            Object value = UNSAFE.getReferenceStable(this, VALUE_OFFSET);
            if (value == null) {
                Object candidate = Objects.requireNonNull(computer.apply(argument));
                Object witness = UNSAFE.compareAndExchangeReference(
                        this, VALUE_OFFSET, null, candidate);
                value = witness == null ? candidate : witness;
            }
            @SuppressWarnings("unchecked")
            T result = (T) value;
            return result;
        }
    }

    @TrustFinalFields
    static final class OfOnce<A, T> implements LazyValue<A, T> {
        private static final Unsafe UNSAFE = Unsafe.getUnsafe();
        private static final long VALUE_OFFSET = UNSAFE.objectFieldOffset(OfOnce.class, "value");

        private final Function<? super A, ? extends T> computer;
        @Stable
        private Object value;

        private OfOnce(Function<? super A, ? extends T> computer) {
            this.computer = computer;
        }

        static <A, T> LazyValue<A, T> of(Function<? super A, ? extends T> computer) {
            return new OfOnce<>(computer);
        }

        @Override
        @ForceInline
        public T get(A argument) {
            Object value = UNSAFE.getReferenceStable(this, VALUE_OFFSET);
            if (value == null) {
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
