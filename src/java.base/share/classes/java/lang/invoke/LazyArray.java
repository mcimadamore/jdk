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
import java.util.function.IntFunction;

/**
 * An array of independently lazily computed values.
 *
 * @param <A> the computing-function argument type
 * @param <T> the element type
 */
public interface LazyArray<A, T> {
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
     * Returns the value at {@code index}, computing it from {@code argument} when needed.
     * Subsequent arguments are ignored after an outcome is published for that index.
     *
     * @param argument the computing-function argument
     * @param index the element index
     * @return the lazy value
     */
    T get(A argument, int index);

    /**
     * Creates a lazy array with the {@link LazyValue.Policy#ONCE} policy.
     *
     * @param size the array size
     * @param computer the element computing function
     * @param <A> the computing-function argument type
     * @param <T> the element type
     * @return the lazy array
     */
    static <A, T> LazyArray<A, T> of(int size,
                                     Computer<? super A, ? extends T> computer) {
        return LazyArray.<A, T>of(LazyValue.Policy.ONCE, size, computer);
    }

    /**
     * Creates a lazy array with the supplied policy.
     *
     * @param policy the lazy-update policy
     * @param size the array size
     * @param computer the element computing function
     * @param <A> the computing-function argument type
     * @param <T> the element type
     * @return the lazy array
     */
    static <A, T> LazyArray<A, T> of(LazyValue.Policy policy,
                                     int size,
                                     Computer<? super A, ? extends T> computer) {
        Objects.requireNonNull(policy);
        Objects.requireNonNull(computer);
        return policy.makeArray(size, computer);
    }

    /**
     * Creates a no-argument lazy array with the {@link LazyValue.Policy#ONCE} policy.
     *
     * @param size the array size
     * @param computer the element computing function
     * @param <T> the element type
     * @return the lazy array
     */
    static <T> LazyArray<Void, T> of(int size, IntFunction<? extends T> computer) {
        return of(LazyValue.Policy.ONCE, size, computer);
    }

    /**
     * Creates a no-argument lazy array with the supplied policy.
     *
     * @param size the array size
     * @param computer the element computing function
     * @param policy the lazy-update policy
     * @param <T> the element type
     * @return the lazy array
     */
    static <T> LazyArray<Void, T> of(LazyValue.Policy policy,
                                     int size,
                                     IntFunction<? extends T> computer) {
        Objects.requireNonNull(computer);
        Computer<Void, T> contextualComputer = (ignored, index) -> computer.apply(index);
        return LazyArray.<Void, T>of(policy, size, contextualComputer);
    }
}
