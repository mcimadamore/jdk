/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.lang;

import jdk.internal.stable.StableValueImpl;

import java.lang.invoke.VarHandle;
import java.util.NoSuchElementException;

/**
 * Stable value.
 *
 * @param <T> type of the holder value
 *
 * @since 24
 */
public sealed interface StableValue<T>
        permits StableValueImpl {

    // Principal methods

    /**
     * {@return {@code true} if the holder value was set to the provided {@code value},
     * otherwise returns {@code false}}
     * <p>
     * When this method returns, a holder value is always set.
     *
     * @param value to set (nullable)
     * @param indices indices
     */
    boolean trySet(T value, int... indices);

    /**
     * {@return the set holder value (nullable) if set, otherwise return the
     * {@code other} value}
     *
     * @param other to return if the stable holder value is not set
     * @param indices indices
     */
    T orElse(T other, int... indices);

    /**
     * {@return the set holder value if set, otherwise throws {@code NoSuchElementException}}
     *
     * @param indices indices
     * @throws NoSuchElementException if no value is set
     */
    T orElseThrow(int... indices);

    /**
     * {@return {@code true} if a holder value is set, {@code false} otherwise}
     * @param indices indices
     */
    boolean isSet(int... indices);

    /**
     * {@return a var handle, used to access the stable value}
     */
    VarHandle varHandle();

    /**
     * {@return the type of the stable value's backing storage}
     */
    Class<?> type();

    /**
     * Sets the holder value to the provided {@code value}, or, if already set,
     * throws {@linkplain IllegalStateException}}
     * <p>
     * When this method returns (or throws an Exception), a holder value is always set.
     *
     * @param value to set (nullable)
     * @throws IllegalStateException if a holder value is already set
     */
    default void setOrThrow(T value) {
        if (!trySet(value)) {
            throw new IllegalStateException("Cannot set the holder value to " + value +
                    " because a holder value is alredy set: " + this);
        }
    }


    // Factory

    /**
     * {@return a fresh stable value with an unset holder value}
     *
     * @param <T> type of the holder value
     * @param dims the dimensions of the stable value's backing storage
     * @param type the type of the stable value's backing storage
     */
    @SuppressWarnings("unchecked")
    static <T> StableValue<T> newInstance(Class<T> type, int... dims) {
        if (type.equals(int.class)) {
            if (dims.length == 0) {
                return (StableValue<T>) new StableValueImpl.OfInt();
            } else if (dims.length == 1) {
                return (StableValue<T>) new StableValueImpl.OfIntArray(dims[0]);
            } else if (dims.length == 2) {
                return (StableValue<T>) new StableValueImpl.OfIntArrayArray(dims[0], dims[1]);
            } else {
                throw new UnsupportedOperationException();
            }
        } else if (!type.isPrimitive()) {
            if (dims.length == 0) {
                return (StableValue<T>) new StableValueImpl.OfObject();
            } else if (dims.length == 1) {
                return (StableValue<T>) new StableValueImpl.OfObjectArray(dims[0]);
            } else if (dims.length == 2) {
                return (StableValue<T>) new StableValueImpl.OfObjectArrayArray(dims[0], dims[1]);
            } else {
                throw new UnsupportedOperationException();
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }
}