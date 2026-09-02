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

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;
import jdk.internal.vm.annotation.TrustFinalFields;

import static java.lang.invoke.MethodHandleStatics.uncaughtException;
import static java.lang.invoke.MethodType.methodType;

final class LazyValueImpl {
    private static final MethodHandle COMPUTE;
    private static final MethodHandle COMPUTE_ONCE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.Lookup.IMPL_LOOKUP;
            COMPUTE = lookup.findStatic(LazyValueImpl.class, "compute",
                    methodType(Object.class, Object.class, Function.class));
            COMPUTE_ONCE = lookup.findStatic(LazyValueImpl.class, "computeOnce",
                    methodType(Object.class, Object.class, Function.class));
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private LazyValueImpl() { }

    static <T> LazyValue<T> ofPlain() {
        return OfPlain.of();
    }

    static <T> LazyValue<T> ofCas() {
        return OfCas.of();
    }

    static <T> LazyValue<T> ofOnce() {
        return OfOnce.of();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @ForceInline
    private static Object compute(Object argument, Function computer) {
        return Objects.requireNonNull(computer.apply(argument));
    }

    @ForceInline
    private static Object computeOnce(Object argument, Function<?, ?> computer) {
        try {
            @SuppressWarnings({ "rawtypes", "unchecked" })
            Object value = ((Function) computer).apply(argument);
            return Objects.requireNonNull(value);
        } catch (Throwable ex) {
            return new Failed(ex);
        }
    }

    private static MethodHandle updater(Class<?> holder, LazyValue.Policy policy) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.Lookup.IMPL_LOOKUP;
            VarHandle target = lookup.findVarHandle(holder, "value", Object.class);
            target = MethodHandles.dropCoordinates(target, 1, Object.class, Function.class);
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
    static final class OfPlain<T> implements LazyValue<T> {
        private static final MethodHandle UPDATER = updater(OfPlain.class, LazyValue.Policy.PLAIN);

        private Object value;

        static <T> LazyValue<T> of() {
            return new OfPlain<>();
        }

        @Override
        @ForceInline
        @SuppressWarnings("unchecked")
        public <A> T get(A argument, Function<? super A, ? extends T> computer) {
            try {
                Object value = (Object) UPDATER.invokeExact(
                        this, argument, computer);
                return (T)value;
            } catch (Throwable ex) {
                throw uncaughtException(ex);
            }
        }
    }

    @TrustFinalFields
    static final class OfCas<T> implements LazyValue<T> {
        private static final MethodHandle UPDATER = updater(OfCas.class, LazyValue.Policy.CAS);

        @Stable
        private Object value;

        static <T> LazyValue<T> of() {
            return new OfCas<>();
        }

        @Override
        @ForceInline
        public <A> T get(A argument, Function<? super A, ? extends T> computer) {
            try {
                @SuppressWarnings("unchecked")
                T value = (T) (Object) UPDATER.invokeExact(
                        this, (Object) argument, (Function) computer);
                return value;
            } catch (Throwable ex) {
                throw uncaughtException(ex);
            }
        }
    }

    @TrustFinalFields
    static final class OfOnce<T> implements LazyValue<T> {
        private static final MethodHandle UPDATER = updater(OfOnce.class, LazyValue.Policy.ONCE);

        @Stable
        private Object value;

        static <T> LazyValue<T> of() {
            return new OfOnce<>();
        }

        @Override
        @ForceInline
        public <A> T get(A argument, Function<? super A, ? extends T> computer) {
            try {
                Object value = (Object) UPDATER.invokeExact(
                        (Object) this, this, (Object) argument, (Function) computer);
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
