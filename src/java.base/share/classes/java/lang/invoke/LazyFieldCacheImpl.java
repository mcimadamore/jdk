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

@TrustFinalFields
final class LazyFieldCacheImpl<R, T> implements LazyFieldCache<R, T> {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private final long offset;
    private final Class<?> type;
    private final Function<? super R, ? extends T> computer;

    static <R, T> LazyFieldCache<R, T> ofField(Class<?> owner,
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
            return new LazyFieldCacheImpl<>(UNSAFE.objectFieldOffset(field), field.getType(), computer);
        } catch (NoSuchFieldException ex) {
            throw new IllegalArgumentException("Cannot access " + owner.getName() + "." + name, ex);
        }
    }

    private LazyFieldCacheImpl(long offset, Class<?> type, Function<? super R, ? extends T> computer) {
        this.offset = offset;
        this.type = type;
        this.computer = computer;
    }

    @Override
    @ForceInline
    @SuppressWarnings("unchecked")
    public T get(R receiver) {
        Object value = getStableValue(receiver);
        if (isDefault(value)) {
            return getCasSlow(receiver);
        }
        return (T)value;
    }

    @Override
    @ForceInline
    @SuppressWarnings("unchecked")
    public T getVolatile(R receiver) {
        Object value = getVolatileValue(receiver);
        if (isDefault(value)) {
            return getCasSlow(receiver);
        }
        return (T)value;
    }

    @SuppressWarnings("unchecked")
    private T getCasSlow(R receiver) {
        Object candidate = requireInitialized(computer.apply(receiver));
        Object witness = compareAndExchangeValue(receiver, candidate);
        return (T)(isDefault(witness) ? candidate : witness);
    }

    @Override
    @ForceInline
    @SuppressWarnings("unchecked")
    public T getSynchronized(Object lock, R receiver) {
        Object value = getVolatileValue(receiver);
        if (isDefault(value)) {
            return getSynchronizedSlow(lock, receiver);
        }
        return (T)value;
    }

    @SuppressWarnings("unchecked")
    private T getSynchronizedSlow(Object lock, R receiver) {
        Object value;
        synchronized (Objects.requireNonNull(lock)) {
            value = getVolatileValue(receiver);
            if (isDefault(value)) {
                value = requireInitialized(computer.apply(receiver));
                putVolatileValue(receiver, value);
            }
        }
        return (T)value;
    }

    private Object getStableValue(Object receiver) {
        if (!type.isPrimitive()) return UNSAFE.getReferenceStable(receiver, offset);
        if (type == int.class) return UNSAFE.getIntStable(receiver, offset);
        if (type == long.class) return UNSAFE.getLongStable(receiver, offset);
        if (type == boolean.class) return UNSAFE.getBooleanStable(receiver, offset);
        if (type == byte.class) return UNSAFE.getByteStable(receiver, offset);
        if (type == short.class) return UNSAFE.getShortStable(receiver, offset);
        if (type == char.class) return UNSAFE.getCharStable(receiver, offset);
        if (type == float.class) return UNSAFE.getFloatStable(receiver, offset);
        return UNSAFE.getDoubleStable(receiver, offset);
    }

    private Object getVolatileValue(Object receiver) {
        if (!type.isPrimitive()) return UNSAFE.getReferenceVolatile(receiver, offset);
        if (type == int.class) return UNSAFE.getIntVolatile(receiver, offset);
        if (type == long.class) return UNSAFE.getLongVolatile(receiver, offset);
        if (type == boolean.class) return UNSAFE.getBooleanVolatile(receiver, offset);
        if (type == byte.class) return UNSAFE.getByteVolatile(receiver, offset);
        if (type == short.class) return UNSAFE.getShortVolatile(receiver, offset);
        if (type == char.class) return UNSAFE.getCharVolatile(receiver, offset);
        if (type == float.class) return UNSAFE.getFloatVolatile(receiver, offset);
        return UNSAFE.getDoubleVolatile(receiver, offset);
    }

    private void putVolatileValue(Object receiver, Object value) {
        if (!type.isPrimitive()) UNSAFE.putReferenceVolatile(receiver, offset, value);
        else if (type == int.class) UNSAFE.putIntVolatile(receiver, offset, (Integer)value);
        else if (type == long.class) UNSAFE.putLongVolatile(receiver, offset, (Long)value);
        else if (type == boolean.class) UNSAFE.putBooleanVolatile(receiver, offset, (Boolean)value);
        else if (type == byte.class) UNSAFE.putByteVolatile(receiver, offset, (Byte)value);
        else if (type == short.class) UNSAFE.putShortVolatile(receiver, offset, (Short)value);
        else if (type == char.class) UNSAFE.putCharVolatile(receiver, offset, (Character)value);
        else if (type == float.class) UNSAFE.putFloatVolatile(receiver, offset, (Float)value);
        else UNSAFE.putDoubleVolatile(receiver, offset, (Double)value);
    }

    private Object compareAndExchangeValue(Object receiver, Object value) {
        if (!type.isPrimitive()) return UNSAFE.compareAndExchangeReference(receiver, offset, null, value);
        if (type == int.class) return UNSAFE.compareAndExchangeInt(receiver, offset, 0, (Integer)value);
        if (type == long.class) return UNSAFE.compareAndExchangeLong(receiver, offset, 0L, (Long)value);
        if (type == boolean.class) return UNSAFE.compareAndExchangeBoolean(receiver, offset, false, (Boolean)value);
        if (type == byte.class) return UNSAFE.compareAndExchangeByte(receiver, offset, (byte)0, (Byte)value);
        if (type == short.class) return UNSAFE.compareAndExchangeShort(receiver, offset, (short)0, (Short)value);
        if (type == char.class) return UNSAFE.compareAndExchangeChar(receiver, offset, (char)0, (Character)value);
        if (type == float.class) return UNSAFE.compareAndExchangeFloat(receiver, offset, 0.0f, (Float)value);
        return UNSAFE.compareAndExchangeDouble(receiver, offset, 0.0d, (Double)value);
    }

    private boolean isDefault(Object value) {
        if (value == null) return true;
        if (!type.isPrimitive()) return false;
        if (type == boolean.class) return !(Boolean)value;
        if (type == char.class) return (Character)value == 0;
        if (type == float.class) return Float.floatToRawIntBits((Float)value) == 0;
        if (type == double.class) return Double.doubleToRawLongBits((Double)value) == 0;
        return ((Number)value).longValue() == 0;
    }

    private T requireInitialized(T value) {
        if (isDefault(value)) {
            throw new IllegalStateException("initializer returned the default value");
        }
        return value;
    }
}
