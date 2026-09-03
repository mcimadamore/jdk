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
 * An array of independently computed lazy values, with a computing function
 * supplied at the use site.
 *
 * @param <T> the element type
 */
public interface LazyArrayUseSite<T> {
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

    /** Lazy-update policy. */
    enum Policy {
        /** Computations and publications use plain accesses and may race. */
        PLAIN() {
            @Override <T> LazyArrayUseSite<T> make(int size) {
                return LazyArrayUseSiteImpl.ofPlain(size);
            }

        },
        /** Computations may race, but only one successful outcome is published. */
        CAS() {
            @Override <T> LazyArrayUseSite<T> make(int size) {
                return LazyArrayUseSiteImpl.ofCas(size);
            }

        },
        /** One outcome is computed under synchronization and is then remembered. */
        ONCE() {
            @Override <T> LazyArrayUseSite<T> make(int size) {
                return LazyArrayUseSiteImpl.ofOnce(size);
            }

        };

        abstract <T> LazyArrayUseSite<T> make(int size);

    }

    /**
     * Returns an element, computing it when needed.
     *
     * @param argument the computing-function argument
     * @param index the element index
     * @param computer the computing function
     * @param <A> the computing-function argument type
     * @return the element
     */
    <A> T get(A argument, int index, Computer<? super A, ? extends T> computer);

    /**
     * Creates a lazy array with the supplied size and policy.
     *
     * @param size the array size
     * @param policy the lazy-update policy
     * @param <T> the element type
     * @return the lazy array
     */
    static <T> LazyArrayUseSite<T> of(int size, Policy policy) {
        return Objects.requireNonNull(policy).make(size);
    }

}
