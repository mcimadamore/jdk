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
 * supplied at the declaration site.
 *
 * @param <A> the computing-function argument type
 * @param <T> the element type
 */
public interface LazyArrayDeclSite<A, T> {
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
            @Override <A, T> LazyArrayDeclSite<A, T> make(
                    int size, Computer<? super A, ? extends T> computer) {
                return LazyArrayDeclSiteImpl.ofPlain(size, computer);
            }
        },
        /** Computations may race, but only one successful outcome is published. */
        CAS() {
            @Override <A, T> LazyArrayDeclSite<A, T> make(
                    int size, Computer<? super A, ? extends T> computer) {
                return LazyArrayDeclSiteImpl.ofCas(size, computer);
            }
        },
        /** One outcome is computed under synchronization and is then remembered. */
        ONCE() {
            @Override <A, T> LazyArrayDeclSite<A, T> make(
                    int size, Computer<? super A, ? extends T> computer) {
                return LazyArrayDeclSiteImpl.ofOnce(size, computer);
            }
        };

        abstract <A, T> LazyArrayDeclSite<A, T> make(
                int size, Computer<? super A, ? extends T> computer);
    }

    /**
     * Returns an element, computing it when needed.
     *
     * @param argument the computing-function argument
     * @param index the element index
     * @return the element
     */
    T get(A argument, int index);

    /**
     * Creates a lazy array with the supplied size, computation and policy.
     *
     * @param size the array size
     * @param computer the computing function
     * @param policy the lazy-update policy
     * @param <A> the computing-function argument type
     * @param <T> the element type
     * @return the lazy array
     */
    static <A, T> LazyArrayDeclSite<A, T> of(
            int size, Computer<? super A, ? extends T> computer, Policy policy) {
        return Objects.requireNonNull(policy).make(
                size, Objects.requireNonNull(computer));
    }
}
