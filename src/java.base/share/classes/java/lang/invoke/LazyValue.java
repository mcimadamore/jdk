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
import java.util.function.Function;

/**
 * A value computed lazily from an argument supplied at access time.
 *
 * @param <A> the computing-function argument type
 * @param <T> the value type
 */
public interface LazyValue<A, T> {
    /**
     * Lazy-update policy.
     */
    enum Policy {
        /** Computations and publications use plain accesses and may race. */
        PLAIN() {
            @Override
            <A, T> LazyValue<A, T> make(Function<? super A, ? extends T> computer) {
                return LazyValueImpl.ofPlain(computer);
            }

            @Override
            <A, T> LazyArray<A, T> makeArray(int size,
                                              LazyArray.Computer<? super A, ? extends T> computer) {
                return LazyArrayImpl.ofPlain(size, computer);
            }
        },
        /** Computations may race, but only one successful outcome is published. */
        CAS() {
            @Override
            <A, T> LazyValue<A, T> make(Function<? super A, ? extends T> computer) {
                return LazyValueImpl.ofCas(computer);
            }

            @Override
            <A, T> LazyArray<A, T> makeArray(int size,
                                              LazyArray.Computer<? super A, ? extends T> computer) {
                return LazyArrayImpl.ofCas(size, computer);
            }
        },
        /** One outcome is computed under synchronization and is then remembered. */
        ONCE() {
            @Override
            <A, T> LazyValue<A, T> make(Function<? super A, ? extends T> computer) {
                return LazyValueImpl.ofOnce(computer);
            }

            @Override
            <A, T> LazyArray<A, T> makeArray(int size,
                                              LazyArray.Computer<? super A, ? extends T> computer) {
                return LazyArrayImpl.ofOnce(size, computer);
            }
        };

        abstract <A, T> LazyValue<A, T> make(Function<? super A, ? extends T> computer);

        abstract <A, T> LazyArray<A, T> makeArray(int size,
                                                   LazyArray.Computer<? super A, ? extends T> computer);
    }

    /**
     * Returns the value, computing it from {@code argument} when needed.
     * Subsequent arguments are ignored after an outcome is published.
     *
     * @param argument the computing-function argument
     * @return the lazy value
     */
    T get(A argument);

    /**
     * Creates a lazy value with the {@link Policy#ONCE} policy.
     *
     * @param computer the computing function
     * @param <A> the computing-function argument type
     * @param <T> the value type
     * @return the lazy value
     */
    static <A, T> LazyValue<A, T> of(Function<? super A, ? extends T> computer) {
        Objects.requireNonNull(computer);
        return Policy.ONCE.make(computer);
    }

    /**
     * Creates a lazy value with the supplied policy.
     *
     * @param policy the lazy-update policy
     * @param computer the computing function
     * @param <A> the computing-function argument type
     * @param <T> the value type
     * @return the lazy value
     */
    static <A, T> LazyValue<A, T> of(Policy policy,
                                     Function<? super A, ? extends T> computer) {
        Objects.requireNonNull(policy);
        Objects.requireNonNull(computer);
        return policy.make(computer);
    }
}
