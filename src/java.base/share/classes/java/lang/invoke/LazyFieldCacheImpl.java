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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.function.Function;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.TrustFinalFields;

abstract class LazyFieldCacheImpl<R, T> implements LazyFieldCache<R, T> {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    final long offset;
    final Function<? super R, ? extends T> computer;

    static <R, T> LazyFieldCache<R, T> ofField(Mode mode,
                                               Class<?> owner,
                                               String name,
                                               Function<? super R, ? extends T> computer,
                                               Class<?> caller) {
        try {
            Field field = owner.getDeclaredField(name);
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers)) {
                throw new IllegalArgumentException(name + " is not an instance field");
            }
            if (Modifier.isFinal(modifiers)) {
                throw new IllegalArgumentException(name + " is a final field");
            }
            if (field.getType().isPrimitive()) {
                throw new IllegalArgumentException(name + " is not a reference field");
            }
            long offset = UNSAFE.objectFieldOffset(field);
            return switch (mode) {
                case RETRY -> new OfRetry<>(offset, computer);
                case ONCE -> new OfOnce<>(offset, computer);
            };
        } catch (NoSuchFieldException ex) {
            throw new IllegalArgumentException("Cannot access " + owner.getName() + "." + name, ex);
        }
    }

    private LazyFieldCacheImpl(long offset, Function<? super R, ? extends T> computer) {
        this.offset = offset;
        this.computer = computer;
    }

    @TrustFinalFields
    private static final class OfRetry<R, T> extends LazyFieldCacheImpl<R, T> {
        private OfRetry(long offset, Function<? super R, ? extends T> computer) {
            super(offset, computer);
        }

        @Override
        @ForceInline
        @SuppressWarnings("unchecked")
        public T get(R receiver) {
            Object value = UNSAFE.getReferenceStable(receiver, offset);
            if (value != null) {
                return (T) value;
            }
            return getSlow(receiver);
        }

        @SuppressWarnings("unchecked")
        private T getSlow(R receiver) {
            Object candidate = Objects.requireNonNull(computer.apply(receiver));
            Object witness = UNSAFE.compareAndExchangeReference(receiver, offset, null, candidate);
            return (T) (witness == null ? candidate : witness);
        }
    }

    @TrustFinalFields
    private static final class OfOnce<R, T> extends LazyFieldCacheImpl<R, T> {
        private OfOnce(long offset, Function<? super R, ? extends T> computer) {
            super(offset, computer);
        }

        @Override
        @ForceInline
        @SuppressWarnings("unchecked")
        public T get(R receiver) {
            Object value = UNSAFE.getReferenceStable(receiver, offset);
            if (value != null) {
                return (T) value;
            }
            return getSlow(receiver);
        }

        @SuppressWarnings("unchecked")
        private T getSlow(R receiver) {
            Object value;
            synchronized (Objects.requireNonNull(receiver)) {
                value = UNSAFE.getReferenceVolatile(receiver, offset);
                if (value == null) {
                    value = Objects.requireNonNull(computer.apply(receiver));
                    UNSAFE.putReferenceVolatile(receiver, offset, value);
                }
            }
            return (T) value;
        }
    }
}
