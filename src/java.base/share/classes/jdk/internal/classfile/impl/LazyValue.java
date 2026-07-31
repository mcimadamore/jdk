/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package jdk.internal.classfile.impl;

import java.util.Objects;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

/** A write-once, lazily-computed value used by scalar classfile caches. */
final class LazyValue<T> {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final long VALUE_OFFSET =
            UNSAFE.objectFieldOffset(LazyValue.class, "value");

    @Stable
    private T value;

    @SuppressWarnings("unchecked")
    @ForceInline
    T get() {
        T result = getOrNull();
        if (result == null) {
            throw new NoSuchElementException();
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    private T getOrNull() {
        return (T) UNSAFE.getReferenceAcquire(this, VALUE_OFFSET);
    }

    @SuppressWarnings("unchecked")
    T orElseSet(Supplier<? extends T> supplier) {
        T result = getOrNull();
        if (result != null) {
            return result;
        }
        T candidate = Objects.requireNonNull(supplier.get());
        T witness = (T) UNSAFE.compareAndExchangeReference(this, VALUE_OFFSET, null, candidate);
        return witness == null ? candidate : witness;
    }
}
