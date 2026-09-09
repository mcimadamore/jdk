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

import java.util.function.IntFunction;

/**
 * An array of independently lazily computed values.
 *
 * @param <T> the element type
 */
public interface LazyArrayCache<T> {
    /**
     * A function which computes an array element.
     *
     * @param <A> the computing-function argument type
     * @param <T> the element type
     */
    @FunctionalInterface
    interface Computer<A, T> {
        /**
         * Computes an element.
         *
         * @param argument the computing-function argument
         * @param index the element index
         * @return the computed element
         */
        T compute(A argument, int index);
    }

    /**
     * Returns the value at {@code index}, computing it with {@code computer} from {@code argument} when needed.
     * Subsequent arguments are ignored after an outcome is published for that index.
     *
     * @param computer the element computing function
     * @param argument the computing-function argument
     * @param index the element index
     * @param <A> the computing-function argument type
     * @return the lazy value
     */
    <A> T getOrCompute(A argument, int index, Computer<? super A, ? extends T> computer);

    /**
     * Returns the value at {@code index}, computing it with {@code computer} when needed.
     *
     * @param index the element index
     * @param computer the element computing function
     * @return the lazy value
     */
    default T getOrCompute(int index, IntFunction<? extends T> computer) {
        return getOrCompute(null, index, (ignored, i) -> computer.apply(i));
    }

    /**
     * Creates a lazy array which retries failed computations and permits racing computations.
     *
     * @param size the array size
     * @param <T> the element type
     * @return the lazy array
     */
    static <T> LazyArrayCache<T> ofRetry(int size) {
        return LazyArrayCacheImpl.ofRetry(size);
    }

    /**
     * Creates a lazy array which computes and remembers one successful or failed outcome per index.
     * @param size the array size
     * @param <T> the element type
     * @return the lazy array
     */
    static <T> LazyArrayCache<T> ofOnce(int size) {
        return LazyArrayCacheImpl.ofOnce(size);
    }

}
