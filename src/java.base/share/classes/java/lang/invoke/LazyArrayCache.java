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

/**
 * A non-owning cache backed by the elements of a Java array.
 *
 * @param <A> the array type
 * @param <T> the array component type
 */
public interface LazyArrayCache<A, T> {
    /**
     * Returns an element, computing and storing it with plain semantics if unset.
     *
     * @param array the storage array
     * @param index the element index
     * @return the cached element
     */
    T get(A array, int index);

    /**
     * Returns an element, computing it if unset and publishing it atomically.
     *
     * @param array the storage array
     * @param index the element index
     * @return the cached element
     */
    T getVolatile(A array, int index);

    /**
     * Returns an element, computing it while holding {@code lock} if unset.
     *
     * @param lock the computation lock
     * @param array the storage array
     * @param index the element index
     * @return the cached element
     */
    T getSynchronized(Object lock, A array, int index);

    /**
     * Creates a cache backed by the elements of an array.
     *
     * @param arrayClass the array class
     * @param computer the element computing function
     * @param <A> the array type
     * @param <T> the component type
     * @return the cache
     */
    static <A, T> LazyArrayCache<A, T> of(Class<A> arrayClass,
                                          Computer<? super A, ? extends T> computer) {
        Objects.requireNonNull(arrayClass);
        Objects.requireNonNull(computer);
        return LazyArrayCacheImpl.of(arrayClass, computer);
    }

    /**
     * A function which computes an array element.
     *
     * @param <A> the array type
     * @param <T> the component type
     */
    @FunctionalInterface
    interface Computer<A, T> {
        /**
         * Computes an array element.
         *
         * @param array the storage array
         * @param index the element index
         * @return the computed element
         */
        T compute(A array, int index);
    }
}
