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

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;
import jdk.internal.vm.annotation.TrustFinalFields;

import static java.lang.invoke.MethodHandleStatics.uncaughtException;
import static java.lang.invoke.MethodType.methodType;

final class LazyArrayImpl {
    private static final MethodHandle COMPUTE;
    private static final MethodHandle COMPUTE_ONCE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.Lookup.IMPL_LOOKUP;
            COMPUTE = lookup.findStatic(LazyArrayImpl.class, "compute",
                    methodType(Object.class, Object.class, int.class, LazyArray.Computer.class));
            COMPUTE_ONCE = lookup.findStatic(LazyArrayImpl.class, "computeOnce",
                    methodType(Object.class, Object.class, int.class, LazyArray.Computer.class));
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private LazyArrayImpl() { }

    static <T> LazyArray<T> ofPlain(int size) {
        return OfPlain.of(size);
    }

    static <T> LazyArray<T> ofCas(int size) {
        return OfCas.of(size);
    }

    static <T> LazyArray<T> ofOnce(int size) {
        return OfOnce.of(size);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Object compute(Object argument, int index, LazyArray.Computer computer) {
        return Objects.requireNonNull(computer.compute(argument, index));
    }

    private static Object computeOnce(Object argument, int index, LazyArray.Computer<?, ?> computer) {
        try {
            @SuppressWarnings({ "rawtypes", "unchecked" })
            Object value = ((LazyArray.Computer) computer).compute(argument, index);
            return Objects.requireNonNull(value);
        } catch (Throwable ex) {
            return new Failed(ex);
        }
    }

    private static MethodHandle updater(Class<?> holder, LazyValue.Policy policy) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.Lookup.IMPL_LOOKUP;
            VarHandle target = MethodHandles.arrayElementVarHandle(Object[].class);
            MethodHandle values = lookup.findGetter(holder, "values", Object[].class);
            target = MethodHandles.filterCoordinates(target, 0, values);
            target = MethodHandles.dropCoordinates(target, 1, Object.class);
            target = MethodHandles.dropCoordinates(target, 3, LazyArray.Computer.class);
            MethodHandle initializer = MethodHandles.dropArguments(
                    policy == LazyValue.Policy.ONCE ? COMPUTE_ONCE : COMPUTE, 0, holder);
            return switch (policy) {
                case PLAIN -> MethodHandles.lazyUpdater(target, initializer, false);
                case CAS -> MethodHandles.lazyUpdaterVolatile(target, initializer, true);
                case ONCE -> MethodHandles.lazyUpdaterSynchronized(target, initializer, true);
            };
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @TrustFinalFields
    static final class OfPlain<T> implements LazyArray<T> {
        private static final MethodHandle UPDATER = updater(OfPlain.class, LazyValue.Policy.PLAIN);

        private final Object[] values;

        private OfPlain(int size) {
            if (size < 0) throw new IllegalArgumentException("Negative size: " + size);
            values = new Object[size];
        }

        static <T> LazyArray<T> of(int size) {
            return new OfPlain<>(size);
        }

        @Override
        @ForceInline
        public <A> T get(A argument, int index, LazyArray.Computer<? super A, ? extends T> computer) {
            try {
                @SuppressWarnings("unchecked")
                T value = (T) (Object) UPDATER.invokeExact(
                        this, (Object) argument, index, (LazyArray.Computer) computer);
                return value;
            } catch (Throwable ex) {
                throw uncaughtException(ex);
            }
        }
    }

    @TrustFinalFields
    static final class OfCas<T> implements LazyArray<T> {
        private static final MethodHandle UPDATER = updater(OfCas.class, LazyValue.Policy.CAS);

        @Stable
        private final Object[] values;

        private OfCas(int size) {
            if (size < 0) throw new IllegalArgumentException("Negative size: " + size);
            values = new Object[size];
        }

        static <T> LazyArray<T> of(int size) {
            return new OfCas<>(size);
        }

        @Override
        @ForceInline
        public <A> T get(A argument, int index, LazyArray.Computer<? super A, ? extends T> computer) {
            try {
                @SuppressWarnings("unchecked")
                T value = (T) (Object) UPDATER.invokeExact(
                        this, (Object) argument, index, (LazyArray.Computer) computer);
                return value;
            } catch (Throwable ex) {
                throw uncaughtException(ex);
            }
        }
    }

    @TrustFinalFields
    static final class OfOnce<T> implements LazyArray<T> {
        private static final MethodHandle UPDATER = updater(OfOnce.class, LazyValue.Policy.ONCE);

        @Stable
        private final Object[] values;

        private OfOnce(int size) {
            if (size < 0) throw new IllegalArgumentException("Negative size: " + size);
            values = new Object[size];
        }

        static <T> LazyArray<T> of(int size) {
            return new OfOnce<>(size);
        }

        @Override
        @ForceInline
        public <A> T get(A argument, int index, LazyArray.Computer<? super A, ? extends T> computer) {
            try {
                Object value = (Object) UPDATER.invokeExact(
                        (Object) this, this, (Object) argument, index, (LazyArray.Computer) computer);
                if (value instanceof Failed failed) {
                    throw uncaughtException(failed.exception);
                }
                @SuppressWarnings("unchecked")
                T result = (T) value;
                return result;
            } catch (Throwable ex) {
                throw uncaughtException(ex);
            }
        }
    }

    private record Failed(Throwable exception) { }
}
