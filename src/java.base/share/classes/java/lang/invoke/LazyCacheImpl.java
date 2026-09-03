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
final class LazyCacheImpl<R, T> implements LazyCache<R, T> {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private final long offset;
    private final Class<?> type;
    private final Function<? super R, ? extends T> computer;

    static <R, T> LazyCache<R, T> ofField(Class<R> owner,
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
            return new LazyCacheImpl<>(UNSAFE.objectFieldOffset(field), field.getType(), computer);
        } catch (NoSuchFieldException ex) {
            throw new IllegalArgumentException("Cannot access " + owner.getName() + "." + name, ex);
        }
    }

    private LazyCacheImpl(long offset, Class<?> type, Function<? super R, ? extends T> computer) {
        this.offset = offset;
        this.type = type;
        this.computer = computer;
    }

    @Override
    @ForceInline
    @SuppressWarnings("unchecked")
    public T get(R receiver) {
        Object value = getPlainValue(receiver);
        if (isDefault(value)) {
            return getSlow(receiver);
        }
        return (T)value;
    }

    private T getSlow(R receiver) {
        T value = requireInitialized(computer.apply(receiver));
        putPlainValue(receiver, value);
        return value;
    }

    @Override
    @ForceInline
    @SuppressWarnings("unchecked")
    public T getVolatile(R receiver) {
        Object value = getVolatileValue(receiver);
        if (isDefault(value)) {
            return getVolatileSlow(receiver);
        }
        return (T)value;
    }

    @SuppressWarnings("unchecked")
    private T getVolatileSlow(R receiver) {
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

    private Object getPlainValue(Object receiver) {
        if (!type.isPrimitive()) return UNSAFE.getReference(receiver, offset);
        if (type == int.class) return UNSAFE.getInt(receiver, offset);
        if (type == long.class) return UNSAFE.getLong(receiver, offset);
        if (type == boolean.class) return UNSAFE.getBoolean(receiver, offset);
        if (type == byte.class) return UNSAFE.getByte(receiver, offset);
        if (type == short.class) return UNSAFE.getShort(receiver, offset);
        if (type == char.class) return UNSAFE.getChar(receiver, offset);
        if (type == float.class) return UNSAFE.getFloat(receiver, offset);
        return UNSAFE.getDouble(receiver, offset);
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

    private void putPlainValue(Object receiver, Object value) {
        if (!type.isPrimitive()) UNSAFE.putReference(receiver, offset, value);
        else if (type == int.class) UNSAFE.putInt(receiver, offset, (Integer)value);
        else if (type == long.class) UNSAFE.putLong(receiver, offset, (Long)value);
        else if (type == boolean.class) UNSAFE.putBoolean(receiver, offset, (Boolean)value);
        else if (type == byte.class) UNSAFE.putByte(receiver, offset, (Byte)value);
        else if (type == short.class) UNSAFE.putShort(receiver, offset, (Short)value);
        else if (type == char.class) UNSAFE.putChar(receiver, offset, (Character)value);
        else if (type == float.class) UNSAFE.putFloat(receiver, offset, (Float)value);
        else UNSAFE.putDouble(receiver, offset, (Double)value);
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
